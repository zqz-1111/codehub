package com.codehub.service.upload;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codehub.common.BusinessException;
import com.codehub.dto.FileVO;
import com.codehub.entity.Commit;
import com.codehub.entity.FileEntity;
import com.codehub.entity.UploadPart;
import com.codehub.entity.UploadSession;
import com.codehub.mapper.CommitMapper;
import com.codehub.mapper.FileMapper;
import com.codehub.mapper.UploadPartMapper;
import com.codehub.mapper.UploadSessionMapper;
import com.codehub.service.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Called only after FileServiceImpl checks repository write permission. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkUploadService {
    private final UploadSessionMapper sessionMapper;
    private final UploadPartMapper partMapper;
    private final FileMapper fileMapper;
    private final CommitMapper commitMapper;
    private final MinioService minio;
    private final RedissonClient redisson;
    private final StringRedisTemplate redis;
    private final FileIndexDispatcher indexDispatcher;
    private final PlatformTransactionManager transactionManager;

    public void upload(Long repoId, Long userId, String uploadId, int index, int total, MultipartFile file) {
        validateId(uploadId);
        if (total < 1 || total > 10000 || index < 0 || index >= total
                || file == null || file.isEmpty() || file.getSize() > 1024 * 1024) {
            throw new BusinessException("非法分片参数（每片1字节至1MiB，总片数1至10000）");
        }
        withLock(uploadId, () -> inTransaction(() -> {
            UploadSession session = sessionMapper.selectForUpdate(uploadId);
            if (session == null) {
                if (partMapper.selectCount(new LambdaQueryWrapper<UploadPart>()
                        .eq(UploadPart::getUploadId, uploadId)) > 0) {
                    throw new BusinessException(409, "旧分片缺少会话信息，请使用新uploadId重新上传");
                }
                session = new UploadSession();
                session.setUploadId(uploadId);
                session.setRepoId(repoId);
                session.setUserId(userId);
                session.setTotalChunks(total);
                session.setStatus("UPLOADING");
                sessionMapper.insert(session);
            }
            checkOwner(session, repoId, userId);
            if (!"UPLOADING".equals(session.getStatus()) || session.getTotalChunks() != total) {
                throw new BusinessException(409, "上传已完成或总片数与会话不一致");
            }
            UploadPart old = partMapper.selectOne(new LambdaQueryWrapper<UploadPart>()
                    .eq(UploadPart::getUploadId, uploadId).eq(UploadPart::getChunkIndex, index));
            String key = "chunks/" + repoId + "/" + userId + "/" + uploadId + "/" + index + "-" + UUID.randomUUID();
            deleteOnRollback(key);
            try (var input = file.getInputStream()) {
                minio.upload(key, input, "application/octet-stream", file.getSize());
            } catch (IOException e) { throw new IllegalStateException("读取分片失败", e); }
            UploadPart part = new UploadPart();
            part.setUploadId(uploadId);
            part.setRepoId(repoId);
            part.setChunkIndex(index);
            part.setObjectKey(key);
            part.setSizeBytes(file.getSize());
            part.setStatus("UPLOADING");
            if (old == null) partMapper.insert(part);
            else {
                part.setId(old.getId());
                partMapper.updateById(part);
                afterCommit(() -> safeDelete(old.getObjectKey()));
            }
            return null;
        }));
    }

    public FileVO merge(Long repoId, Long userId, String uploadId, String fileName, String cleanPath) {
        validateId(uploadId);
        if (fileName == null || fileName.isBlank() || fileName.length() > 255
                || fileName.contains("/") || fileName.contains("\\") || fileName.contains("\0")) {
            throw new BusinessException("非法文件名");
        }
        return withLock(uploadId, () -> inTransaction(() -> {
            UploadSession session = sessionMapper.selectForUpdate(uploadId);
            if (session == null) throw new BusinessException("上传会话不存在，请重新上传分片");
            checkOwner(session, repoId, userId);
            if ("MERGED".equals(session.getStatus())) {
                if (!Objects.equals(cleanPath, session.getMergedPath())
                        || !Objects.equals(fileName, session.getMergedFileName())) {
                    throw new BusinessException(409, "同一上传会话不能合并为其他文件");
                }
                FileEntity existing = fileMapper.selectById(session.getMergedFileId());
                if (existing == null) throw new BusinessException(410, "已合并文件已删除，请新建上传会话");
                return toVO(existing);
            }
            if (!"UPLOADING".equals(session.getStatus())) throw new BusinessException(409, "上传会话状态异常");
            List<UploadPart> parts = partMapper.selectList(new LambdaQueryWrapper<UploadPart>()
                    .eq(UploadPart::getUploadId, uploadId).eq(UploadPart::getRepoId, repoId));
            parts = parts.stream().sorted(Comparator.comparing(UploadPart::getChunkIndex,
                    Comparator.nullsFirst(Comparator.naturalOrder()))).toList();
            if (parts.size() != session.getTotalChunks()) throw new BusinessException(409, "分片尚未上传完整");
            for (int i = 0; i < parts.size(); i++) {
                UploadPart part = parts.get(i);
                if (!Objects.equals(part.getChunkIndex(), i) || part.getSizeBytes() == null
                        || part.getSizeBytes() <= 0 || !repoId.equals(part.getRepoId())
                        || !uploadId.equals(part.getUploadId()) || !"UPLOADING".equals(part.getStatus())) {
                    throw new BusinessException(409, "分片序号、大小或状态不一致");
                }
            }
            return mergeVerified(session, parts, fileName, cleanPath);
        }));
    }

    private FileVO mergeVerified(UploadSession session, List<UploadPart> parts, String fileName, String path) {
        Path temp = null;
        try {
            temp = Files.createTempFile("codehub-merge-", ".tmp");
            long size = 0;
            // Sequential I/O to disk bounds heap use; this is not MinIO server-side compose.
            try (var output = Files.newOutputStream(temp)) {
                for (UploadPart part : parts) {
                    try (var input = minio.download(part.getObjectKey())) {
                        long copied = 0;
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            copied += count;
                            if (copied > part.getSizeBytes()) throw new BusinessException(409, "分片内容长度超出记录");
                            output.write(buffer, 0, count);
                        }
                        if (copied != part.getSizeBytes()) throw new BusinessException(409, "分片内容长度不匹配");
                        size = Math.addExact(size, copied);
                    }
                }
            }
            String key = "repos/" + session.getRepoId() + "/" + UUID.randomUUID();
            deleteOnRollback(key);
            try (var input = Files.newInputStream(temp)) {
                minio.upload(key, input, "application/octet-stream", size);
            }
            FileEntity file = new FileEntity();
            file.setRepoId(session.getRepoId());
            file.setPath(path);
            file.setObjectKey(key);
            file.setMimeType("application/octet-stream");
            file.setSizeBytes(size);
            fileMapper.insert(file);
            Commit commit = new Commit();
            commit.setRepoId(session.getRepoId());
            commit.setAuthorId(session.getUserId());
            commit.setMessage("Upload file (chunked): " + path);
            commitMapper.insert(commit);
            file.setCommitId(commit.getId());
            fileMapper.updateById(file);
            session.setStatus("MERGED");
            session.setMergedFileId(file.getId());
            session.setMergedPath(path);
            session.setMergedFileName(fileName);
            sessionMapper.updateById(session);
            partMapper.delete(new LambdaQueryWrapper<UploadPart>().eq(UploadPart::getUploadId, session.getUploadId()));
            if (indexDispatcher.accepts(file)) {
                try (var input = Files.newInputStream(temp)) { indexDispatcher.schedule(file, input); }
            }
            afterCommit(() -> {
                for (UploadPart part : parts) safeDelete(part.getObjectKey());
                try { redis.delete("repo:file_tree:" + session.getRepoId()); }
                catch (Exception e) { log.warn("合并已提交，缓存失效失败，等待TTL", e); }
            });
            return toVO(file);
        } catch (IOException e) {
            throw new IllegalStateException("分片合并读写失败", e);
        } finally {
            if (temp != null) {
                try { Files.deleteIfExists(temp); }
                catch (IOException e) { log.warn("临时合并文件清理失败: {}", temp, e); }
            }
        }
    }

    private <T> T inTransaction(Supplier<T> action) {
        // Explicit transaction inside the session lock; commit/rollback finishes before unlocking.
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx.execute(status -> action.get());
    }

    private <T> T withLock(String id, Supplier<T> action) {
        RLock lock = redisson.getLock("lock:upload:" + id);
        boolean held;
        try { held = lock.tryLock(0, TimeUnit.SECONDS); } // Watchdog lease, no fixed 30s expiry.
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("上传操作被中断");
        }
        if (!held) throw new BusinessException(409, "上传会话正在处理，请稍后重试");
        try { return action.get(); }
        finally { if (lock.isHeldByCurrentThread()) lock.unlock(); }
    }

    private void checkOwner(UploadSession session, Long repoId, Long userId) {
        if (!repoId.equals(session.getRepoId()) || !userId.equals(session.getUserId())) {
            throw new BusinessException(403, "上传会话不属于当前用户和仓库");
        }
    }

    private void validateId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}")) throw new BusinessException("非法uploadId");
    }

    private void deleteOnRollback(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) safeDelete(key);
            }
        });
    }

    private void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }

    private void safeDelete(String key) {
        try { minio.delete(key); }
        catch (Exception e) { log.warn("临时对象清理失败，需后续清理: {}", key, e); }
    }

    private FileVO toVO(FileEntity file) {
        FileVO vo = new FileVO();
        BeanUtils.copyProperties(file, vo);
        return vo;
    }
}
