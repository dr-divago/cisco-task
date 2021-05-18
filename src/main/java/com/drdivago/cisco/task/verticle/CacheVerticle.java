package com.drdivago.cisco.task.verticle;

import com.drdivago.cisco.task.common.GreenLantern;
import com.drdivago.cisco.task.common.LanternLocation;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.reactivex.Completable;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.eventbus.Message;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CacheVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(CacheVerticle.class);
  public static final String PUT_ADDRESS_ASSIGNED = "cache.assigned.put";
  public static final String GET_ADDRESS_ASSIGNED = "cache.assigned.get";

  public static final String PUT_ADDRESS_CURRENT = "cache.current.put";
  public static final String GET_ADDRESS_CURRENT = "cache.current.get";

  private Cache<String, Integer> currentSectorCache;
  private Cache<String, Integer> assignedSectorCache;

  @Override
  public Completable rxStart() {

    assignedSectorCache = Caffeine
        .newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofMinutes(1))
        .build();

    currentSectorCache = Caffeine
      .newBuilder()
      .maximumSize(10_000)
      .expireAfterWrite(Duration.ofMinutes(1))
      .build();

    vertx.eventBus().consumer(PUT_ADDRESS_ASSIGNED, this::putLocationAssigned);
    vertx.eventBus().consumer(GET_ADDRESS_ASSIGNED, this::getLocationAssigned);
    vertx.eventBus().consumer(PUT_ADDRESS_CURRENT, this::putLocationCurrent);
    vertx.eventBus().consumer(GET_ADDRESS_CURRENT, this::getLocationCurrent);

    return Completable.complete();
  }

  private void putLocationCurrent(Message<JsonObject> message) {
    put(message, currentSectorCache);
  }

  private void getLocationCurrent(Message<String> message) {
    get(message, currentSectorCache);
  }

  private void getLocationAssigned(Message<String> message) {
    get(message, assignedSectorCache);
  }

  private void putLocationAssigned(Message<JsonObject> message) {
    put(message, assignedSectorCache);
  }

  private void get(Message<String> message, Cache<String, Integer> cache ) {
    Integer sector = cache.getIfPresent(message.body());

    if (sector == null) {
      logger.info("Cache miss, no cached location for greenLantern {}", message.body());
      JsonArray replyMessage = new JsonArray().add(new JsonObject().put("result", "error"));
      message.reply(replyMessage);
    } else {
      logger.info("Found something in cache, return {} to client for {}", sector, message.body());
      JsonArray replyMessage = new JsonArray()
        .add(new JsonObject().put("result", "ok"))
        .add(JsonObject.mapFrom(new LanternLocation(new GreenLantern(message.body()), sector)));

      message.reply(replyMessage);

    }
  }

  private void put(Message<JsonObject> message, Cache<String, Integer> cache) {
    var greenLantern = message.body().mapTo(LanternLocation.class);
    cache.put(greenLantern.getLantern().getName(), greenLantern.getLocation());
  }
}
