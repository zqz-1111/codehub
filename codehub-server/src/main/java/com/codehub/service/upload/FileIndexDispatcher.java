package com.codehub.service.upload;

import com.codehub.config.RabbitMQConfig;
import com.codehub.dto.FileIndexMessage;
import com.codehub.entity.FileEntity;
import com.codehub.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Best-effort indexing after metadata commits, not a transactional outbox. */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileIndexDispatcher {
    public static final int MAX_INDEX_BYTES = 1024 * 1024;
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "java", "kt", "py", "js", "ts", "tsx", "jsx", "vue", "html", "css", "scss",
            "json", "xml", "yml", "yaml", "md", "txt", "sql", "sh", "properties", "csv");
    private final RabbitTemplate rabbitTemplate;
    private final AiService aiService;

    public boolean accepts(FileEntity file) {
        String path = file.getPath().toLowerCase(Locale.ROOT);
        int dot = path.lastIndexOf('.');
        return file.getSizeBytes() != null && file.getSizeBytes() <= MAX_INDEX_BYTES
                && dot >= 0 && TEXT_EXTENSIONS.contains(path.substring(dot + 1));
    }

    public void schedule(FileEntity file, InputStream input) throws IOException {
        if (!accepts(file)) return;
        byte[] bytes = input.readNBytes(MAX_INDEX_BYTES + 1);
        if (bytes.length > MAX_INDEX_BYTES) return;
        String content;
        try {
            content = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException invalidText) {
            log.info("跳过非UTF-8文件索引: fileId={}", file.getId());
            return;
        }
        if (content.indexOf('\0') >= 0) return;
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("索引必须在文件元数据事务内登记");
        }
        FileIndexMessage message = new FileIndexMessage(file.getRepoId(), file.getId(),
                file.getPath(), content, UUID.randomUUID().toString());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { dispatch(message); }
        });
    }

    private void dispatch(FileIndexMessage message) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.FILE_EXCHANGE, RabbitMQConfig.FILE_INDEX_KEY,
                    message, outbound -> {
                        outbound.getMessageProperties().setMessageId(message.getMessageId());
                        return outbound;
                    });
        } catch (Exception sendFailure) {
            log.warn("MQ发送异常，尝试同步索引: fileId={}", message.getFileId(), sendFailure);
            try {
                aiService.buildIndex(message.getRepoId(), message.getFileId(),
                        message.getFilePath(), message.getContent());
            } catch (Exception indexFailure) {
                // Committed uploads stay successful; reconciliation is still an operational task.
                log.error("文件已提交但索引构建失败: fileId={}", message.getFileId(), indexFailure);
            }
        }
    }
}
