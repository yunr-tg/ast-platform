package com.ast.platform.workersdk.config;

import com.ast.platform.workersdk.client.DefaultWorkerControlPlaneClient;
import com.ast.platform.workersdk.client.WorkerControlPlaneClient;
import com.ast.platform.workersdk.controller.WorkerDispatchController;
import com.ast.platform.workersdk.handler.TaskExecutionHandler;
import com.ast.platform.workersdk.lifecycle.WorkerLifecycleManager;
import com.ast.platform.workersdk.runtime.WorkerRuntimeManager;
import io.micrometer.tracing.Tracer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(WorkerSdkProperties.class)
public class TaskWorkerSdkAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WorkerControlPlaneClient workerControlPlaneClient(RestClient.Builder builder,
                                                             WorkerSdkProperties properties) {
        return new DefaultWorkerControlPlaneClient(builder, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkerRuntimeManager workerRuntimeManager(WorkerSdkProperties properties) {
        return new WorkerRuntimeManager(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkerDispatchController workerDispatchController(List<TaskExecutionHandler> handlers,
                                                             WorkerControlPlaneClient controlPlaneClient,
                                                             WorkerSdkProperties properties,
                                                             WorkerRuntimeManager runtimeManager,
                                                             Tracer tracer) {
        return new WorkerDispatchController(handlers, controlPlaneClient, properties, runtimeManager, tracer);
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkerLifecycleManager workerLifecycleManager(WorkerSdkProperties properties,
                                                         WorkerControlPlaneClient client,
                                                         WorkerRuntimeManager runtimeManager,
                                                         List<TaskExecutionHandler> handlers) {
        return new WorkerLifecycleManager(properties, client, runtimeManager, handlers);
    }
}
