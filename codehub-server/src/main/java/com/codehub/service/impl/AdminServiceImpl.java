package com.codehub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codehub.common.BusinessException;
import com.codehub.dto.ModelConfigDTO;
import com.codehub.dto.UserVO;
import com.codehub.entity.*;
import com.codehub.mapper.*;
import com.codehub.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserMapper userMapper;
    private final RepositoryMapper repoMapper;
    private final RepositoryMemberMapper memberMapper;
    private final CommitMapper commitMapper;
    private final FileMapper fileMapper;
    private final CodeIndexMapper codeIndexMapper;
    private final AiChatHistoryMapper chatHistoryMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final AuditLogMapper auditLogMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // ========== 用户管理 ==========

    @Override
    public IPage<UserVO> listUsers(int page, int size, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(User::getUsername, keyword)
                    .or().like(User::getEmail, keyword);
        }
        wrapper.orderByDesc(User::getCreatedAt);

        IPage<User> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        return result.convert(user -> {
            UserVO vo = new UserVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setEmail(user.getEmail());
            vo.setRole(user.getRole());
            vo.setStatus(user.getStatus());
            vo.setCreatedAt(user.getCreatedAt());
            return vo;
        });
    }

    @Override
    public void updateUserRole(Long userId, String role) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        user.setRole(role);
        userMapper.updateById(user);
    }

    @Override
    public void updateUserStatus(Long userId, String status) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Override
    public void resetPassword(Long userId, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }

    // ========== 仓库管理 ==========

    @Override
    public IPage<Repository> listAllRepos(int page, int size) {
        return repoMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Repository>().orderByDesc(Repository::getCreatedAt));
    }

    @Override
    @Transactional
    public void forceDeleteRepo(Long repoId) {
        Repository repo = repoMapper.selectById(repoId);
        if (repo == null) throw new BusinessException("仓库不存在");

        chatHistoryMapper.delete(new LambdaQueryWrapper<AiChatHistory>().eq(AiChatHistory::getRepoId, repoId));
        codeIndexMapper.delete(new LambdaQueryWrapper<CodeIndex>().eq(CodeIndex::getRepoId, repoId));
        fileMapper.delete(new LambdaQueryWrapper<FileEntity>().eq(FileEntity::getRepoId, repoId));
        memberMapper.delete(new LambdaQueryWrapper<RepositoryMember>().eq(RepositoryMember::getRepoId, repoId));
        commitMapper.delete(new LambdaQueryWrapper<Commit>().eq(Commit::getRepoId, repoId));
        repoMapper.deleteById(repoId);
    }

    @Override
    public void updateRepoVisibility(Long repoId, String visibility) {
        Repository repo = repoMapper.selectById(repoId);
        if (repo == null) throw new BusinessException("仓库不存在");
        repo.setVisibility(visibility);
        repoMapper.updateById(repo);
    }

    // ========== 模型配置 ==========

    @Override
    public IPage<ModelConfig> listModelConfigs(int page, int size) {
        return modelConfigMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ModelConfig>().orderByDesc(ModelConfig::getCreatedAt));
    }

    @Override
    public ModelConfig createModelConfig(ModelConfigDTO dto) {
        ModelConfig config = new ModelConfig();
        config.setProvider(dto.getProvider());
        config.setModelName(dto.getModelName());
        config.setBaseUrl(dto.getBaseUrl());
        config.setApiKey(dto.getApiKey());
        config.setEnabled(dto.getEnabled());
        modelConfigMapper.insert(config);
        return config;
    }

    @Override
    public ModelConfig updateModelConfig(Long id, ModelConfigDTO dto) {
        ModelConfig config = modelConfigMapper.selectById(id);
        if (config == null) throw new BusinessException("配置不存在");
        config.setProvider(dto.getProvider());
        config.setModelName(dto.getModelName());
        config.setBaseUrl(dto.getBaseUrl());
        config.setApiKey(dto.getApiKey());
        config.setEnabled(dto.getEnabled());
        modelConfigMapper.updateById(config);
        return config;
    }

    @Override
    public void deleteModelConfig(Long id) {
        modelConfigMapper.deleteById(id);
    }

    @Override
    public void toggleModelEnabled(Long id, boolean enabled) {
        ModelConfig config = modelConfigMapper.selectById(id);
        if (config == null) throw new BusinessException("配置不存在");
        config.setEnabled(enabled);
        modelConfigMapper.updateById(config);
    }

    // ========== 审计日志 ==========

    @Override
    public void recordAudit(Long userId, String action, String targetType, Long targetId, String detail, String ip) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        log.setIp(ip);
        auditLogMapper.insert(log);
    }

    @Override
    public IPage<AuditLog> listAuditLogs(int page, int size, String action, Long userId) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (action != null && !action.isBlank()) {
            wrapper.eq(AuditLog::getAction, action);
        }
        if (userId != null) {
            wrapper.eq(AuditLog::getUserId, userId);
        }
        wrapper.orderByDesc(AuditLog::getCreatedAt);
        return auditLogMapper.selectPage(new Page<>(page, size), wrapper);
    }

    // ========== 统计数据 ==========

    @Override
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        // 总数统计
        stats.put("totalUsers", userMapper.selectCount(null));
        stats.put("totalRepos", repoMapper.selectCount(null));
        stats.put("totalFiles", fileMapper.selectCount(null));
        stats.put("totalChats", chatHistoryMapper.selectCount(null));

        // 今日新增
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        stats.put("todayNewUsers", userMapper.selectCount(
                new LambdaQueryWrapper<User>().ge(User::getCreatedAt, todayStart)));
        stats.put("todayNewRepos", repoMapper.selectCount(
                new LambdaQueryWrapper<Repository>().ge(Repository::getCreatedAt, todayStart)));
        stats.put("todayNewChats", chatHistoryMapper.selectCount(
                new LambdaQueryWrapper<AiChatHistory>().ge(AiChatHistory::getCreatedAt, todayStart)));

        // 活跃模型数
        stats.put("activeModels", modelConfigMapper.selectCount(
                new LambdaQueryWrapper<ModelConfig>().eq(ModelConfig::getEnabled, true)));

        return stats;
    }

    @Override
    public List<Map<String, Object>> getDailyStats(int days) {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime dayStart = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime dayEnd = LocalDateTime.of(date, LocalTime.MAX);

            Map<String, Object> dayStat = new HashMap<>();
            dayStat.put("date", date.toString());
            dayStat.put("users", userMapper.selectCount(
                    new LambdaQueryWrapper<User>()
                            .ge(User::getCreatedAt, dayStart)
                            .le(User::getCreatedAt, dayEnd)));
            dayStat.put("repos", repoMapper.selectCount(
                    new LambdaQueryWrapper<Repository>()
                            .ge(Repository::getCreatedAt, dayStart)
                            .le(Repository::getCreatedAt, dayEnd)));
            dayStat.put("chats", chatHistoryMapper.selectCount(
                    new LambdaQueryWrapper<AiChatHistory>()
                            .ge(AiChatHistory::getCreatedAt, dayStart)
                            .le(AiChatHistory::getCreatedAt, dayEnd)));
            dayStat.put("files", fileMapper.selectCount(
                    new LambdaQueryWrapper<FileEntity>()
                            .ge(FileEntity::getCreatedAt, dayStart)
                            .le(FileEntity::getCreatedAt, dayEnd)));
            result.add(dayStat);
        }
        return result;
    }
}
