package com.drdivago.cisco.task.service;

import com.drdivago.cisco.task.service.AsyncCacheService.CACHE_TYPE;
import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.reactivex.core.eventbus.Message;

public interface AsyncCacheable<K, V> {
    Single<Message<JsonArray>> get(K key, CACHE_TYPE cache_type);
    void cacheResult(K key, V value, CACHE_TYPE cache_type);
}
