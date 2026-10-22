package com.codehub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.codehub.common.BusinessException;
import com.codehub.dto.CommitVO;
import com.codehub.dto.FileVO;
import com.codehub.entity.Commit;
import com.codehub.entity.FileEntity;
import com.codehub.entity.Repository;
import com.codehub.entity.RepositoryMember;
import com.codehub.entity.User;
import com.codehub.mapper.CommitMapper;
import com.codehub.mapper.FileMapper;
import com.codehub.mapper.RepositoryMapper;
import com.codehub.mapper.RepositoryMemberMapper;
import com.codehub.mapper.UserMapper;
import com.codehub.service.FileService;
import com.codehub.service.MinioService;
import com.codehub.service.upload.ChunkUploadService;
import com.codehub.service.upload.FileIndexDispatcher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final FileMapper fileMapper;
    private final RepositoryMapper repoMapper;
    private final RepositoryMemberMapper memberMapper;
    private final CommitMapper commitMapper;
    private final MinioService minioService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;
    private final RedissonClient redissonClient;
    private final ChunkUploadService chunkUploadService;
    private final FileIndexDispatcher indexDispatcher;

    private static final String FILE_TREE_CACHE_PREFIX = "repo:file_tree:";

    @Override
    @Transactional
    public FileVO uploadFile(Long repoId, Long userId, String path, MultipartFile file) {
        // 1. 权限检查：WRITE或OWNER才能上传
        checkWritePermission(repoId, userId);

        // 2. 路径清洗（防路径遍历攻击）
        String cleanPath = sanitizePath(path);

        // 3. 生成MinIO objectKey: repos/{repoId}/{uuid}_{filename}
        String objectKey = "repos/" + repoId + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();

        // 4. 上传到MinIO
        try {
            minioService.upload(objectKey, file.getInputStream(), file.getContentType(), file.getSize());
        } catch (IOException e) {
            throw new RuntimeException("文件读取失败", e);
        }

        // 5. 保存文件元数据到MySQL
        FileEntity fileEntity = new FileEntity();
        fileEntity.setRepoId(repoId);
        fileEntity.setPath(cleanPath);
        fileEntity.setObjectKey(objectKey);
        fileEntity.setMimeType(file.getContentType());
        fileEntity.setSizeBytes(file.getSize());
        fileMapper.insert(fileEntity);

        // 6. 创建Commit记录
        Commit commit = new Commit();
        commit.setRepoId(repoId);
        commit.setMessage("Add file: " + cleanPath);
        commit.setAuthorId(userId);
        commitMapper.insert(commit);

        // 7. 更新文件的commitId
        fileEntity.setCommitId(commit.getId());
        fileMapper.updateById(fileEntity);

        // 8. 更新仓库的updatedAt
        repoMapper.updateById(repoMapper.selectById(repoId));

        // Only publish after the metadata transaction commits, matching the chunked path.
        if (indexDispatcher.accepts(fileEntity)) {
            try (var input = file.getInputStream()) {
                indexDispatcher.schedule(fileEntity, input);
            } catch (IOException e) {
                throw new IllegalStateException("读取索引内容失败", e);
            }
        }

        // 10. 清除文件目录缓存
        redisTemplate.delete(FILE_TREE_CACHE_PREFIX + repoId);

        return toFileVO(fileEntity);
    }

    @Override
    public String getFileDownloadUrl(Long repoId, Long userId, Long fileId) {
        // 权限检查：READ及以上可下载
        checkReadPermission(repoId, userId);

        FileEntity file = getFileById(fileId);
        if (!file.getRepoId().equals(repoId)) {
            throw new BusinessException("文件不属于该仓库");
        }

        return minioService.getPresignedUrl(file.getObjectKey());
    }

    @Override
    public byte[] getFileContent(Long repoId, Long userId, Long fileId) {
        checkReadPermission(repoId, userId);

        FileEntity file = getFileById(fileId);
        if (!file.getRepoId().equals(repoId)) {
            throw new BusinessException("文件不属于该仓库");
        }

        try (var is = minioService.download(file.getObjectKey())) {
            return is.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("文件读取失败", e);
        }
    }

    @Override
    @Transactional
    public void deleteFile(Long repoId, Long userId, Long fileId) {
        checkWritePermission(repoId, userId);

        FileEntity file = getFileById(fileId);
        if (!file.getRepoId().equals(repoId)) {
            throw new BusinessException("文件不属于该仓库");
        }

        // 删除MinIO对象
        minioService.delete(file.getObjectKey());

        // 删除MySQL记录
        fileMapper.deleteById(fileId);

        // 创建Commit记录
        Commit commit = new Commit();
        commit.setRepoId(repoId);
        commit.setMessage("Delete file: " + file.getPath());
        commit.setAuthorId(userId);
        commitMapper.insert(commit);

        // 清除文件目录缓存
        redisTemplate.delete(FILE_TREE_CACHE_PREFIX + repoId);
    }

    /**
     * 文件列表 — 带完整缓存防护的读链路
     *
     * 缓存穿透：不存在的仓库缓存空值标记（NULL_MARKER），恶意ID不再打到数据库
     * 缓存击穿：热点key过期瞬间，Redisson互斥锁保证只有一个线程回源重建
     * 缓存雪崩：TTL = 5分钟 + 随机0~60秒，避免大量key同一时刻集中过期
     */
    private static final String NULL_MARKER = "__NULL__";
    private static final long BASE_TTL_MINUTES = 5;
    private static final long TTL_JITTER_SECONDS = 60;

    @Override
    public List<FileVO> listFiles(Long repoId, Long userId) {
        checkReadPermission(repoId, userId);

        String cacheKey = FILE_TREE_CACHE_PREFIX + repoId;

        // 1. 查缓存（命中直接返回）
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if (NULL_MARKER.equals(cached)) {
                // 空值标记：该仓库确实没有文件/不存在，防穿透
                return List.of();
            }
            try {
                return objectMapper.readValue(cached, new TypeReference<List<FileVO>>() {});
            } catch (Exception e) {
                log.warn("缓存反序列化失败，回源查询", e);
            }
        }

        // 2. 缓存未命中 → Redisson互斥锁，防击穿（只放一个线程回源）
        RLock lock = redissonClient.getLock("lock:file_tree:" + repoId);
        try {
            boolean acquired = lock.tryLock(3, 10, TimeUnit.SECONDS); // 最多等3秒，锁10秒自动释放
            if (!acquired) {
                // 没抢到锁：别的线程正在回源，稍等后重读缓存
                Thread.sleep(200);
                String secondTry = redisTemplate.opsForValue().get(cacheKey);
                if (secondTry != null && !NULL_MARKER.equals(secondTry)) {
                    return objectMapper.readValue(secondTry, new TypeReference<List<FileVO>>() {});
                }
                // 兜底：直接查库返回（不写缓存），避免请求堆积超时
                return queryFilesFromDb(repoId);
            }

            // 3. 抢到锁：double check（可能上一个持锁线程已重建好缓存）
            cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return NULL_MARKER.equals(cached)
                        ? List.of()
                        : objectMapper.readValue(cached, new TypeReference<List<FileVO>>() {});
            }

            // 4. 回源查库
            List<FileVO> result = queryFilesFromDb(repoId);

            // 5. 写缓存：空结果也缓存（防穿透）；TTL加随机抖动（防雪崩）
            long ttlSeconds = TimeUnit.MINUTES.toSeconds(BASE_TTL_MINUTES)
                    + java.util.concurrent.ThreadLocalRandom.current().nextLong(TTL_JITTER_SECONDS);
            String value = result.isEmpty() ? NULL_MARKER : objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(cacheKey, value, ttlSeconds, TimeUnit.SECONDS);

            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("查询被中断");
        } catch (Exception e) {
            // Redis故障时降级直查数据库，保证可用性
            log.warn("缓存链路异常，降级直查DB: {}", e.getMessage());
            return queryFilesFromDb(repoId);
        } finally {
            // 只释放自己持有的锁（防止误删别人的锁）
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private List<FileVO> queryFilesFromDb(Long repoId) {
        List<FileEntity> files = fileMapper.selectList(
                new LambdaQueryWrapper<FileEntity>()
                        .eq(FileEntity::getRepoId, repoId)
                        .orderByAsc(FileEntity::getPath));
        return files.stream().map(this::toFileVO).collect(Collectors.toList());
    }

    @Override
    public IPage<CommitVO> listCommits(Long repoId, Long userId, int page, int size) {
        checkReadPermission(repoId, userId);

        IPage<Commit> result = commitMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size),
                new LambdaQueryWrapper<Commit>()
                        .eq(Commit::getRepoId, repoId)
                        .orderByDesc(Commit::getCreatedAt));

        return result.convert(commit -> {
            CommitVO vo = new CommitVO();
            vo.setId(commit.getId());
            vo.setMessage(commit.getMessage());
            vo.setAuthorId(commit.getAuthorId());
            vo.setCreatedAt(commit.getCreatedAt());
            // 获取作者名
            User author = userMapper.selectById(commit.getAuthorId());
            vo.setAuthorName(author != null ? author.getUsername() : "unknown");
            return vo;
        });
    }

    @Override
    public void uploadChunk(Long repoId, Long userId, String uploadId, int chunkIndex, int totalChunks, MultipartFile file) {
        checkWritePermission(repoId, userId);
        chunkUploadService.upload(repoId, userId, uploadId, chunkIndex, totalChunks, file);
    }

    @Override
    public FileVO mergeChunks(Long repoId, Long userId, String uploadId, String fileName, String path) {
        checkWritePermission(repoId, userId);
        String cleanPath = sanitizePath(path);
        if (cleanPath.isBlank() || cleanPath.length() > 500 || cleanPath.indexOf('\0') >= 0) {
            throw new BusinessException("非法文件路径");
        }
        return chunkUploadService.merge(repoId, userId, uploadId, fileName, cleanPath);
    }

    // ========== 私有方法 ==========

    private void checkWritePermission(Long repoId, Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        Repository repo = getRepo(repoId);
        if (repo.getOwnerId().equals(userId)) return; // OWNER

        RepositoryMember member = memberMapper.selectOne(
                new LambdaQueryWrapper<RepositoryMember>()
                        .eq(RepositoryMember::getRepoId, repoId)
                        .eq(RepositoryMember::getUserId, userId));
        if (member == null || "READ".equals(member.getRole())) {
            throw new BusinessException(403, "无权操作（需要WRITE或OWNER权限）");
        }
    }

    private void checkReadPermission(Long repoId, Long userId) {
        Repository repo = getRepo(repoId);

        // PUBLIC仓库所有人可读
        if ("PUBLIC".equals(repo.getVisibility())) return;

        // PRIVATE仓库需要是成员
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        RepositoryMember member = memberMapper.selectOne(
                new LambdaQueryWrapper<RepositoryMember>()
                        .eq(RepositoryMember::getRepoId, repoId)
                        .eq(RepositoryMember::getUserId, userId));
        if (member == null) {
            throw new BusinessException(403, "无权访问该仓库");
        }
    }

    private Repository getRepo(Long repoId) {
        Repository repo = repoMapper.selectById(repoId);
        if (repo == null) throw new BusinessException("仓库不存在");
        return repo;
    }

    private FileEntity getFileById(Long fileId) {
        FileEntity file = fileMapper.selectById(fileId);
        if (file == null) throw new BusinessException("文件不存在");
        return file;
    }

    /**
     * 路径清洗：去除前后斜杠、防止路径遍历
     */
    private String sanitizePath(String path) {
        if (path == null || path.isBlank()) {
            throw new BusinessException("文件路径不能为空");
        }
        // 去除前后斜杠和反斜杠
        String clean = path.replace("\\", "/").replaceAll("^/+", "").replaceAll("/+$", "");
        // 防止路径遍历
        if (clean.contains("..")) {
            throw new BusinessException("非法文件路径");
        }
        return clean;
    }

    private FileVO toFileVO(FileEntity entity) {
        FileVO vo = new FileVO();
        vo.setId(entity.getId());
        vo.setPath(entity.getPath());
        vo.setMimeType(entity.getMimeType());
        vo.setSizeBytes(entity.getSizeBytes());
        vo.setCommitId(entity.getCommitId());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
