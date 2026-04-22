package com.ast.platform.domain.task;

public enum SchedulingStrategy {
    LEAST_LOADED,
    RANDOM,
    ROUND_ROBIN,
    CONSISTENT_HASH,
    WEIGHTED_RANDOM,
    WEIGHTED_ROUND_ROBIN,
    RESOURCE_AWARE,
    FAIR_DRR,
    MAX_MIN_FAIRNESS
}
