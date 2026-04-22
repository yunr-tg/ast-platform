package com.ast.platform.scheduler.infrastructure.repository;

import com.ast.platform.scheduler.domain.model.CompensationAudit;
import com.ast.platform.scheduler.domain.repository.CompensationAuditRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

@Repository
public class JdbcCompensationAuditRepository implements CompensationAuditRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcCompensationAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(CompensationAudit audit) {
        jdbcTemplate.update("""
                insert into scheduler_compensation_audit(audit_id, task_id, action, detail, created_at)
                values (?, ?, ?, ?, ?)
                """,
                audit.auditId(),
                audit.taskId(),
                audit.action(),
                audit.detail(),
                Timestamp.from(audit.createdAt()));
    }
}
