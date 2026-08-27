package com.codehub.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.codehub.common.Result;
import com.codehub.dto.AiChatRequest;
import com.codehub.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;

@Tag(name = "AI代码助手", description = "代码问答、索引构建")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @Operation(summary = "流式AI问答（NDJSON）")
    @PostMapping(value = "/repos/{repoId}/ask", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public void askQuestion(
            @PathVariable Long repoId,
            @RequestBody AiChatRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        Long userId = (Long) httpRequest.getAttribute("userId");

        // 设置NDJSON响应头
        response.setContentType("application/x-ndjson");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("X-Accel-Buffering", "no");

        try {
            PrintWriter writer = response.getWriter();
            aiService.streamAsk(repoId, userId, request, line -> {
                writer.println(line);
                writer.flush();
            });
        } catch (Exception e) {
            // 写入错误事件
            try {
                PrintWriter writer = response.getWriter();
                writer.println("{\"type\":\"error\",\"data\":\"" + e.getMessage().replace("\"", "'") + "\"}");
                writer.flush();
            } catch (Exception ignored) {}
        }
    }

    @Operation(summary = "获取对话历史")
    @GetMapping("/repos/{repoId}/history")
    public Result<?> getHistory(
            @PathVariable Long repoId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(aiService.getHistory(repoId, userId, page, size));
    }
}
