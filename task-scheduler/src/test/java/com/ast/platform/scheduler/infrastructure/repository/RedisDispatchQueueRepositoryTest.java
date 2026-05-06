package com.ast.platform.scheduler.infrastructure.repository;

import com.ast.platform.scheduler.domain.model.ReadyTaskEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisDispatchQueueRepositoryTest {

    private StringRedisTemplate redisTemplate;
    private ListOperations<String, String> listOps;
    private SetOperations<String, String> setOps;
    private ZSetOperations<String, String> zSetOps;
    private RedisDispatchQueueRepository repository;
    private ObjectMapper objectMapper;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        listOps = mock(ListOperations.class);
        setOps = mock(SetOperations.class);
        zSetOps = mock(ZSetOperations.class);
        
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        repository = new RedisDispatchQueueRepository(redisTemplate, objectMapper);
    }

    @Test
    void testEnqueueReady() throws Exception {
        ReadyTaskEnvelope envelope = new ReadyTaskEnvelope("task-1", "tenant-1", "type-1", "group-1", "trace-1", 5, Instant.now());
        
        when(setOps.add(anyString(), anyString())).thenReturn(1L);

        repository.enqueueReady(envelope);
        
        verify(listOps).rightPush(eq("dispatch:ready:tenant-1:type-1"), anyString());
        verify(setOps).add(eq("dispatch:active:keys:set"), eq("tenant-1:type-1"));
        verify(listOps).leftPush(eq("dispatch:active:keys"), eq("tenant-1:type-1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testPollNextReadyTask_Success() throws Exception {
        ReadyTaskEnvelope envelope = new ReadyTaskEnvelope("task-1", "tenant-1", "type-1", "group-1", "trace-1", 5, Instant.now());
        String json = objectMapper.writeValueAsString(envelope);
        
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of("tenant-1:type-1", json));
        
        Optional<ReadyTaskEnvelope> result = repository.pollNextReadyTask();
        
        assertTrue(result.isPresent(), "Result should be present");
        assertEquals("task-1", result.get().taskId());
        assertEquals("trace-1", result.get().traceId());
    }

    @Test
    void testReclaimTimeoutTasks() throws Exception {
        ReadyTaskEnvelope envelope = new ReadyTaskEnvelope("task-1", "tenant-1", "type-1", "group-1", "trace-1", 5, Instant.now());
        String json = objectMapper.writeValueAsString(envelope);
        Instant now = Instant.now();
        
        when(setOps.members("dispatch:processing:active:keys")).thenReturn(Set.of("tenant-1:type-1"));
        when(zSetOps.rangeByScore(eq("dispatch:processing:tenant-1:type-1"), eq(0.0), anyDouble())).thenReturn(Set.of(json));
        when(setOps.add(anyString(), anyString())).thenReturn(1L);

        int reclaimed = repository.reclaimTimeoutTasks(now);
        
        assertEquals(1, reclaimed);
        verify(zSetOps).remove("dispatch:processing:tenant-1:type-1", json);
        verify(listOps).leftPush(eq("dispatch:ready:tenant-1:type-1"), anyString());
    }

    @Test
    void testCommitTask() throws Exception {
        ReadyTaskEnvelope envelope = new ReadyTaskEnvelope("task-1", "tenant-1", "type-1", "group-1", "trace-1", 5, Instant.now());
        repository.commitTask(envelope);
        verify(zSetOps).remove(eq("dispatch:processing:tenant-1:type-1"), anyString());
    }

    @Test
    void testRollbackTask() throws Exception {
        ReadyTaskEnvelope envelope = new ReadyTaskEnvelope("task-1", "tenant-1", "type-1", "group-1", "trace-1", 5, Instant.now());
        when(setOps.add(anyString(), anyString())).thenReturn(1L);

        repository.rollbackTask(envelope);
        
        verify(zSetOps).remove(eq("dispatch:processing:tenant-1:type-1"), anyString());
        verify(listOps).leftPush(eq("dispatch:ready:tenant-1:type-1"), anyString());
        verify(listOps).leftPush(eq("dispatch:active:keys"), eq("tenant-1:type-1"));
    }

    @Test
    void testEnqueueRetry() throws Exception {
        ReadyTaskEnvelope envelope = new ReadyTaskEnvelope("task-1", "tenant-1", "type-1", "group-1", "trace-1", 5, Instant.now());
        Instant dueTime = Instant.now().plusSeconds(60);
        
        repository.enqueueRetry(envelope, dueTime);
        
        verify(zSetOps).add(eq("dispatch:retry:tenant-1:type-1"), anyString(), eq((double) dueTime.toEpochMilli()));
        verify(setOps).add(eq("dispatch:retry:active:keys"), eq("tenant-1:type-1"));
    }

    @Test
    void testMoveDueRetryTasks() throws Exception {
        ReadyTaskEnvelope envelope = new ReadyTaskEnvelope("task-1", "tenant-1", "type-1", "group-1", "trace-1", 5, Instant.now());
        String json = objectMapper.writeValueAsString(envelope);
        Instant now = Instant.now();
        
        when(setOps.members("dispatch:retry:active:keys")).thenReturn(Set.of("tenant-1:type-1"));
        when(zSetOps.rangeByScore(eq("dispatch:retry:tenant-1:type-1"), eq(0.0), anyDouble())).thenReturn(Set.of(json));
        when(setOps.add(anyString(), anyString())).thenReturn(1L);

        int moved = repository.moveDueRetryTasks(now);
        
        assertEquals(1, moved);
        verify(zSetOps).remove("dispatch:retry:tenant-1:type-1", json);
        verify(listOps).rightPush(eq("dispatch:ready:tenant-1:type-1"), anyString());
    }
}
