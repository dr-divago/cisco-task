package com.drdivago.cisco.task.service;

import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.reactivex.core.eventbus.Message;

public interface AsyncCacheable<K, V> {
    Single<Message<JsonArray>> get(K key);
    void cacheResult(K key, V value);
}
