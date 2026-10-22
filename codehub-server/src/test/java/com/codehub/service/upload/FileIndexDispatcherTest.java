package com.codehub.service.upload;

import com.codehub.dto.FileIndexMessage;
import com.codehub.entity.FileEntity;
import com.codehub.service.AiService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FileIndexDispatcherTest {
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final AiService ai = mock(AiService.class);
    private final FileIndexDispatcher dispatcher = new FileIndexDispatcher(rabbit, ai);
    private final TransactionTemplate tx = new TransactionTemplate(
            new ChunkUploadServiceTest.TestTransactionManager(new ArrayList<>()));

    @Test
    void normalUploadIndexWaitsForCommit() {
        tx.execute(status -> {
            schedule("hello".getBytes());
            verifyNoInteractions(rabbit);
            return null;
        });
        verify(rabbit).convertAndSend(anyString(), anyString(),
                argThat((FileIndexMessage m) -> m.getFileId() == 100L && m.getContent().equals("hello")),
                any(MessagePostProcessor.class));
    }

    @Test
    void rolledBackMetadataCannotPublishIndex() {
        assertThrows(IllegalStateException.class, () -> tx.execute(status -> {
            schedule("hello".getBytes());
            throw new IllegalStateException("metadata failed");
        }));
        verifyNoInteractions(rabbit, ai);
    }

    @Test
    void brokerSendExceptionFallsBackAndIndexFailureDoesNotUndoCommittedUpload() {
        doThrow(new IllegalStateException("broker down")).when(rabbit).convertAndSend(
                anyString(), anyString(), any(FileIndexMessage.class), any(MessagePostProcessor.class));
        doThrow(new IllegalStateException("index unavailable")).when(ai).buildIndex(anyLong(), anyLong(), anyString(), anyString());
        assertDoesNotThrow(() -> tx.execute(status -> { schedule("hello".getBytes()); return null; }));
        verify(ai).buildIndex(10L, 100L, "Test.java", "hello");
    }

    @Test
    void binaryOversizedAndInvalidUtf8AreNotIndexed() {
        FileEntity file = file(); file.setPath("image.png");
        assertFalse(dispatcher.accepts(file));
        file.setPath("Test.java"); file.setSizeBytes((long) FileIndexDispatcher.MAX_INDEX_BYTES + 1);
        assertFalse(dispatcher.accepts(file));
        tx.execute(status -> {
            schedule(new byte[]{(byte) 0xff, (byte) 0xfe});
            schedule(new byte[]{0, 1});
            return null;
        });
        verifyNoInteractions(rabbit, ai);
    }

    private void schedule(byte[] content) {
        try { dispatcher.schedule(file(), new ByteArrayInputStream(content)); }
        catch (java.io.IOException e) { throw new AssertionError(e); }
    }
    private FileEntity file() {
        FileEntity file = new FileEntity(); file.setRepoId(10L); file.setId(100L);
        file.setPath("Test.java"); file.setSizeBytes(5L);
        return file;
    }
}
