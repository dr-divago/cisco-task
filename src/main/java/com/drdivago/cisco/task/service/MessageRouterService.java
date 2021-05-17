package com.drdivago.cisco.task.service;

import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.eventbus.Message;

public class MessageRouterService {

  public static final String SHAZAM_CURRENT_LOCATION_ADDRESS = "current.location.single";
  public static final String SHAZAM_ASSIGNED_LOCATION_ADDRESS = "assigned.location.single";
  public static final String SHAZAM_CURRENT_LOCATION_BATCH = "current.location.batch";
  public static final String SHAZAM_ASSIGNED_LOCATION_BATCH = "assigned.location.batch";

  private final Vertx vertx;

  public MessageRouterService(Vertx vertx) {
    this.vertx = vertx;
  }

  public Single<Message<JsonObject>> askForCurrentLocation(JsonArray message) {
    return vertx.eventBus().rxRequest(SHAZAM_CURRENT_LOCATION_ADDRESS, message);
  }

  public Single<Message<JsonObject>> askForAssignedLocation(JsonArray message) {
    return vertx.eventBus().rxRequest(SHAZAM_ASSIGNED_LOCATION_ADDRESS, message);
  }

  public Single<Message<JsonArray>> askForCurrentLocationBatch(JsonArray message) {
    return vertx.eventBus().rxRequest(SHAZAM_CURRENT_LOCATION_BATCH, message);
  }

  public Single<Message<JsonArray>> askForAssignedLocationBatch(JsonArray message) {
    return vertx.eventBus().rxRequest(SHAZAM_CURRENT_LOCATION_BATCH, message);
  }
}
