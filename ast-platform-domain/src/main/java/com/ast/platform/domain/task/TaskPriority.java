package com.ast.platform.domain.task;

public enum TaskPriority {
    LOW(1),
    DEFAULT(5),
    HIGH(10),
    CRITICAL(20);
    
    private final int value;
    
    TaskPriority(int value) {
        this.value = value;
    }
    
    public int getValue() {
        return value;
    }
    
    public static TaskPriority fromValue(int value) {
        for (TaskPriority priority : values()) {
            if (priority.value == value) {
                return priority;
            }
        }
        return DEFAULT;
    }
    
    public static TaskPriority fromValueOrDefault(Integer value) {
        if (value == null) {
            return DEFAULT;
        }
        return fromValue(value);
    }
}