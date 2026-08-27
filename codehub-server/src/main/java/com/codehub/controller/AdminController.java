package com.codehub.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.codehub.common.Result;
import com.codehub.dto.ModelConfigDTO;
import com.codehub.dto.UserVO;
import com.codehub.entity.AuditLog;
import com.codehub.entity.ModelConfig;
import com.codehub.entity.Repository;
import com.codehub.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "管理后台", description = "用户管理、仓库管理、模型配置、审计日志、统计数据")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // ========== 权限检查 ==========

    private Long requireAdmin(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new com.codehub.common.BusinessException(401, "未登录");
        }
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            throw new com.codehub.common.BusinessException(403, "需要管理员权限");
        }
        return userId;
    }

    // ========== 用户管理 ==========

    @Operation(summary = "用户列表")
    @GetMapping("/users")
    public Result<IPage<UserVO>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(adminService.listUsers(page, size, keyword));
    }

    @Operation(summary = "修改用户角色")
    @PutMapping("/users/{userId}/role")
    public Result<Void> updateUserRole(
            @PathVariable Long userId,
            @RequestParam String role,
            HttpServletRequest request) {
        Long adminId = requireAdmin(request);
        adminService.updateUserRole(userId, role);
        adminService.recordAudit(adminId, "UPDATE_ROLE", "USER", userId, "角色改为" + role, request.getRemoteAddr());
        return Result.success();
    }

    @Operation(summary = "修改用户状态")
    @PutMapping("/users/{userId}/status")
    public Result<Void> updateUserStatus(
            @PathVariable Long userId,
            @RequestParam String status,
            HttpServletRequest request) {
        Long adminId = requireAdmin(request);
        adminService.updateUserStatus(userId, status);
        adminService.recordAudit(adminId, "UPDATE_STATUS", "USER", userId, "状态改为" + status, request.getRemoteAddr());
        return Result.success();
    }

    @Operation(summary = "重置密码")
    @PutMapping("/users/{userId}/password")
    public Result<Void> resetPassword(
            @PathVariable Long userId,
            @RequestParam String newPassword,
            HttpServletRequest request) {
        Long adminId = requireAdmin(request);
        adminService.resetPassword(userId, newPassword);
        adminService.recordAudit(adminId, "RESET_PASSWORD", "USER", userId, "重置密码", request.getRemoteAddr());
        return Result.success();
    }

    // ========== 仓库管理 ==========

    @Operation(summary = "所有仓库列表")
    @GetMapping("/repos")
    public Result<IPage<Repository>> listAllRepos(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(adminService.listAllRepos(page, size));
    }

    @Operation(summary = "强制删除仓库")
    @DeleteMapping("/repos/{repoId}")
    public Result<Void> forceDeleteRepo(
            @PathVariable Long repoId,
            HttpServletRequest request) {
        Long adminId = requireAdmin(request);
        adminService.forceDeleteRepo(repoId);
        adminService.recordAudit(adminId, "FORCE_DELETE_REPO", "REPO", repoId, "强制删除仓库", request.getRemoteAddr());
        return Result.success();
    }

    @Operation(summary = "修改仓库可见性")
    @PutMapping("/repos/{repoId}/visibility")
    public Result<Void> updateRepoVisibility(
            @PathVariable Long repoId,
            @RequestParam String visibility,
            HttpServletRequest request) {
        Long adminId = requireAdmin(request);
        adminService.updateRepoVisibility(repoId, visibility);
        adminService.recordAudit(adminId, "UPDATE_VISIBILITY", "REPO", repoId, "可见性改为" + visibility, request.getRemoteAddr());
        return Result.success();
    }

    // ========== 模型配置 ==========

    @Operation(summary = "模型配置列表")
    @GetMapping("/models")
    public Result<IPage<ModelConfig>> listModelConfigs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(adminService.listModelConfigs(page, size));
    }

    @Operation(summary = "创建模型配置")
    @PostMapping("/models")
    public Result<ModelConfig> createModelConfig(
            @Valid @RequestBody ModelConfigDTO dto,
            HttpServletRequest request) {
        Long adminId = requireAdmin(request);
        ModelConfig config = adminService.createModelConfig(dto);
        adminService.recordAudit(adminId, "CREATE_MODEL", "MODEL", config.getId(), dto.getProvider() + "/" + dto.getModelName(), request.getRemoteAddr());
        return Result.success(config);
    }

    @Operation(summary = "更新模型配置")
    @PutMapping("/models/{id}")
    public Result<ModelConfig> updateModelConfig(
            @PathVariable Long id,
            @Valid @RequestBody ModelConfigDTO dto,
            HttpServletRequest request) {
        Long adminId = requireAdmin(request);
        ModelConfig config = adminService.updateModelConfig(id, dto);
        adminService.recordAudit(adminId, "UPDATE_MODEL", "MODEL", id, dto.getProvider() + "/" + dto.getModelName(), request.getRemoteAddr());
        return Result.success(config);
    }

    @Operation(summary = "删除模型配置")
    @DeleteMapping("/models/{id}")
    public Result<Void> deleteModelConfig(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long adminId = requireAdmin(request);
        adminService.deleteModelConfig(id);
        adminService.recordAudit(adminId, "DELETE_MODEL", "MODEL", id, "删除模型配置", request.getRemoteAddr());
        return Result.success();
    }

    @Operation(summary = "切换模型启用状态")
    @PutMapping("/models/{id}/toggle")
    public Result<Void> toggleModelEnabled(
            @PathVariable Long id,
            @RequestParam boolean enabled,
            HttpServletRequest request) {
        requireAdmin(request);
        adminService.toggleModelEnabled(id, enabled);
        return Result.success();
    }

    // ========== 审计日志 ==========

    @Operation(summary = "审计日志列表")
    @GetMapping("/audit-logs")
    public Result<IPage<AuditLog>> listAuditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long userId,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(adminService.listAuditLogs(page, size, action, userId));
    }

    // ========== 统计数据 ==========

    @Operation(summary = "仪表盘统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> getDashboardStats(HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(adminService.getDashboardStats());
    }

    @Operation(summary = "每日统计趋势")
    @GetMapping("/stats/daily")
    public Result<?> getDailyStats(
            @RequestParam(defaultValue = "7") int days,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(adminService.getDailyStats(days));
    }
}
