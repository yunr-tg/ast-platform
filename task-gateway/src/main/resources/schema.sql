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
    progress int not null default 0,
    version int not null,
    created_at timestamp(3) not null,
    updated_at timestamp(3) not null,
    constraint uk_gateway_task_request unique (tenant_id, request_id),
    constraint uk_gateway_task_biz unique (tenant_id, task_type, biz_key)
);

create table if not exists gateway_publish_outbox (
    outbox_id varchar(64) primary key,
    task_id varchar(64) not null,
    tenant_id varchar(64) not null,
    task_type varchar(128) not null,
    topic varchar(255) not null,
    payload text not null,
    status varchar(32) not null,
    retry_count int not null,
    last_error_message varchar(1024),
    next_retry_time timestamp(3),
    last_published_at timestamp(3),
    created_at timestamp(3) not null,
    updated_at timestamp(3) not null,
    constraint uk_gateway_publish_outbox_task unique (task_id)
);

create index if not exists idx_gateway_publish_outbox_status_created
    on gateway_publish_outbox (status, created_at);
