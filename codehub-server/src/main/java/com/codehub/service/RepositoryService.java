package com.codehub.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.codehub.dto.CreateRepoRequest;
import com.codehub.dto.RepoVO;
import com.codehub.entity.Repository;

public interface RepositoryService extends IService<Repository> {

    /**
     * 创建仓库（自动初始化README + 初始Commit + OWNER成员）
     */
    RepoVO createRepo(Long userId, CreateRepoRequest request);

    /**
     * 获取仓库详情（检查权限）
     */
    RepoVO getRepoDetail(Long repoId, Long userId);

    /**
     * 仓库列表（我的仓库）
     */
    IPage<RepoVO> listMyRepos(Long userId, int page, int size);

    /**
     * 仓库列表（Public仓库）
     */
    IPage<RepoVO> listPublicRepos(int page, int size);

    /**
     * 删除仓库（只有OWNER可以）
     */
    void deleteRepo(Long repoId, Long userId);
}
