package com.ast.platform.scheduler.domain.repository;

import com.ast.platform.scheduler.domain.model.CompensationAudit;

public interface CompensationAuditRepository {
    void save(CompensationAudit audit);
}
