package com.drdivago.cisco.task.service;

import com.drdivago.cisco.task.verticle.CacheVerticle;
import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.eventbus.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncCacheService<T> implements AsyncCacheable<String, T> {

  private static final Logger logger = LoggerFactory.getLogger(AsyncCacheService.class);
  private final Vertx vertx;
  private final Class<T> classType;

  public enum CACHE_TYPE {
    ASSIGNED_LOCATION_CACHE(CacheVerticle.GET_ADDRESS_ASSIGNED, CacheVerticle.PUT_ADDRESS_ASSIGNED),
    CURRENT_LOCATION_CACHE(CacheVerticle.GET_ADDRESS_CURRENT, CacheVerticle.PUT_ADDRESS_CURRENT);

    private String get;
    private String put;
    CACHE_TYPE(String getAddressAssigned, String putAddressAssigned) {
      this.get = getAddressAssigned;
      this.put = putAddressAssigned;
    }
  }

  public AsyncCacheService(Vertx vertx, Class<T> classType) {
    this.vertx = vertx;
    this.classType = classType;
    logger.info("AsyncCache vertx {}", vertx);
  }

  @Override
  public Single<Message<JsonArray>> get(String key, CACHE_TYPE cache_type) {
    return vertx.eventBus().rxRequest(cache_type.get, key);
  }

  @Override
  public void cacheResult(String key, T value, CACHE_TYPE cache_type) {
    vertx.eventBus().request(cache_type.put, JsonObject.mapFrom(value));
  }

  public Single<Optional<T>> tryRecoverFromCache(String key, CACHE_TYPE cacheType) {
    logger.info("Trying to recover location from cache...");

    Single<Message<JsonArray>> cacheResponse = get(key, cacheType);
    return cacheResponse.map( x -> {
      var object = x.body().getJsonObject(0);
      var result = object.getString("result");
      if (result.equals("ok")) {
        return Optional.of(x.body().getJsonObject(1).mapTo(getClassType()));
      }
      else {
        return Optional.empty();
      }
    });
  }

  public List<Single<Optional<T>>> tryRecoverFromCache(String[] keys, CACHE_TYPE cacheType) {
    logger.info("Error connecting to external service! Trying to recover location from cache...");

    List<Single<Optional<T>>> lists = new ArrayList<>();

    for (var i = 0; i < keys.length; i++) {
      Single<Message<JsonArray>> cacheResponse = get(keys[i], cacheType);
      lists.add(cacheResponse.map( x -> {
        var object = x.body().getJsonObject(0);
        var result = object.getString("result");
        if (result.equals("ok")) {
          return Optional.of(x.body().getJsonObject(1).mapTo(getClassType()));
        }
        else {
          return Optional.empty();
        }
      }));
    }

    return lists;
  }

  public Class<T> getClassType() {
    return classType;
  }
}
