package com.ast.platform.scheduler.domain.repository;

import com.ast.platform.scheduler.domain.model.DispatchRecord;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DispatchRecordRepository {

    DispatchRecord save(DispatchRecord record);

    Optional<DispatchRecord> findByTaskId(String taskId);

    List<DispatchRecord> findRetryDueRecords(Instant now, int limit);

    List<DispatchRecord> findAll();
}