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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CacheVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(CacheVerticle.class);
  public static final String PUT_ADDRESS = "cache.put";
  public static final String GET_ADDRESS = "cache.get";

  private Cache<String, Integer> sectorCache;

  @Override
  public Completable rxStart() {

    sectorCache = Caffeine
        .newBuilder()
        .maximumSize(10_000)
        .build();

    vertx.eventBus().consumer(PUT_ADDRESS, this::putLocation);
    vertx.eventBus().consumer(GET_ADDRESS, this::getLocation);

    return Completable.complete();
  }

  private void getLocation(Message<String> message) {
    Integer sector = sectorCache.getIfPresent(message.body());

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

  private void putLocation(Message<JsonObject> message) {
    var greenLantern = message.body().mapTo(LanternLocation.class);
    sectorCache.put(greenLantern.getLantern().getName(), greenLantern.getLocation());
  }
}
