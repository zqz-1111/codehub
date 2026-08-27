package com.codehub.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.codehub.dto.ModelConfigDTO;
import com.codehub.dto.UserVO;
import com.codehub.entity.AuditLog;
import com.codehub.entity.ModelConfig;
import com.codehub.entity.Repository;

import java.util.List;
import java.util.Map;

public interface AdminService {

    // ========== 用户管理 ==========
    IPage<UserVO> listUsers(int page, int size, String keyword);
    void updateUserRole(Long userId, String role);
    void updateUserStatus(Long userId, String status);
    void resetPassword(Long userId, String newPassword);

    // ========== 仓库管理 ==========
    IPage<Repository> listAllRepos(int page, int size);
    void forceDeleteRepo(Long repoId);
    void updateRepoVisibility(Long repoId, String visibility);

    // ========== 模型配置 ==========
    IPage<ModelConfig> listModelConfigs(int page, int size);
    ModelConfig createModelConfig(ModelConfigDTO dto);
    ModelConfig updateModelConfig(Long id, ModelConfigDTO dto);
    void deleteModelConfig(Long id);
    void toggleModelEnabled(Long id, boolean enabled);

    // ========== 审计日志 ==========
    void recordAudit(Long userId, String action, String targetType, Long targetId, String detail, String ip);
    IPage<AuditLog> listAuditLogs(int page, int size, String action, Long userId);

    // ========== 统计数据 ==========
    Map<String, Object> getDashboardStats();
    List<Map<String, Object>> getDailyStats(int days);
}
