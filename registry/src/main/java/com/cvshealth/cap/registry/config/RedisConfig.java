// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for L2 cache support.
 * <p>
 * This configuration is only enabled when both conditions are met:
 * <ul>
 *   <li>{@code app.cache.redis.enabled=true}</li>
 *   <li>{@code RedisConnectionFactory} is available (requires {@code spring.data.redis.host})</li>
 * </ul>
 * <p>
 * Note: Redis repository scanning is disabled via application.yaml
 * ({@code spring.data.redis.repositories.enabled=false}) since we only use
 * Redis for caching, not as a data repository.
 */
@Configuration
@ConditionalOnProperty(name = "app.cache.redis.enabled", havingValue = "true")
@AutoConfigureAfter(DataRedisAutoConfiguration.class)
@EnableConfigurationProperties(DataRedisProperties.class)
@Slf4j
public class RedisConfig {

    /**
     * Creates a LettuceConnectionFactory using Spring Boot's Redis properties.
     * This ensures the connection factory uses the configured host, port, username, and password
     * from application.yaml or application-{profile}.yml.
     *
     * @param redisProperties the Redis properties auto-configured by Spring Boot
     * @return configured LettuceConnectionFactory
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory(DataRedisProperties redisProperties) {
        log.info("Creating LettuceConnectionFactory with host={}, port={}, username={}",
                redisProperties.getHost(), redisProperties.getPort(), redisProperties.getUsername());

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisProperties.getHost());
        config.setPort(redisProperties.getPort());

        if (redisProperties.getUsername() != null) {
            config.setUsername(redisProperties.getUsername());
        }
        if (redisProperties.getPassword() != null) {
            config.setPassword(redisProperties.getPassword());
        }

        return new LettuceConnectionFactory(config);
    }

    /**
     * Creates a RedisTemplate configured for caching with JSON serialization.
     * <p>
     * Uses String serialization for keys and JSON serialization for values,
     * which allows for human-readable cache entries and cross-language compatibility.
     * <p>
     * This bean is only created when:
     * <ul>
     *   <li>{@code app.cache.redis.enabled=true}</li>
     *   <li>{@code RedisConnectionFactory} is available</li>
     * </ul>
     *
     * @param connectionFactory the Redis connection factory (auto-configured by Spring Boot)
     * @return configured RedisTemplate for cache operations
     */
    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisTemplate<String, Object> cacheRedisTemplate(RedisConnectionFactory connectionFactory) {
        log.info("Creating cacheRedisTemplate - Redis L2 cache is enabled");
        
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use String serializer for keys
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Use JSON serializer for values with type information
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        objectMapper.findAndRegisterModules();

        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
