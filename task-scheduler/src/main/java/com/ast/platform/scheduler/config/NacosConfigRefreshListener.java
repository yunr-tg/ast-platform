package com.ast.platform.scheduler.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class NacosConfigRefreshListener {

    private static final Logger log = LoggerFactory.getLogger(NacosConfigRefreshListener.class);
    private final ContextRefresher contextRefresher;

    public NacosConfigRefreshListener(ContextRefresher contextRefresher) {
        this.contextRefresher = contextRefresher;
    }

    @EventListener
    public void onApplicationEvent(org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent event) {
        log.info("Nacos配置已刷新，重新加载@RefreshScope注解的Bean");
    }

    @EventListener
    public void onApplicationEvent(org.springframework.cloud.endpoint.event.RefreshEvent event) {
        log.info("收到配置刷新事件，来源: {}", event.getEventDesc());
        try {
            contextRefresher.refresh();
            log.info("配置刷新成功");
        } catch (Exception e) {
            log.error("配置刷新失败", e);
        }
    }
}