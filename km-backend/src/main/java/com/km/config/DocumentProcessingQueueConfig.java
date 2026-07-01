package com.km.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(DocumentProcessingQueueProperties.class)
@ConditionalOnProperty(name = "km.document-processing.queue.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentProcessingQueueConfig {

    @Bean
    public DirectExchange documentProcessingExchange(DocumentProcessingQueueProperties properties) {
        return new DirectExchange(properties.getExchange(), true, false);
    }

    @Bean
    public DirectExchange documentProcessingDeadLetterExchange(DocumentProcessingQueueProperties properties) {
        return new DirectExchange(properties.getDeadLetterExchange(), true, false);
    }

    @Bean
    public Queue documentProcessingQueue(DocumentProcessingQueueProperties properties) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", properties.getDeadLetterExchange());
        arguments.put("x-dead-letter-routing-key", properties.getDeadLetterRoutingKey());
        return new Queue(properties.getQueue(), true, false, false, arguments);
    }

    @Bean
    public Queue documentProcessingDeadLetterQueue(DocumentProcessingQueueProperties properties) {
        return new Queue(properties.getDeadLetterQueue(), true);
    }

    @Bean
    public Binding documentProcessingBinding(
            @Qualifier("documentProcessingQueue") Queue documentProcessingQueue,
            @Qualifier("documentProcessingExchange") DirectExchange documentProcessingExchange,
            DocumentProcessingQueueProperties properties) {
        return BindingBuilder.bind(documentProcessingQueue)
                .to(documentProcessingExchange)
                .with(properties.getRoutingKey());
    }

    @Bean
    public Binding documentProcessingDeadLetterBinding(
            @Qualifier("documentProcessingDeadLetterQueue") Queue documentProcessingDeadLetterQueue,
            @Qualifier("documentProcessingDeadLetterExchange") DirectExchange documentProcessingDeadLetterExchange,
            DocumentProcessingQueueProperties properties) {
        return BindingBuilder.bind(documentProcessingDeadLetterQueue)
                .to(documentProcessingDeadLetterExchange)
                .with(properties.getDeadLetterRoutingKey());
    }

    @Bean
    public Jackson2JsonMessageConverter rabbitJsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter rabbitJsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(rabbitJsonMessageConverter);
        return rabbitTemplate;
    }
}
