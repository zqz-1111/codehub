package com.codehub.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.codehub.annotation.Idempotent;
import com.codehub.common.Result;
import com.codehub.dto.CreateRepoRequest;
import com.codehub.dto.RepoVO;
import com.codehub.service.RepositoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "仓库管理", description = "仓库CRUD、权限控制")
@RestController
@RequestMapping("/repos")
@RequiredArgsConstructor
public class RepositoryController {

    private final RepositoryService repositoryService;

    @Operation(summary = "创建仓库")
    @Idempotent(windowSeconds = 5)
    @PostMapping
    public Result<RepoVO> createRepo(@Valid @RequestBody CreateRepoRequest request,
                                      HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(repositoryService.createRepo(userId, request));
    }

    @Operation(summary = "获取仓库详情")
    @GetMapping("/{id}")
    public Result<RepoVO> getRepo(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return Result.success(repositoryService.getRepoDetail(id, userId));
    }

    @Operation(summary = "我的仓库列表")
    @GetMapping("/my")
    public Result<IPage<RepoVO>> listMyRepos(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(repositoryService.listMyRepos(userId, page, size));
    }

    @Operation(summary = "Public仓库列表")
    @GetMapping("/public")
    public Result<IPage<RepoVO>> listPublicRepos(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(repositoryService.listPublicRepos(page, size));
    }

    @Operation(summary = "删除仓库")
    @DeleteMapping("/{id}")
    public Result<Void> deleteRepo(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        repositoryService.deleteRepo(id, userId);
        return Result.success();
    }
}
