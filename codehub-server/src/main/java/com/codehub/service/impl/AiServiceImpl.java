package com.codehub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codehub.ai.CodeChunker;
import com.codehub.ai.EmbeddingClient;
import com.codehub.ai.LlmClient;
import com.codehub.common.BusinessException;
import com.codehub.dto.AiChatRequest;
import com.codehub.entity.AiChatHistory;
import com.codehub.entity.CodeIndex;
import com.codehub.entity.Repository;
import com.codehub.entity.RepositoryMember;
import com.codehub.mapper.AiChatHistoryMapper;
import com.codehub.mapper.CodeIndexMapper;
import com.codehub.mapper.RepositoryMapper;
import com.codehub.mapper.RepositoryMemberMapper;
import com.codehub.service.AiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final LlmClient llmClient;
    private final CodeChunker codeChunker;
    private final EmbeddingClient embeddingClient;
    private final CodeIndexMapper codeIndexMapper;
    private final AiChatHistoryMapper chatHistoryMapper;
    private final RepositoryMapper repoMapper;
    private final RepositoryMemberMapper memberMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            你是 CodeHub AI 代码助手。你的职责是帮助用户理解代码仓库中的代码。
            规则：
            1. 基于提供的代码上下文回答问题
            2. 如果上下文中没有相关信息，如实告知
            3. 回答要简洁、专业，适合开发者阅读
            4. 引用代码时标注文件路径
            5. 使用Markdown格式回答
            """;

    @Override
    public void buildIndex(Long repoId, Long fileId, String filePath, String content) {
        // 删除该文件的旧索引
        codeIndexMapper.delete(new LambdaQueryWrapper<CodeIndex>()
                .eq(CodeIndex::getRepoId, repoId)
                .eq(CodeIndex::getFileId, fileId));

        // 切分代码
        List<CodeChunker.CodeChunk> chunks = codeChunker.chunkCode(filePath, content);

        // 存入索引
        for (CodeChunker.CodeChunk chunk : chunks) {
            CodeIndex index = new CodeIndex();
            index.setRepoId(repoId);
            index.setFileId(fileId);
            index.setChunkType(chunk.getChunkType());
            index.setChunkName(chunk.getChunkName());
            index.setContent(chunk.getContent());
            index.setImports(chunk.getImports());
            index.setPathFeatures(filePath);

            // 生成Embedding向量
            try {
                String embedText = chunk.getChunkName() + " " + chunk.getContent();
                float[] vector = embeddingClient.embed(embedText);
                if (vector != null) {
                    index.setEmbedding(objectMapper.writeValueAsString(vector));
                }
            } catch (Exception e) {
                log.debug("Embedding生成失败（不影响索引）: {}", e.getMessage());
            }

            codeIndexMapper.insert(index);
        }

        log.info("代码索引构建完成: repoId={}, file={}, chunks={}", repoId, filePath, chunks.size());
    }

    @Override
    public void streamAsk(Long repoId, Long userId, AiChatRequest request, Consumer<String> writer) {
        // 1. 限流检查（10次/分钟）
        String rateLimitKey = "ratelimit:ai:" + userId;
        Long count = redisTemplate.opsForValue().increment(rateLimitKey);
        if (count != null && count == 1) {
            redisTemplate.expire(rateLimitKey, 1, TimeUnit.MINUTES);
        }
        if (count != null && count > 10) {
            sendEvent(writer, "error", "请求过于频繁，请稍后再试（限制10次/分钟）");
            return;
        }

        // 2. 权限检查
        checkAccess(repoId, userId);

        // 3. 发送状态事件
        sendEvent(writer, "status", "正在检索相关代码...");

        // 4. 检索相关代码（关键词搜索）
        String question = request.getQuestion();
        List<CodeIndex> relevantChunks = searchRelevantCode(repoId, question);

        // 5. 组装上下文
        StringBuilder contextBuilder = new StringBuilder();
        ArrayNode references = objectMapper.createArrayNode();

        for (CodeIndex chunk : relevantChunks) {
            contextBuilder.append("## 文件: ").append(chunk.getPathFeatures());
            contextBuilder.append(" (").append(chunk.getChunkType()).append(": ");
            contextBuilder.append(chunk.getChunkName()).append(")\n");
            contextBuilder.append(chunk.getContent()).append("\n\n");

            ObjectNode ref = objectMapper.createObjectNode();
            ref.put("file", chunk.getPathFeatures());
            ref.put("type", chunk.getChunkType());
            ref.put("name", chunk.getChunkName());
            ref.put("fileId", chunk.getFileId());
            references.add(ref);
        }

        // 6. 发送引用事件
        if (!references.isEmpty()) {
            sendEvent(writer, "reference", references.toString());
        }

        sendEvent(writer, "status", "正在生成回答...");

        // 7. 调用LLM流式生成
        StringBuilder answerBuilder = new StringBuilder();
        llmClient.streamChat(SYSTEM_PROMPT, question, contextBuilder.toString(), token -> {
            answerBuilder.append(token);
            sendEvent(writer, "token", token);
        });

        // 8. 保存对话历史
        AiChatHistory history = new AiChatHistory();
        history.setRepoId(repoId);
        history.setUserId(userId);
        history.setQuestion(question);
        history.setAnswer(answerBuilder.toString());
        chatHistoryMapper.insert(history);

        // 9. 发送完成事件
        sendEvent(writer, "done", "");
    }

    @Override
    public Object getHistory(Long repoId, Long userId, int page, int size) {
        return chatHistoryMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<AiChatHistory>()
                        .eq(AiChatHistory::getRepoId, repoId)
                        .eq(AiChatHistory::getUserId, userId)
                        .orderByDesc(AiChatHistory::getCreatedAt));
    }

    /**
     * 检索相关代码：优先Embedding向量检索，降级到关键词检索
     */
    private List<CodeIndex> searchRelevantCode(Long repoId, String question) {
        // 尝试向量检索
        float[] questionVector = embeddingClient.embed(question);
        if (questionVector != null) {
            return vectorSearch(repoId, question, questionVector);
        }

        // 降级到关键词检索
        return keywordSearch(repoId, question);
    }

    /**
     * Embedding向量检索
     */
    private List<CodeIndex> vectorSearch(Long repoId, String question, float[] questionVector) {
        // 获取仓库所有有向量的索引
        List<CodeIndex> allIndexes = codeIndexMapper.selectList(
                new LambdaQueryWrapper<CodeIndex>()
                        .eq(CodeIndex::getRepoId, repoId)
                        .isNotNull(CodeIndex::getEmbedding)
                        .last("LIMIT 100"));

        if (allIndexes.isEmpty()) {
            return keywordSearch(repoId, question);
        }

        // 计算余弦相似度并排序
        allIndexes.sort((a, b) -> {
            float simA = getSimilarity(a.getEmbedding(), questionVector);
            float simB = getSimilarity(b.getEmbedding(), questionVector);
            return Float.compare(simB, simA); // 降序
        });

        // 返回前5个最相关的
        return allIndexes.subList(0, Math.min(5, allIndexes.size()));
    }

    private float getSimilarity(String embeddingJson, float[] questionVector) {
        try {
            float[] vector = objectMapper.readValue(embeddingJson, float[].class);
            return EmbeddingClient.cosineSimilarity(vector, questionVector);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 关键词检索（降级方案）
     */
    private List<CodeIndex> keywordSearch(Long repoId, String question) {
        String[] keywords = question.split("[\\s,，.。?？!！：:（）()]+");

        List<String> validKeywords = new java.util.ArrayList<>();
        for (String keyword : keywords) {
            if (keyword.isEmpty()) continue;
            if (keyword.matches("[a-zA-Z]+") && keyword.length() >= 2) {
                validKeywords.add(keyword);
            } else if (keyword.matches("[\\u4e00-\\u9fa5]+") && keyword.length() >= 1) {
                validKeywords.add(keyword);
            }
        }

        if (validKeywords.isEmpty()) {
            return codeIndexMapper.selectList(
                    new LambdaQueryWrapper<CodeIndex>()
                            .eq(CodeIndex::getRepoId, repoId)
                            .last("LIMIT 10"));
        }

        LambdaQueryWrapper<CodeIndex> wrapper = new LambdaQueryWrapper<CodeIndex>()
                .eq(CodeIndex::getRepoId, repoId);

        wrapper.and(w -> {
            for (int i = 0; i < validKeywords.size(); i++) {
                String kw = validKeywords.get(i);
                if (i > 0) w.or();
                w.like(CodeIndex::getChunkName, kw)
                        .or().like(CodeIndex::getContent, kw)
                        .or().like(CodeIndex::getImports, kw)
                        .or().like(CodeIndex::getPathFeatures, kw);
            }
        });

        wrapper.last("LIMIT 10");
        List<CodeIndex> results = codeIndexMapper.selectList(wrapper);

        if (results.isEmpty()) {
            return codeIndexMapper.selectList(
                    new LambdaQueryWrapper<CodeIndex>()
                            .eq(CodeIndex::getRepoId, repoId)
                            .last("LIMIT 10"));
        }

        return results;
    }

    private void checkAccess(Long repoId, Long userId) {
        Repository repo = repoMapper.selectById(repoId);
        if (repo == null) throw new BusinessException("仓库不存在");

        if ("PRIVATE".equals(repo.getVisibility())) {
            if (userId == null) throw new BusinessException(401, "未登录");
            RepositoryMember member = memberMapper.selectOne(
                    new LambdaQueryWrapper<RepositoryMember>()
                            .eq(RepositoryMember::getRepoId, repoId)
                            .eq(RepositoryMember::getUserId, userId));
            if (member == null) throw new BusinessException(403, "无权访问该仓库");
        }
    }

    /**
     * 发送NDJSON事件
     * 格式: {"type":"token","data":"xxx"}
     */
    private void sendEvent(Consumer<String> writer, String type, String data) {
        try {
            ObjectNode event = objectMapper.createObjectNode();
            event.put("type", type);
            event.put("data", data);
            writer.accept(objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("发送事件失败", e);
        }
    }
}
