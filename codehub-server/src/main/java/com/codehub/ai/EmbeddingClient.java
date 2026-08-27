package com.codehub.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * Embedding向量客户端
 * 调用Embedding API将文本转为向量
 */
@Slf4j
@Component
public class EmbeddingClient {

    @Value("${llm.embedding-url:}")
    private String embeddingUrl;

    @Value("${llm.embedding-key:}")
    private String embeddingKey;

    @Value("${llm.embedding-model:}")
    private String embeddingModel;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将文本转为向量
     * @return 向量数组，失败返回null
     */
    public float[] embed(String text) {
        if (embeddingUrl == null || embeddingUrl.isBlank() || embeddingKey == null || embeddingKey.equals("sk-placeholder")) {
            return null; // 未配置embedding API
        }

        try {
            String body = objectMapper.writeValueAsString(new EmbeddingRequest(embeddingModel, text));
            String url = embeddingUrl.endsWith("/") ? embeddingUrl + "embeddings" : embeddingUrl + "/embeddings";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + embeddingKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Embedding API调用失败: status={}, body={}", response.statusCode(), response.body());
                return null;
            }

            JsonNode json = objectMapper.readTree(response.body());
            JsonNode embedding = json.path("data").path(0).path("embedding");

            if (embedding.isMissingNode() || !embedding.isArray()) {
                log.warn("Embedding响应格式异常");
                return null;
            }

            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = (float) embedding.get(i).asDouble();
            }
            return vector;

        } catch (Exception e) {
            log.warn("Embedding调用异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 计算两个向量的余弦相似度
     */
    public static float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0;

        float dotProduct = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        float denominator = (float) (Math.sqrt(normA) * Math.sqrt(normB));
        return denominator == 0 ? 0 : dotProduct / denominator;
    }

    private record EmbeddingRequest(String model, String input) {}
}
