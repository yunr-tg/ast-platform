package com.ast.platform.gateway.config;

import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.SessionCredentialsProvider;
import org.apache.rocketmq.client.apis.StaticSessionCredentialsProvider;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnProperty(prefix = "ast.gateway.rocketmq", name = "enabled", havingValue = "true")
public class GatewayRocketMqConfiguration {

    private Producer producer;

    @Bean
    public ClientServiceProvider clientServiceProvider() {
        return ClientServiceProvider.loadService();
    }

    @Bean
    public ClientConfiguration rocketMqClientConfiguration(GatewayRocketMqProperties properties) {
        ClientConfigurationBuilderAdapter builder = new ClientConfigurationBuilderAdapter(
                ClientConfiguration.newBuilder()
                        .setEndpoints(properties.getEndpoints())
                        .enableSsl(properties.isSslEnabled())
        );
        if (StringUtils.hasText(properties.getAccessKey()) && StringUtils.hasText(properties.getSecretKey())) {
            SessionCredentialsProvider provider =
                    new StaticSessionCredentialsProvider(properties.getAccessKey(), properties.getSecretKey());
            builder.setCredentialProvider(provider);
        }
        return builder.build();
    }

    @Bean
    public Producer rocketMqProducer(ClientServiceProvider clientServiceProvider,
                                     ClientConfiguration rocketMqClientConfiguration,
                                     GatewayProperties gatewayProperties) throws ClientException {
        this.producer = clientServiceProvider.newProducerBuilder()
                .setClientConfiguration(rocketMqClientConfiguration)
                .setTopics(gatewayProperties.getSubmitTopic())
                .build();
        return producer;
    }

    @PreDestroy
    public void destroy() throws Exception {
        if (producer != null) {
            producer.close();
        }
    }

    private static final class ClientConfigurationBuilderAdapter {
        private final org.apache.rocketmq.client.apis.ClientConfigurationBuilder builder;

        private ClientConfigurationBuilderAdapter(org.apache.rocketmq.client.apis.ClientConfigurationBuilder builder) {
            this.builder = builder;
        }

        private void setCredentialProvider(SessionCredentialsProvider provider) {
            builder.setCredentialProvider(provider);
        }

        private ClientConfiguration build() {
            return builder.build();
        }
    }
}
