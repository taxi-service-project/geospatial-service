package com.example.geospatial_service.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;

@Configuration
public class RedisConfig {

    // 1. Cache Redis (영속성 x)
    @Bean
    @Primary
    public ReactiveRedisConnectionFactory cacheConnectionFactory(
            @Value("${spring.redis.cache.host}") String host,
            @Value("${spring.redis.cache.port}") int port) {
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
    }

    @Bean(name = "cacheRedisTemplate")
    @Primary
    public ReactiveRedisTemplate<String, String> cacheRedisTemplate(
            @Qualifier("cacheConnectionFactory") ReactiveRedisConnectionFactory factory) {
        return new ReactiveRedisTemplate<>(factory, RedisSerializationContext.string());
    }

    // 2. Storage Redis (영속성 o)
    @Bean
    public ReactiveRedisConnectionFactory storageConnectionFactory(
            @Value("${spring.redis.storage.host}") String host,
            @Value("${spring.redis.storage.port}") int port) {
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
    }

    @Bean(name = "storageRedisTemplate")
    public ReactiveRedisTemplate<String, String> storageRedisTemplate(
            @Qualifier("storageConnectionFactory") ReactiveRedisConnectionFactory factory) {
        return new ReactiveRedisTemplate<>(factory, RedisSerializationContext.string());
    }
}