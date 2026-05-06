package com.ast.platform.common.metrics;

public final class MetricNames {

    private MetricNames() {}

    public static final String TASK_SUBMIT_TOTAL = "ast.task.submit.total";
    public static final String TASK_SUBMIT_SUCCESS = "ast.task.submit.success";
    public static final String TASK_SUBMIT_FAILURE = "ast.task.submit.failure";
    public static final String TASK_SUBMIT_DURATION = "ast.task.submit.duration";

    public static final String TASK_QUEUE_SIZE = "ast.task.queue.size";
    public static final String TASK_QUEUE_ENQUEUE_TOTAL = "ast.task.queue.enqueue.total";
    public static final String TASK_QUEUE_DEQUEUE_TOTAL = "ast.task.queue.dequeue.total";

    public static final String TASK_DISPATCH_TOTAL = "ast.task.dispatch.total";
    public static final String TASK_DISPATCH_SUCCESS = "ast.task.dispatch.success";
    public static final String TASK_DISPATCH_FAILURE = "ast.task.dispatch.failure";
    public static final String TASK_DISPATCH_DURATION = "ast.task.dispatch.duration";
    public static final String TASK_DISPATCH_DELAY = "ast.task.dispatch.delay";

    public static final String TASK_CALLBACK_TOTAL = "ast.task.callback.total";
    public static final String TASK_CALLBACK_SUCCESS = "ast.task.callback.success";
    public static final String TASK_CALLBACK_FAILURE = "ast.task.callback.failure";

    public static final String TASK_RETRY_TOTAL = "ast.task.retry.total";
    public static final String TASK_DEAD_LETTER_TOTAL = "ast.task.dead_letter.total";

    public static final String TASK_PROGRESS_REPORT_TOTAL = "ast.task.progress.report.total";

    public static final String TASK_CANCEL_TOTAL = "ast.task.cancel.total";
    public static final String TASK_CANCEL_SUCCESS = "ast.task.cancel.success";

    public static final String OUTBOX_PUBLISH_TOTAL = "ast.outbox.publish.total";
    public static final String OUTBOX_PUBLISH_SUCCESS = "ast.outbox.publish.success";
    public static final String OUTBOX_PUBLISH_FAILURE = "ast.outbox.publish.failure";
    public static final String OUTBOX_REPUBLISH_TOTAL = "ast.outbox.republish.total";

    public static final String WORKER_REGISTER_TOTAL = "ast.worker.register.total";
    public static final String WORKER_HEARTBEAT_TOTAL = "ast.worker.heartbeat.total";
    public static final String WORKER_HEARTBEAT_SUCCESS = "ast.worker.heartbeat.success";
    public static final String WORKER_HEARTBEAT_FAILURE = "ast.worker.heartbeat.failure";
    public static final String WORKER_ACTIVE_COUNT = "ast.worker.active.count";
    public static final String WORKER_AVAILABLE_SLOTS = "ast.worker.available_slots";

    public static final String REDIS_QUEUE_SIZE = "ast.redis.queue.size";
    public static final String REDIS_QUEUE_READY_SIZE = "ast.redis.queue.ready.size";
    public static final String REDIS_QUEUE_PROCESSING_SIZE = "ast.redis.queue.processing.size";
    public static final String REDIS_QUEUE_RETRY_SIZE = "ast.redis.queue.retry.size";

    public static final String ROCKETMQ_PRODUCER_SEND_TOTAL = "ast.rocketmq.producer.send.total";
    public static final String ROCKETMQ_PRODUCER_SEND_SUCCESS = "ast.rocketmq.producer.send.success";
    public static final String ROCKETMQ_PRODUCER_SEND_FAILURE = "ast.rocketmq.producer.send.failure";
    public static final String ROCKETMQ_CONSUMER_LAG = "ast.rocketmq.consumer.lag";

    public static final String BUSINESS_NOTIFY_TOTAL = "ast.business.notify.total";
    public static final String BUSINESS_NOTIFY_SUCCESS = "ast.business.notify.success";
    public static final String BUSINESS_NOTIFY_FAILURE = "ast.business.notify.failure";

    public static final String COMPENSATOR_SCAN_TOTAL = "ast.compensator.scan.total";
    public static final String COMPENSATOR_RECOVER_TOTAL = "ast.compensator.recover.total";

    public static final String TAG_TENANT_ID = "tenant_id";
    public static final String TAG_TASK_TYPE = "task_type";
    public static final String TAG_WORKER_ID = "worker_id";
    public static final String TAG_WORKER_GROUP = "worker_group";
    public static final String TAG_STATUS = "status";
    public static final String TAG_ERROR_CODE = "error_code";
    public static final String TAG_QUEUE_TYPE = "queue_type";
    public static final String TAG_TOPIC = "topic";
}