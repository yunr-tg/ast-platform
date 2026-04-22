package com.ast.platform.scheduler.domain.repository;

import com.ast.platform.scheduler.domain.model.NotifyOutboxRecord;

import java.time.Instant;
import java.util.List;

public interface NotifyOutboxRepository {

    NotifyOutboxRecord save(NotifyOutboxRecord record);

    List<NotifyOutboxRecord> findDueRecords(String status, Instant now, int limit);
}