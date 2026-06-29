package com.km.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String DOCUMENT_PROCESS_QUEUE = "km.document.process";

    @Bean
    public Queue documentProcessQueue() {
        return new Queue(DOCUMENT_PROCESS_QUEUE, true);
    }
}
