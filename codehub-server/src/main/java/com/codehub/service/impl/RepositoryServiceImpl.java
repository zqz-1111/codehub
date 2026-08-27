package com.codehub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codehub.common.BusinessException;
import com.codehub.dto.CreateRepoRequest;
import com.codehub.dto.RepoVO;
import com.codehub.entity.Commit;
import com.codehub.entity.Repository;
import com.codehub.entity.RepositoryMember;
import com.codehub.entity.User;
import com.codehub.entity.AiChatHistory;
import com.codehub.entity.CodeIndex;
import com.codehub.entity.FileEntity;
import com.codehub.mapper.AiChatHistoryMapper;
import com.codehub.mapper.CodeIndexMapper;
import com.codehub.mapper.CommitMapper;
import com.codehub.mapper.FileMapper;
import com.codehub.mapper.RepositoryMapper;
import com.codehub.mapper.RepositoryMemberMapper;
import com.codehub.mapper.UserMapper;
import com.codehub.service.RepositoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RepositoryServiceImpl extends ServiceImpl<RepositoryMapper, Repository> implements RepositoryService {

    private final RepositoryMemberMapper memberMapper;
    private final CommitMapper commitMapper;
    private final UserMapper userMapper;
    private final FileMapper fileMapper;
    private final CodeIndexMapper codeIndexMapper;
    private final AiChatHistoryMapper chatHistoryMapper;

    @Override
    @Transactional
    public RepoVO createRepo(Long userId, CreateRepoRequest request) {
        // 检查仓库名是否已存在（同一用户下）
        long count = count(new LambdaQueryWrapper<Repository>()
                .eq(Repository::getOwnerId, userId)
                .eq(Repository::getName, request.getName()));
        if (count > 0) {
            throw new BusinessException("仓库名已存在");
        }

        // 创建仓库
        Repository repo = new Repository();
        repo.setName(request.getName());
        repo.setOwnerId(userId);
        repo.setDescription(request.getDescription());
        repo.setVisibility(request.getVisibility() != null ? request.getVisibility() : "PRIVATE");
        repo.setDefaultBranch("main");
        repo.setStarCount(0);
        save(repo);

        // 创建OWNER成员
        RepositoryMember owner = new RepositoryMember();
        owner.setRepoId(repo.getId());
        owner.setUserId(userId);
        owner.setRole("OWNER");
        memberMapper.insert(owner);

        // 创建初始Commit（README.md）
        Commit initCommit = new Commit();
        initCommit.setRepoId(repo.getId());
        initCommit.setMessage("Initial commit: add README.md");
        initCommit.setAuthorId(userId);
        initCommit.setParentCommitId(null);
        commitMapper.insert(initCommit);

        return toRepoVO(repo);
    }

    @Override
    public RepoVO getRepoDetail(Long repoId, Long userId) {
        Repository repo = getById(repoId);
        if (repo == null) {
            throw new BusinessException("仓库不存在");
        }

        // 检查权限：PUBLIC仓库所有人可见，PRIVATE只有成员可见
        if ("PRIVATE".equals(repo.getVisibility())) {
            RepositoryMember member = memberMapper.selectOne(
                    new LambdaQueryWrapper<RepositoryMember>()
                            .eq(RepositoryMember::getRepoId, repoId)
                            .eq(RepositoryMember::getUserId, userId));
            if (member == null) {
                throw new BusinessException(403, "无权访问该仓库");
            }
        }

        return toRepoVO(repo);
    }

    @Override
    public IPage<RepoVO> listMyRepos(Long userId, int page, int size) {
        Page<Repository> pageParam = new Page<>(page, size);
        IPage<Repository> result = page(pageParam, new LambdaQueryWrapper<Repository>()
                .eq(Repository::getOwnerId, userId)
                .orderByDesc(Repository::getUpdatedAt));

        return result.convert(this::toRepoVO);
    }

    @Override
    public IPage<RepoVO> listPublicRepos(int page, int size) {
        Page<Repository> pageParam = new Page<>(page, size);
        IPage<Repository> result = page(pageParam, new LambdaQueryWrapper<Repository>()
                .eq(Repository::getVisibility, "PUBLIC")
                .orderByDesc(Repository::getUpdatedAt));

        return result.convert(this::toRepoVO);
    }

    @Override
    @Transactional
    public void deleteRepo(Long repoId, Long userId) {
        Repository repo = getById(repoId);
        if (repo == null) {
            throw new BusinessException("仓库不存在");
        }
        if (!repo.getOwnerId().equals(userId)) {
            throw new BusinessException(403, "只有仓库所有者才能删除");
        }

        // 按顺序删除所有关联记录（先删子表再删父表）
        chatHistoryMapper.delete(new LambdaQueryWrapper<AiChatHistory>()
                .eq(AiChatHistory::getRepoId, repoId));
        codeIndexMapper.delete(new LambdaQueryWrapper<CodeIndex>()
                .eq(CodeIndex::getRepoId, repoId));
        fileMapper.delete(new LambdaQueryWrapper<FileEntity>()
                .eq(FileEntity::getRepoId, repoId));
        memberMapper.delete(new LambdaQueryWrapper<RepositoryMember>()
                .eq(RepositoryMember::getRepoId, repoId));
        commitMapper.delete(new LambdaQueryWrapper<Commit>()
                .eq(Commit::getRepoId, repoId));
        removeById(repoId);
    }

    private RepoVO toRepoVO(Repository repo) {
        RepoVO vo = new RepoVO();
        vo.setId(repo.getId());
        vo.setName(repo.getName());
        vo.setOwnerId(repo.getOwnerId());
        vo.setOwnerName(getUsernameById(repo.getOwnerId()));
        vo.setDescription(repo.getDescription());
        vo.setVisibility(repo.getVisibility());
        vo.setDefaultBranch(repo.getDefaultBranch());
        vo.setStarCount(repo.getStarCount());
        vo.setCreatedAt(repo.getCreatedAt());
        vo.setUpdatedAt(repo.getUpdatedAt());
        return vo;
    }

    private String getUsernameById(Long userId) {
        User user = userMapper.selectById(userId);
        return user != null ? user.getUsername() : "unknown";
    }
}
