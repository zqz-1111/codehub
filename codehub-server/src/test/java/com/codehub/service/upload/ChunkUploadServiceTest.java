package com.codehub.service.upload;

import com.codehub.common.BusinessException;
import com.codehub.dto.FileIndexMessage;
import com.codehub.dto.FileVO;
import com.codehub.entity.*;
import com.codehub.mapper.*;
import com.codehub.service.AiService;
import com.codehub.service.MinioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real service and Spring transaction callbacks; external stores and database commands are mocked. */
class ChunkUploadServiceTest {
    private final UploadSessionMapper sessions = mock(UploadSessionMapper.class);
    private final UploadPartMapper parts = mock(UploadPartMapper.class);
    private final FileMapper files = mock(FileMapper.class);
    private final CommitMapper commits = mock(CommitMapper.class);
    private final MinioService minio = mock(MinioService.class);
    private final RedissonClient redisson = mock(RedissonClient.class);
    private final RLock lock = mock(RLock.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final AiService ai = mock(AiService.class);
    private final List<String> events = new ArrayList<>();
    private final FileIndexDispatcher index = new FileIndexDispatcher(rabbit, ai);
    private ChunkUploadService service;
    private UploadSession session;

    @BeforeEach
    void setUp() throws Exception {
        when(redisson.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(0, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        doAnswer(call -> { events.add("unlock"); return null; }).when(lock).unlock();
        when(parts.selectCount(any())).thenReturn(0L);
        session = new UploadSession();
        session.setUploadId("session-1");
        session.setRepoId(10L);
        session.setUserId(20L);
        session.setTotalChunks(2);
        session.setStatus("UPLOADING");
        when(sessions.selectForUpdate("session-1")).thenReturn(session);
        when(files.insert(any(FileEntity.class))).thenAnswer(call -> {
            ((FileEntity) call.getArgument(0)).setId(100L);
            events.add("metadata");
            return 1;
        });
        when(commits.insert(any(Commit.class))).thenAnswer(call -> {
            ((Commit) call.getArgument(0)).setId(200L);
            return 1;
        });
        doAnswer(call -> {
            assertTrue(events.contains("commit"), "Must publish after commit");
            events.add("index");
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), any(FileIndexMessage.class), any(MessagePostProcessor.class));
        service = new ChunkUploadService(sessions, parts, files, commits, minio, redisson, redis, index,
                new TestTransactionManager(events));
    }

    @Test
    void mergesInOrderCountsRealBytesAndRunsSideEffectsOnlyAfterCommitBeforeUnlock() throws Exception {
        when(parts.selectList(any())).thenReturn(List.of(part(1, "p1", 2), part(0, "p0", 3)));
        when(minio.download("p0")).thenReturn(new ByteArrayInputStream("abc".getBytes()));
        when(minio.download("p1")).thenReturn(new ByteArrayInputStream("de".getBytes()));
        AtomicReference<byte[]> merged = new AtomicReference<>();
        doAnswer(call -> { merged.set(((InputStream) call.getArgument(1)).readAllBytes()); return null; })
                .when(minio).upload(startsWith("repos/"), any(), anyString(), eq(5L));
        doAnswer(call -> {
            assertTrue(events.contains("commit"));
            events.add("cleanup"); return null;
        }).when(minio).delete(startsWith("p"));
        FileVO result = service.merge(10L, 20L, "session-1", "Test.java", "src/Test.java");
        assertArrayEquals("abcde".getBytes(), merged.get());
        assertEquals(5L, result.getSizeBytes());
        assertEquals(100L, result.getId());
        assertEquals(200L, result.getCommitId());
        assertEquals("MERGED", session.getStatus());
        assertEquals(100L, session.getMergedFileId());
        assertTrue(events.indexOf("index") > events.indexOf("commit"));
        assertEquals("unlock", events.get(events.size() - 1));
        verify(redis).delete("repo:file_tree:10");
    }

    @Test
    void rejectsMissingOrDuplicateIndicesBeforeStorageWrites() {
        when(parts.selectList(any())).thenReturn(List.of(part(0, "p0", 3)));
        assertThrows(BusinessException.class, () -> merge());
        when(parts.selectList(any())).thenReturn(List.of(part(0, "p0", 3), part(0, "other", 3)));
        assertThrows(BusinessException.class, () -> merge());
        verifyNoInteractions(minio, files, commits, rabbit);
    }

    @Test
    void sessionCannotBeUsedByAnotherUserOrRepository() {
        assertEquals(403, assertThrows(BusinessException.class,
                () -> service.merge(10L, 99L, "session-1", "a.txt", "a.txt")).getCode());
        assertEquals(403, assertThrows(BusinessException.class,
                () -> service.merge(99L, 20L, "session-1", "a.txt", "a.txt")).getCode());
        verifyNoInteractions(minio, files, parts);
    }

    @Test
    void repeatedMergeReturnsPersistedReceiptAndRejectsChangedTarget() {
        session.setStatus("MERGED"); session.setMergedFileId(100L);
        session.setMergedPath("a.txt"); session.setMergedFileName("a.txt");
        FileEntity file = new FileEntity(); file.setId(100L); file.setSizeBytes(5L);
        when(files.selectById(100L)).thenReturn(file);
        assertEquals(100L, merge().getId());
        assertThrows(BusinessException.class, () -> service.merge(10L, 20L, "session-1", "b.txt", "b.txt"));
        verifyNoInteractions(minio, parts, rabbit, commits);
    }

    @Test
    void metadataFailurePreservesPartsAndCompensatesNewFinalObject() throws Exception {
        readySinglePart();
        when(files.insert(any(FileEntity.class))).thenThrow(new IllegalStateException("DB failed"));
        assertThrows(IllegalStateException.class, () -> merge());
        assertTrue(events.contains("rollback"));
        verify(minio).delete(startsWith("repos/"));
        verify(minio, never()).delete("p0");
        verify(parts, never()).delete(any());
        verifyNoInteractions(rabbit);
    }

    @Test
    void missingObjectOrWrongByteLengthCannotCreatePartialFinalFile() throws Exception {
        readySinglePart();
        when(minio.download("p0")).thenThrow(new IllegalStateException("object missing"));
        assertThrows(IllegalStateException.class, () -> merge());
        doReturn(new ByteArrayInputStream(new byte[]{1, 2})).when(minio).download("p0");
        assertThrows(BusinessException.class, () -> merge());
        doReturn(new ByteArrayInputStream(new byte[]{1, 2, 3, 4})).when(minio).download("p0");
        assertThrows(BusinessException.class, () -> merge());
        verify(minio, never()).upload(anyString(), any(), anyString(), anyLong());
        verify(parts, never()).delete(any());
        verifyNoInteractions(files, rabbit);
    }

    @Test
    void retryChunkReplacesRecordAndCleansOldObjectAfterCommit() {
        UploadPart old = part(0, "old-key", 3); old.setId(5L);
        when(parts.selectOne(any())).thenReturn(old);
        service.upload(10L, 20L, "session-1", 0, 2, chunk());
        verify(parts).updateById(argThat((UploadPart p) -> p.getId() == 5L && p.getSizeBytes() == 3L
                && p.getObjectKey().startsWith("chunks/10/20/session-1/0-")));
        verify(parts, never()).insert(any(UploadPart.class));
        verify(minio).delete("old-key");
        assertTrue(events.contains("commit"));
    }

    @Test
    void failedChunkRecordSaveDeletesNewObjectButKeepsPreviousObject() {
        UploadPart old = part(0, "old-key", 3); old.setId(5L);
        when(parts.selectOne(any())).thenReturn(old);
        when(parts.updateById(any(UploadPart.class))).thenThrow(new IllegalStateException("DB failed"));
        assertThrows(IllegalStateException.class, () -> service.upload(10L, 20L, "session-1", 0, 2, chunk()));
        verify(minio).delete(startsWith("chunks/10/20/session-1/0-"));
        verify(minio, never()).delete("old-key");
    }

    @Test
    void uploadValidatesManifestAndRejectsLegacyOrCompletedSessions() {
        assertThrows(BusinessException.class, () -> service.upload(10L, 20L, "session-1", 0, 3, chunk()));
        session.setStatus("MERGED");
        assertThrows(BusinessException.class, () -> service.upload(10L, 20L, "session-1", 0, 2, chunk()));
        when(sessions.selectForUpdate("session-1")).thenReturn(null);
        when(parts.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.upload(10L, 20L, "session-1", 0, 2, chunk()));
        verifyNoInteractions(minio);
    }

    @Test
    void firstChunkCreatesOwnedManifestBeforeSavingPart() {
        when(sessions.selectForUpdate("new-session")).thenReturn(null);
        service.upload(10L, 20L, "new-session", 0, 2, chunk());
        verify(sessions).insert(argThat((UploadSession s) -> s.getUploadId().equals("new-session")
                && s.getRepoId() == 10L && s.getUserId() == 20L && s.getTotalChunks() == 2));
        verify(parts).insert(argThat((UploadPart p) -> p.getSizeBytes() == 3L && p.getChunkIndex() == 0));
    }

    @Test
    void rejectsInvalidInputAndLockContention() throws Exception {
        assertThrows(BusinessException.class, () -> service.upload(10L, 20L, "../bad", 0, 1, chunk()));
        assertThrows(BusinessException.class, () -> service.upload(10L, 20L, "ok", 1, 1, chunk()));
        assertThrows(BusinessException.class, () -> service.upload(10L, 20L, "ok", 0, 0, chunk()));
        assertThrows(BusinessException.class, () -> service.merge(10L, 20L, "ok", "../a.txt", "a.txt"));
        when(lock.tryLock(0, TimeUnit.SECONDS)).thenReturn(false);
        assertEquals(409, assertThrows(BusinessException.class, () -> merge()).getCode());
        verifyNoInteractions(sessions, minio);
        verify(lock, never()).unlock();
    }

    private void readySinglePart() throws Exception {
        session.setTotalChunks(1);
        when(parts.selectList(any())).thenReturn(List.of(part(0, "p0", 3)));
        when(minio.download("p0")).thenReturn(new ByteArrayInputStream("abc".getBytes()));
    }
    private FileVO merge() { return service.merge(10L, 20L, "session-1", "a.txt", "a.txt"); }
    private MockMultipartFile chunk() { return new MockMultipartFile("file", "a.txt", "text/plain", "abc".getBytes()); }
    private UploadPart part(int index, String key, long size) {
        UploadPart p = new UploadPart(); p.setUploadId("session-1"); p.setRepoId(10L);
        p.setChunkIndex(index); p.setObjectKey(key); p.setSizeBytes(size); p.setStatus("UPLOADING");
        return p;
    }

    static class TestTransactionManager extends AbstractPlatformTransactionManager {
        private final List<String> events;
        TestTransactionManager(List<String> events) { this.events = events; }
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { events.add("begin"); }
        @Override protected void doCommit(DefaultTransactionStatus status) { events.add("commit"); }
        @Override protected void doRollback(DefaultTransactionStatus status) { events.add("rollback"); }
    }
}
