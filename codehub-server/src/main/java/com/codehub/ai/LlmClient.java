package com.codehub.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.function.Consumer;

/**
 * OpenAI兼容LLM客户端
 * 支持DeepSeek/阿里云百炼/小米等所有兼容OpenAI接口的大模型
 */
@Slf4j
@Component
public class LlmClient {

    @Value("${llm.base-url}")
    private String baseUrl;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 流式调用LLM
     * @param systemPrompt 系统提示词
     * @param userMessage 用户消息
     * @param context 相关代码上下文
     * @param onToken 每收到一个token的回调
     */
    public void streamChat(String systemPrompt, String userMessage, String context,
                           Consumer<String> onToken) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("stream", true);

            ArrayNode messages = body.putArray("messages");
            ObjectNode sysMsg = messages.addObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);

            if (context != null && !context.isBlank()) {
                ObjectNode ctxMsg = messages.addObject();
                ctxMsg.put("role", "system");
                ctxMsg.put("content", "以下是相关代码上下文：\n\n" + context);
            }

            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);

            String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            // 流式接收SSE
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            log.error("LLM调用失败: status={}", response.statusCode());
                            onToken.accept("[ERROR] LLM调用失败，状态码: " + response.statusCode());
                            return;
                        }
                        response.body().forEachOrdered(line -> {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6).trim();
                                if ("[DONE]".equals(data)) return;
                                try {
                                    JsonNode json = objectMapper.readTree(data);
                                    JsonNode delta = json.path("choices").path(0).path("delta").path("content");
                                    if (!delta.isMissingNode() && !delta.isNull()) {
                                        onToken.accept(delta.asText());
                                    }
                                } catch (Exception e) {
                                    // 忽略解析错误（可能是空行）
                                }
                            }
                        });
                    })
                    .exceptionally(e -> {
                        log.error("LLM流式调用异常", e);
                        onToken.accept("[ERROR] LLM调用异常: " + e.getMessage());
                        return null;
                    })
                    .join();

        } catch (Exception e) {
            log.error("LLM调用构建失败", e);
            onToken.accept("[ERROR] 请求构建失败: " + e.getMessage());
        }
    }

    /**
     * 非流式调用（用于简单场景）
     */
    public String chat(String systemPrompt, String userMessage) {
        StringBuilder result = new StringBuilder();
        streamChat(systemPrompt, userMessage, null, result::append);
        return result.toString();
    }
}
