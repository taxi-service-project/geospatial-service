package com.example.geospatial_service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@Slf4j
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        factory.getContainerProperties().setObservationEnabled(true);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (record, exception) -> log.error("🚨 [Config 전송 실패] 기사({}) 설정값 전달 포기. Payload: {}", record.key(), record.value()),
                new FixedBackOff(1000L, 3)
        );

        errorHandler.setAckAfterHandle(true);

        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
