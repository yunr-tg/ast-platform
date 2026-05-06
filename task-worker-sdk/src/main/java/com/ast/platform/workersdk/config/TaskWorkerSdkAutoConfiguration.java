package com.ast.platform.workersdk.config;

import com.ast.platform.workersdk.client.HttpWorkerControlPlaneClient;
import com.ast.platform.workersdk.client.WorkerControlPlaneClient;
import com.ast.platform.workersdk.controller.WorkerDispatchController;
import com.ast.platform.workersdk.handler.TaskExecutionHandler;
import com.ast.platform.workersdk.lifecycle.WorkerGracefulShutdownHook;
import com.ast.platform.workersdk.lifecycle.WorkerLifecycleManager;
import com.ast.platform.workersdk.runtime.WorkerRuntimeManager;
import io.micrometer.tracing.Tracer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(WorkerSdkProperties.class)
public class TaskWorkerSdkAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkerControlPlaneClient workerControlPlaneClient(RestTemplate restTemplate,
                                                             WorkerSdkProperties properties) {
        return new HttpWorkerControlPlaneClient(restTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkerRuntimeManager workerRuntimeManager(WorkerSdkProperties properties) {
        return new WorkerRuntimeManager(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkerGracefulShutdownHook workerGracefulShutdownHook(
            WorkerSdkProperties properties,
            WorkerRuntimeManager runtimeManager,
            ApplicationContext applicationContext) {
        return new WorkerGracefulShutdownHook(properties, runtimeManager, applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkerDispatchController workerDispatchController(List<TaskExecutionHandler> handlers,
                                                             WorkerControlPlaneClient controlPlaneClient,
                                                             WorkerSdkProperties properties,
                                                             WorkerRuntimeManager runtimeManager,
                                                             WorkerGracefulShutdownHook shutdownHook,
                                                             Tracer tracer) {
        return new WorkerDispatchController(handlers, controlPlaneClient, properties, runtimeManager, shutdownHook, tracer);
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
