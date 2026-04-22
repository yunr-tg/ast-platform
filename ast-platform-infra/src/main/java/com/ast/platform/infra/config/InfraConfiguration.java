package com.ast.platform.infra.config;

import com.ast.platform.domain.task.TaskProgressRepository;
import com.ast.platform.infra.redis.RedisTaskProgressRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class InfraConfiguration {

    @Bean
    public TaskProgressRepository taskProgressRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        return new RedisTaskProgressRepository(redisTemplate, objectMapper);
    }
}
