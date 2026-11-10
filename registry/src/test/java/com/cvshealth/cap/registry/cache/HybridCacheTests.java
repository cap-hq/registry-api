// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.cache;

import com.cvshealth.cap.registry.config.HybridCacheConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridCacheTests {

    private static final String CACHE_NAME = "test-cache";
    private static final String REDIS_KEY_PREFIX = "registry-tests:cache:";
    private static final int L1_TTL_MINUTES = 5;
    private static final int L2_TTL_MINUTES = 10;
    private static final int L1_MAX_SIZE = 1000;
    private static final int L2_BATCH_SIZE = 100;
    private static final HybridCacheConfig hybridCacheConfig = new HybridCacheConfig(L1_TTL_MINUTES, L2_TTL_MINUTES, L1_MAX_SIZE, L2_BATCH_SIZE, REDIS_KEY_PREFIX);

    @Mock
    private Cache l1Cache;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private HybridCache hybridCache;

    @BeforeEach
    void setUp() {
        hybridCache = new HybridCache(
                CACHE_NAME,
                l1Cache,
                redisTemplate,
                hybridCacheConfig
        );
    }

    @Test
    void getName_returnsCacheName() {
        assertThat(hybridCache.getName()).isEqualTo(CACHE_NAME);
    }

    @Test
    void getNativeCache_returnsThis() {
        assertThat(hybridCache.getNativeCache()).isSameAs(hybridCache);
    }

    @Test
    void get_l1Hit_returnsValueFromL1() {
        Object key = "key1";
        Object value = "value1";
        Cache.ValueWrapper wrapper = () -> value;
        when(l1Cache.get(key)).thenReturn(wrapper);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // when
        Cache.ValueWrapper result = hybridCache.get(key);

        // then
        assertThat(result).isNotNull();
        assertThat(result.get()).isEqualTo(value);
        verify(l1Cache).get(key);
        verify(redisTemplate.opsForValue(), never()).get(any());
    }

    @Test
    void get_l1MissL2Hit_populatesL1AndReturnsValue() {
        Object key = "key2";
        Object value = "value2";
        String redisKey = REDIS_KEY_PREFIX + CACHE_NAME + ":" + key;
        when(l1Cache.get(key)).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(redisKey)).thenReturn(value);

        // when
        Cache.ValueWrapper result = hybridCache.get(key);

        // then
        assertThat(result).isNotNull();
        assertThat(result.get()).isEqualTo(value);
        verify(l1Cache).put(key, value);
        verify(valueOperations).get(redisKey);
    }

    @Test
    void get_l1MissL2Miss_returnsNull() {
        Object key = "key3";
        when(l1Cache.get(key)).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY_PREFIX + CACHE_NAME + ":" + key)).thenReturn(null);

        // when
        Cache.ValueWrapper result = hybridCache.get(key);

        // then
        assertThat(result).isNull();
    }

    @Test
    void get_withType_returnsTypedValue() {
        Object key = "key4";
        String value = "value4";
        Cache.ValueWrapper wrapper = () -> value;
        when(l1Cache.get(key)).thenReturn(wrapper);

        // when
        String result = hybridCache.get(key, String.class);

        // then
        assertThat(result).isEqualTo(value);
    }

    @Test
    void get_withType_wrongType_throwsIllegalStateException() {
        Object key = "key5";
        int value = 42;
        Cache.ValueWrapper wrapper = () -> value;
        when(l1Cache.get(key)).thenReturn(wrapper);

        // when
        assertThrows(IllegalStateException.class, () -> hybridCache.get(key, String.class));
    }

    @Test
    void get_withValueLoader_onHit_returnsCachedValue() {
        Object key = "key6";
        Object value = "value6";
        Cache.ValueWrapper wrapper = () -> value;
        when(l1Cache.get(key)).thenReturn(wrapper);
        Callable<String> valueLoader = () -> "loaded";

        // when
        String result = hybridCache.get(key, valueLoader);

        // then
        assertThat(result).isEqualTo("value6");
    }

    @Test
    void get_withValueLoader_onMiss_loadsAndCachesValue() {
        Object key = "key7";
        String loadedValue = "loaded-value";
        when(l1Cache.get(key)).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY_PREFIX + CACHE_NAME + ":" + key)).thenReturn(null);
        Callable<String> valueLoader = () -> loadedValue;

        // when
        String result = hybridCache.get(key, valueLoader);

        // then
        assertThat(result).isEqualTo(loadedValue);
        verify(l1Cache).put(key, loadedValue);
        verify(valueOperations).set(eq(REDIS_KEY_PREFIX + CACHE_NAME + ":" + key), eq(loadedValue), any(Duration.class));
    }

    @Test
    void get_withValueLoader_throws_wrapsInValueRetrievalException() {
        // given
        Object key = "key8";
        when(l1Cache.get(key)).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any())).thenReturn(null);
        Callable<String> valueLoader = () -> {
            throw new RuntimeException("load failed");
        };

        //when
        assertThrows(Cache.ValueRetrievalException.class, () -> hybridCache.get(key, valueLoader));
    }

    @Test
    void put_writesToL1AndL2() {
        Object key = "key9";
        Object value = "value9";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // when
        hybridCache.put(key, value);

        // then
        verify(l1Cache).put(key, value);
        verify(valueOperations).set(eq(REDIS_KEY_PREFIX + CACHE_NAME + ":" + key), eq(value), any(Duration.class));
    }

    @Test
    void put_nullValue_doesNothing() {
        Object key = "key10";

        // when
        hybridCache.put(key, null);

        // then
        verify(l1Cache, never()).put(any(), any());
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void evict_removesFromL1AndL2() {
        Object key = "key11";
        String redisKey = REDIS_KEY_PREFIX + CACHE_NAME + ":" + key;
        when(redisTemplate.delete(redisKey)).thenReturn(true);

        // when
        hybridCache.evict(key);

        // then
        verify(l1Cache).evict(key);
        verify(redisTemplate).delete(redisKey);
    }

    @Test
    void evictIfPresent_returnsTrueWhenL1Evicted() {
        Object key = "key12";
        String redisKey = REDIS_KEY_PREFIX + CACHE_NAME + ":" + key;
        when(l1Cache.evictIfPresent(key)).thenReturn(true);
        when(redisTemplate.delete(redisKey)).thenReturn(false);

        // when
        boolean result = hybridCache.evictIfPresent(key);

        // then
        assertThat(result).isTrue();
        verify(l1Cache).evictIfPresent(key);
        verify(redisTemplate).delete(redisKey);
    }

    @Test
    void evictIfPresent_returnsTrueWhenL2Evicted() {
        Object key = "key13";
        String redisKey = REDIS_KEY_PREFIX + CACHE_NAME + ":" + key;
        when(l1Cache.evictIfPresent(key)).thenReturn(false);
        when(redisTemplate.delete(redisKey)).thenReturn(true);

        // when
        boolean result = hybridCache.evictIfPresent(key);

        // then
        assertThat(result).isTrue();
    }

    @Test
    void evictIfPresent_returnsFalseWhenNeitherEvicted() {
        Object key = "key14";
        String redisKey = REDIS_KEY_PREFIX + CACHE_NAME + ":" + key;
        when(l1Cache.evictIfPresent(key)).thenReturn(false);
        when(redisTemplate.delete(redisKey)).thenReturn(false);

        // when
        boolean result = hybridCache.evictIfPresent(key);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void clear_clearsL1AndExecutesRedisCallback() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(0L);

        // when
        hybridCache.clear();

        // then
        verify(l1Cache).clear();
        verify(redisTemplate).execute(any(RedisCallback.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void invalidate_clearsAndReturnsTrue() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(0L);

        // when
        boolean result = hybridCache.invalidate();

        // then
        assertThat(result).isTrue();
        verify(l1Cache).clear();
        verify(redisTemplate).execute(any(RedisCallback.class));
    }

    @Test
    void get_l2Throws_returnsNullGracefully() {
        Object key = "key15";
        when(l1Cache.get(key)).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any())).thenThrow(new RuntimeException("Redis down"));

        // when
        Cache.ValueWrapper result = hybridCache.get(key);

        // then
        assertThat(result).isNull();
    }

    @Test
    void put_l2Throws_stillWritesL1() {
        Object key = "key16";
        Object value = "value16";
        doThrow(new RuntimeException("Redis down")).when(valueOperations).set(any(), any(), any(Duration.class));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // when
        hybridCache.put(key, value);

        // then
        verify(l1Cache).put(key, value);
    }

    @Test
    void evict_l2Throws_stillEvictsL1() {
        Object key = "key17";
        String redisKey = REDIS_KEY_PREFIX + CACHE_NAME + ":" + key;
        when(redisTemplate.delete(redisKey)).thenThrow(new RuntimeException("Redis down"));

        // when
        hybridCache.evict(key);

        // then
        verify(l1Cache).evict(key);
    }
}
