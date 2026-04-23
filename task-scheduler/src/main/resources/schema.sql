create table if not exists gateway_task (
    task_id varchar(64) primary key,
    tenant_id varchar(64) not null,
    task_type varchar(128) not null,
    biz_key varchar(128) not null,
    request_id varchar(128) not null,
    worker_group varchar(128) not null,
    tag varchar(128),
    payload text,
    callback_url varchar(512),
    trace_id varchar(64) not null,
    status varchar(32) not null,
    priority int not null default 5,
    version int not null,
    created_at timestamp(3) not null,
    updated_at timestamp(3) not null,
    constraint uk_gateway_task_request unique (tenant_id, request_id),
    constraint uk_gateway_task_biz unique (tenant_id, task_type, biz_key)
);

create table if not exists scheduler_worker (
    worker_id varchar(64) primary key,
    worker_group varchar(128) not null,
    host varchar(255),
    port int,
    protocol varchar(32),
    worker_version varchar(64),
    supported_task_types varchar(1024) not null,
    tags varchar(1024),
    status varchar(32) not null,
    active_task_count int not null,
    max_concurrency int not null,
    available_slots int not null,
    avg_rt bigint not null,
    error_rate double not null,
    last_register_at timestamp(3),
    last_heartbeat_at timestamp(3),
    weight int not null default 100,
    cpu_usage double not null default 0,
    memory_usage double not null default 0,
    updated_at timestamp(3) not null
);

create table if not exists scheduler_dispatch (
    task_id varchar(64) primary key,
    tenant_id varchar(64) not null,
    task_type varchar(128) not null,
    worker_group varchar(128) not null,
    worker_id varchar(64),
    dispatch_token varchar(64),
    dispatch_status varchar(32) not null,
    trace_id varchar(64) not null,
    retry_count int not null,
    next_retry_time timestamp(3),
    last_error_message varchar(1024),
    last_result_payload text,
    last_dispatched_at timestamp(3),
    updated_at timestamp(3) not null,
    created_at timestamp(3) not null
);

create table if not exists scheduler_notify_outbox (
    outbox_id varchar(64) primary key,
    task_id varchar(64) not null,
    callback_url varchar(512),
    payload text not null,
    status varchar(32) not null,
    trace_id varchar(64) not null,
    retry_count int not null,
    next_retry_time timestamp(3),
    last_error_message varchar(1024),
    created_at timestamp(3) not null,
    updated_at timestamp(3) not null
);

create table if not exists scheduler_compensation_audit (
    audit_id varchar(64) primary key,
    task_id varchar(64),
    action varchar(64) not null,
    detail varchar(2048),
    created_at timestamp(3) not null
);

create index if not exists idx_scheduler_worker_group_status
    on scheduler_worker (worker_group, status);

create index if not exists idx_scheduler_dispatch_status_retry
    on scheduler_dispatch (dispatch_status, next_retry_time);

create index if not exists idx_scheduler_notify_status_retry
    on scheduler_notify_outbox (status, next_retry_time);
