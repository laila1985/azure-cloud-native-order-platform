package com.example.orderplatform.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Enables Spring's cache abstraction backed by Redis (Azure Cache for Redis).
 * <p>
 * This implements the cache-aside pattern for reads:
 * <pre>
 *   GET /orders/{id}  →  Redis  →  cache hit?  →  return
 *                                     │ no
 *                                     ▼
 *                                   SQL DB  →  store in Redis  →  return
 * </pre>
 * <p>
 * Values are serialized to JSON via a Jackson mapper that knows about
 * {@code java.time} types (the order's {@code createdAt} is an {@code Instant}).
 * A time-to-live bounds how long a stale entry may survive.
 * <p>
 * Default typing is restricted to the application model and {@code java.util}
 * collections so that Hibernate's internal {@code PersistentBag} (the lazy
 * {@code items} collection) is serialized as a plain list rather than a class
 * reference that cannot be rehydrated outside a session.
 * <p>
 * Disable with {@code app.cache.redis.enabled=false} (used by tests that run
 * without a Redis instance).
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(name = "app.cache.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisCacheConfig {

    @Value("${spring.cache.redis.time-to-live:PT5M}")
    private Duration timeToLive;

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Only type application model + java.util collections. Excludes Hibernate
        // internals (e.g. PersistentBag) so lazy collections round-trip as lists.
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.example.orderplatform.model.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.math.")
                .build();
        mapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);

        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(mapper);

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .entryTtl(timeToLive);

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
