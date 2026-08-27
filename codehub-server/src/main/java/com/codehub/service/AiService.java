package com.codehub.service;

import com.codehub.dto.AiChatRequest;

import java.io.OutputStream;
import java.util.function.Consumer;

public interface AiService {

    /**
     * 构建代码索引（文件上传后调用）
     */
    void buildIndex(Long repoId, Long fileId, String filePath, String content);

    /**
     * 流式AI问答（NDJSON输出）
     * @param repoId 仓库ID
     * @param userId 用户ID
     * @param request 问答请求
     * @param writer NDJSON输出回调
     */
    void streamAsk(Long repoId, Long userId, AiChatRequest request, Consumer<String> writer);

    /**
     * 获取对话历史
     */
    Object getHistory(Long repoId, Long userId, int page, int size);
}
