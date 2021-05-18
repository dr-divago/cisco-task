package com.drdivago.cisco.task.verticle;

import com.drdivago.cisco.task.common.LanternLocation;
import com.drdivago.cisco.task.common.ErrorCode;
import com.drdivago.cisco.task.service.AsyncCacheService.CACHE_TYPE;
import com.drdivago.cisco.task.service.ConnectionService;
import com.drdivago.cisco.task.service.MessageRouterService;
import com.drdivago.cisco.task.service.Request;
import com.drdivago.cisco.task.validator.AssignedValidator;
import io.reactivex.Completable;
import io.reactivex.Single;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.TimeoutException;
import io.vertx.core.json.JsonArray;
import io.vertx.reactivex.circuitbreaker.CircuitBreaker;
import io.vertx.reactivex.core.RxHelper;
import io.vertx.reactivex.core.eventbus.Message;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AssignedServiceVerticle extends BaseVerticle {

  private static final Logger logger = LoggerFactory.getLogger(AssignedServiceVerticle.class);

  private static final String ASSIGNED_LOCATION_ENDPOINT = "/sector/v1/assigned/";
  private static final int ASSIGNED_SERVICE_PORT = 9997;

  private ConnectionService connectionService;

  @Override
  public Completable rxStart() {
    super.rxStart();
    logger.info("Started AssignedServiceVerticle");

    connectionService =
      new ConnectionService(vertx)
        .withCircuitBreaker(circuitBreaker)
        .withValidator(new AssignedValidator());

    vertx.eventBus().consumer(MessageRouterService.SHAZAM_ASSIGNED_LOCATION_ADDRESS, this::getAssignedLocationFromSingleMember);
    vertx.eventBus().consumer(MessageRouterService.SHAZAM_ASSIGNED_LOCATION_BATCH, this::getAssignedLocationFromBatch);

    return Completable.complete();
  }

  private void getAssignedLocationFromBatch(Message<JsonArray> message) {
      var result = new JsonArray();
      List<Single<HttpResponse<Optional<LanternLocation>>>> singleList = new ArrayList<>();
      for (var i = 0; i < message.body().size(); i++) {
        var greenLanternName = message.body().getString(i);

        var request = new Request.Builder()
          .withBaseURL(ASSIGNED_LOCATION_ENDPOINT)
          .withPort(ASSIGNED_SERVICE_PORT)
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
          message.reply(result);
        })
        .map(HttpResponse::body)
        .subscribe(
          okResponse -> okResponse.ifPresentOrElse(greenLantern -> {
              logger.info("AssignedService response {}, caching result", greenLantern.toJson());
              asyncCacheService.cacheResult(greenLantern.getLantern().getName(), greenLantern, CACHE_TYPE.ASSIGNED_LOCATION_CACHE);
              result.add(greenLantern.toJson());
            },
            () -> {
              logger.info("Nothing in optional");
              message.fail(ErrorCode.WRONG_RESPONSE.getCode(), ErrorCode.WRONG_RESPONSE.getMessage());
            }),
          err -> {
            logger.error("Timeout connecting to AssignedService:", err);
            message.fail(ErrorCode.TIMEOUT.getCode(), ErrorCode.TIMEOUT.getMessage());
          });
  }

  private void getAssignedLocationFromSingleMember(Message<JsonArray> message) {
    var greenLanternName = message.body().getString(0);

    var request = new Request.Builder()
      .withBaseURL(ASSIGNED_LOCATION_ENDPOINT)
      .withPort(ASSIGNED_SERVICE_PORT)
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
        logger.info("AssignedService response {}, caching result and reply", greenLantern.toJson());
        asyncCacheService.cacheResult(greenLantern.getLantern().getName(), greenLantern, CACHE_TYPE.ASSIGNED_LOCATION_CACHE);
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
    var assignedServiceCircuitBreaker = "assigned-service-circuit-breaker";
    circuitBreaker =
        CircuitBreaker.create(
                assignedServiceCircuitBreaker,
                vertx,
                new CircuitBreakerOptions()
                    .setMaxFailures(5)
                    .setMaxRetries(0)
                    .setTimeout(1000)
                    .setResetTimeout(10000))
            .retryPolicy(retryCount -> retryCount * 100L)
            .openHandler(v -> logger.info("open {}", assignedServiceCircuitBreaker))
            .halfOpenHandler(v -> logger.info("half open {}", assignedServiceCircuitBreaker))
            .closeHandler(v -> logger.info("closed {}", assignedServiceCircuitBreaker));
  }
}
