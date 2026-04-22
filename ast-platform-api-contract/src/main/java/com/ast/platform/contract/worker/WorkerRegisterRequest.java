package com.ast.platform.contract.worker;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record WorkerRegisterRequest(
        @NotBlank String workerId,
        @NotBlank String workerGroup,
        @NotBlank String host,
        @Positive int port,
        @NotBlank String protocol,
        @NotBlank String version,
        @NotEmpty List<String> supportedTaskTypes,
        List<String> tags,
        @Positive int maxConcurrency,
        Integer weight
) {
}
