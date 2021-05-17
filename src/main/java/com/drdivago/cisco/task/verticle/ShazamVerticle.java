package com.drdivago.cisco.task.verticle;

import com.drdivago.cisco.task.common.LanternLocation;
import com.drdivago.cisco.task.model.ErrorCode;
import com.drdivago.cisco.task.service.ConnectionService;
import com.drdivago.cisco.task.service.MessageRouterService;
import com.drdivago.cisco.task.service.Request;
import com.drdivago.cisco.task.validator.ShazamValidator;
import io.reactivex.Completable;
import io.reactivex.Single;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.TimeoutException;
import io.vertx.core.json.JsonArray;
import io.vertx.reactivex.circuitbreaker.CircuitBreaker;
import io.vertx.reactivex.core.RxHelper;
import io.vertx.reactivex.core.eventbus.Message;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ShazamVerticle extends BaseVerticle {

  private static final Logger logger = LoggerFactory.getLogger(ShazamVerticle.class);

  private static final String CURRENT_LOCATION_ENDPOINT = "/shazam/v1/location/";
  private static final int SHAZAM_SERVICE_PORT = 9998;

  private static final String SHAZAM_CIRCUIT_BREAKER = "shazam-service-circuit-breaker";

  private ConnectionService connectionService;

  @Override
  public Completable rxStart() {
    super.rxStart();
    logger.info("ShazamVerticle started...");

    connectionService =
        new ConnectionService(vertx)
            .withCircuitBreaker(circuitBreaker)
            .withValidator(new ShazamValidator());

    var eventBus = vertx.eventBus();
    eventBus.consumer(MessageRouterService.SHAZAM_CURRENT_LOCATION_ADDRESS, this::getCurrentLocationFromSingleMember);
    eventBus.consumer(MessageRouterService.SHAZAM_CURRENT_LOCATION_BATCH, this::getCurrentLocationFromBatch);

    return Completable.complete();
  }

  @Override
  public Completable rxStop() {
    return Completable.complete();
  }

  private void getCurrentLocationFromBatch(Message<JsonArray> message) {
    var result = new JsonArray();
    List<Single<HttpResponse<Optional<LanternLocation>>>> singleList = new ArrayList<>();
    for (var i = 0; i < message.body().size(); i++) {
      var greenLanternName = message.body().getString(i);

      var request = new Request.Builder()
        .withBaseURL(CURRENT_LOCATION_ENDPOINT)
        .withPort(SHAZAM_SERVICE_PORT)
        .withEndpoint(greenLanternName)
        .build();

      Single<HttpResponse<Optional<LanternLocation>>> single =
        connectionService
          .build(request)
          .subscribeOn(RxHelper.scheduler(vertx));

      singleList.add(single);
    }

    Single.mergeDelayError(singleList)
        .doOnComplete(() -> {
          logger.info("Complete");
          message.reply(result);
        })
        .map(HttpResponse::body)
        .subscribe(
          okResponse -> okResponse.ifPresentOrElse(greenLantern -> {
              logger.info("ShazamService response {}, caching result", greenLantern.toJson());
              asyncCacheService.cacheResult(greenLantern.getLantern().getName(), greenLantern);
              result.add(greenLantern.toJson());
            },
            () -> {
              logger.info("Nothing in optional");
              message.fail(ErrorCode.WRONG_RESPONSE.getCode(), ErrorCode.WRONG_RESPONSE.getMessage());
            }),
          err -> {
              logger.info("Error");
              message.fail(ErrorCode.TIMEOUT.getCode(), ErrorCode.TIMEOUT.getMessage());
          });
  }


  private void getCurrentLocationFromSingleMember(Message<JsonArray> message) {
    var greenLanternName = message.body().getString(0);

    var request = new Request.Builder()
      .withBaseURL(CURRENT_LOCATION_ENDPOINT)
      .withPort(SHAZAM_SERVICE_PORT)
      .withEndpoint(greenLanternName)
      .build();

    var single = connectionService
      .build(request)
      .subscribeOn(RxHelper.scheduler(vertx));

    connectionService
      .connect(single)
      .subscribe(
        okResponse -> manageOk(okResponse, message),
        err -> manageError(err, message)
      );
  }

  private void manageOk(Optional<LanternLocation> okResponse, Message<JsonArray> message) {
    okResponse.ifPresentOrElse( greenLantern -> {
        logger.info("ShazamService response {}, caching result and reply", greenLantern.toJson());
        asyncCacheService.cacheResult(greenLantern.getLantern().getName(), greenLantern);
        message.reply(greenLantern.toJson());
      },
      () -> message.fail(ErrorCode.WRONG_RESPONSE.getCode(), ErrorCode.WRONG_RESPONSE.getMessage())
    );
  }

  private void manageError(Throwable err, Message<JsonArray> message) {
    if (err instanceof TimeoutException) {
      message.fail(ErrorCode.TIMEOUT.getCode(), ErrorCode.TIMEOUT.getMessage());
    } else
      message.fail(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
  }

  @Override
  protected void configureCircuitBreak() {
    circuitBreaker =
        CircuitBreaker.create(
                SHAZAM_CIRCUIT_BREAKER,
                vertx,
                new CircuitBreakerOptions()
                    .setMaxFailures(5)
                    .setMaxRetries(0)
                    .setTimeout(3000)
                    .setResetTimeout(10000))
            .retryPolicy(retryCount -> retryCount * 100L)
            .openHandler(v -> logger.info("open {}", SHAZAM_CIRCUIT_BREAKER))
            .halfOpenHandler(v -> logger.info("half open {}", SHAZAM_CIRCUIT_BREAKER))
            .closeHandler(v -> logger.info("closed {}", SHAZAM_CIRCUIT_BREAKER));
  }
}
