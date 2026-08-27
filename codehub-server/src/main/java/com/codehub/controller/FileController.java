package com.codehub.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.codehub.annotation.Idempotent;
import com.codehub.common.Result;
import com.codehub.dto.CommitVO;
import com.codehub.dto.FileVO;
import com.codehub.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "文件管理", description = "文件上传、下载、删除")
@RestController
@RequestMapping("/repos/{repoId}/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "上传文件")
    @Idempotent(windowSeconds = 3, message = "文件正在上传中，请勿重复提交")
    @PostMapping
    public Result<FileVO> uploadFile(
            @PathVariable Long repoId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("path") String path,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(fileService.uploadFile(repoId, userId, path, file));
    }

    @Operation(summary = "列出仓库文件")
    @GetMapping
    public Result<List<FileVO>> listFiles(
            @PathVariable Long repoId,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return Result.success(fileService.listFiles(repoId, userId));
    }

    @Operation(summary = "获取文件预签名下载URL")
    @GetMapping("/{fileId}/download")
    public Result<String> getDownloadUrl(
            @PathVariable Long repoId,
            @PathVariable Long fileId,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return Result.success(fileService.getFileDownloadUrl(repoId, userId, fileId));
    }

    @Operation(summary = "获取文件内容（代码展示）")
    @GetMapping("/{fileId}/content")
    public Result<String> getFileContent(
            @PathVariable Long repoId,
            @PathVariable Long fileId,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        byte[] content = fileService.getFileContent(repoId, userId, fileId);
        return Result.success(new String(content, StandardCharsets.UTF_8));
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/{fileId}")
    public Result<Void> deleteFile(
            @PathVariable Long repoId,
            @PathVariable Long fileId,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        fileService.deleteFile(repoId, userId, fileId);
        return Result.success();
    }

    @Operation(summary = "提交历史")
    @GetMapping("/commits")
    public Result<IPage<CommitVO>> listCommits(
            @PathVariable Long repoId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return Result.success(fileService.listCommits(repoId, userId, page, size));
    }

    @Operation(summary = "上传分片")
    @PostMapping("/chunks")
    public Result<Void> uploadChunk(
            @PathVariable Long repoId,
            @RequestParam String uploadId,
            @RequestParam int chunkIndex,
            @RequestParam int totalChunks,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        fileService.uploadChunk(repoId, userId, uploadId, chunkIndex, totalChunks, file);
        return Result.success();
    }

    @Operation(summary = "合并分片")
    @PostMapping("/chunks/merge")
    public Result<FileVO> mergeChunks(
            @PathVariable Long repoId,
            @RequestParam String uploadId,
            @RequestParam String fileName,
            @RequestParam String path,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(fileService.mergeChunks(repoId, userId, uploadId, fileName, path));
    }
}
