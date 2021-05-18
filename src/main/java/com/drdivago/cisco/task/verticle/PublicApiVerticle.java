package com.drdivago.cisco.task.verticle;

import com.drdivago.cisco.task.common.GreenLanternList;
import com.drdivago.cisco.task.common.LanternLocation;
import com.drdivago.cisco.task.common.GreenLantern;
import com.drdivago.cisco.task.service.AsyncCacheService;
import com.drdivago.cisco.task.service.AsyncCacheService.CACHE_TYPE;
import com.drdivago.cisco.task.service.MessageRouterService;
import com.drdivago.cisco.task.writer.MessageWriter;
import io.reactivex.Completable;
import io.reactivex.Single;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.RxHelper;
import io.vertx.reactivex.core.eventbus.Message;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class PublicApiVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(PublicApiVerticle.class);

  private static final String ASSIGNED_LOCATION_ENDPOINT = "/locator/v1/location/assigned/single/:greenLanternName";
  private static final String ASSIGNED_LOCATION_BATCH_ENDPOINT = "/locator/v1/location/assigned/batch/:greenLanternName";
  private static final String CURRENT_LOCATION_ENDPOINT = "/locator/v1/location/current/single/:greenLanternName";
  private static final String CURRENT_LOCATION_BATCH_ENDPOINT = "/locator/v1/location/current/batch/:greenLanternName";

  private AsyncCacheService<LanternLocation> asyncCacheService;
  private MessageRouterService messageRouterService;
  private List<GreenLantern> greenLanternList;

  @Override
  public Completable rxStart() {
    asyncCacheService = new AsyncCacheService<>(vertx, LanternLocation.class);
    messageRouterService = new MessageRouterService(vertx);
    greenLanternList = GreenLanternList.getGreenLanterns();
    var router = Router.router(vertx);
    initRoute(router);

    return vertx
      .createHttpServer()
      .requestHandler(router)
      .rxListen(8080)
      .ignoreElement();
  }

  private void initRoute(Router router) {
    router.get(ASSIGNED_LOCATION_ENDPOINT).handler(this::getAssignedLocation);
    router.get(ASSIGNED_LOCATION_BATCH_ENDPOINT).handler(this::getAssignedBatchLocation);
    router.get(CURRENT_LOCATION_ENDPOINT).handler(this::getCurrentLocation);
    router.get(CURRENT_LOCATION_BATCH_ENDPOINT).handler(this::getCurrentLocationBatch);
  }

  private void getAssignedLocation(RoutingContext ctx) {
    Function<JsonArray, Single<Message<JsonObject>>> route = messageRouterService::askForAssignedLocation;
    askForSingleLocation(ctx, route, CACHE_TYPE.ASSIGNED_LOCATION_CACHE);
  }

  private void getAssignedBatchLocation(RoutingContext ctx) {
    Function<JsonArray, Single<Message<JsonArray>>> route = messageRouterService::askForAssignedLocationBatch;
    askForBatch(ctx, route, CACHE_TYPE.ASSIGNED_LOCATION_CACHE);
  }

  private void getCurrentLocation(RoutingContext ctx) {
    Function<JsonArray, Single<Message<JsonObject>>> f = messageRouterService::askForCurrentLocation;
    askForSingleLocation(ctx, f, CACHE_TYPE.CURRENT_LOCATION_CACHE);
  }

  private void getCurrentLocationBatch(RoutingContext ctx) {
    Function<JsonArray, Single<Message<JsonArray>>> f = messageRouterService::askForCurrentLocationBatch;
    askForBatch(ctx, f, CACHE_TYPE.CURRENT_LOCATION_CACHE);
  }

  private void askForSingleLocation(RoutingContext ctx, Function<JsonArray, Single<Message<JsonObject>>> route, CACHE_TYPE cacheType) {
    String greenLanternName = ctx.pathParam("greenLanternName");
    var writer = new MessageWriter(ctx);
    validateRequest(Arrays.asList(greenLanternName).toArray(new String[1]), writer);

    JsonArray message = new JsonArray().add(0, greenLanternName);
    asyncCacheService
      .tryRecoverFromCache(greenLanternName, cacheType)
      .subscribe(lanternLocation -> {
          lanternLocation
            .stream()
            .forEach( value -> writer.okResponse("Sector " + value.getLocation()));
        handleResponseJsonObject(route.apply(message), writer);
      },
      err -> {
        logger.error("CacheVerticle down or timeout");
        writer.errResponse("Error from server");
      }
    );
  }

  private void askForBatch(RoutingContext ctx, Function<JsonArray, Single<Message<JsonArray>>> f, CACHE_TYPE cacheType) {
    var writer = new MessageWriter(ctx);
    String greenLanternNameList = ctx.pathParam("greenLanternName");
    String[] list = greenLanternNameList.split(",");

    validateRequest(list, writer);

    var message = new JsonArray();
    Stream.of(list).forEach(message::add);

    Single.zip(asyncCacheService.tryRecoverFromCache(list, cacheType), this::mergeResult).subscribe(
      ok -> {
        if (ok.size() == list.length ) //recovered all values from cache
          writer.okResponse(ok);
        handleResponse(f.apply(message), writer); //try to connect to server to recover latest position
      },
      err -> {
        logger.error("Error {}", err);
        handleResponse(f.apply(message), writer);
      });
  }

  private void validateRequest(String[] params, MessageWriter writer) {
    boolean res = Stream.of(params).map(GreenLantern::new).allMatch(greenLanternList::contains);

    if (!res)
     writer.notFound("Wrong parameters");
  }

  private JsonArray mergeResult(Object[] results) {
    var result = new JsonArray();

    IntStream.iterate(0, x -> x + 1)
      .limit(results.length)
      .mapToObj(i -> (Optional<LanternLocation>)results[i])
      .flatMap(Optional::stream)
      .forEach( location -> result.add(new JsonObject()
        .put("lantern", new JsonObject().put("name", location.getLantern().getName()))
        .put("location", location.getLocation()))
        );

    return result;
  }

  private void handleResponseJsonObject(Single<Message<JsonObject>> single, MessageWriter writer) {
    single
        .map(Message::body)
        .subscribeOn(RxHelper.scheduler(vertx))
        .timeout(1, TimeUnit.SECONDS, RxHelper.scheduler(vertx))
        .subscribe(
            response -> writer.okResponse("Sector " + response.getInteger("location")),
            error -> manageError(error, writer)
        );
  }

  private void handleResponse(Single<Message<JsonArray>> single, MessageWriter writer) {
    single
      .map(Message::body)
      .subscribeOn(RxHelper.scheduler(vertx))
      .timeout(1, TimeUnit.SECONDS, RxHelper.scheduler(vertx))
      .subscribe(
        writer::okResponse,
        error -> manageError(error, writer)
      );
  }

  private void manageError(Throwable error, MessageWriter writer) {
    if (error instanceof ReplyException) {
      var ex = (ReplyException) error;
      if (ex.failureType() == ReplyFailure.NO_HANDLERS)
        return;
      if (ex.failureCode() == 404) {
        logger.info("Not found..");
        //we can cache 404 for the request
        writer.notFound("Member not found or doesn't exist");
      }
      if (ex.failureCode() == 504 ) {
        logger.info("Timeout from service");
        writer.errorResponse();
      }
    } else if (error instanceof java.util.concurrent.TimeoutException) {
      logger.info("Service response too slow");
      writer.errorResponse();
    }
  }
}
