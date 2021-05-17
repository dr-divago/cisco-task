package com.drdivago.cisco.task.service;

import com.drdivago.cisco.task.common.GreenLantern;
import com.drdivago.cisco.task.common.LanternLocation;
import com.drdivago.cisco.task.validator.Validable;
import io.reactivex.Single;
import io.vertx.core.Promise;
import io.vertx.reactivex.circuitbreaker.CircuitBreaker;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.client.predicate.ResponsePredicateResult;
import io.vertx.reactivex.ext.web.codec.BodyCodec;
import io.vertx.reactivex.impl.AsyncResultSingle;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionService {

  private static final Logger logger = LoggerFactory.getLogger(ConnectionService.class);
  protected WebClient webClient;
  private boolean withCircuitBreaker;
  private CircuitBreaker circuitBreaker;
  private Validable validator;


  private Function<HttpResponse<Void>, ResponsePredicateResult> statusCodeValidator =
    resp -> {
      if (resp.statusCode() == 200 || resp.statusCode() == 404) {
        return ResponsePredicateResult.success();
      }
      return ResponsePredicateResult.failure("Does not work");
    };

  public ConnectionService(Vertx vertx) {
    webClient = WebClient.create(vertx);
  }

  public ConnectionService withCircuitBreaker(CircuitBreaker circuitBreaker) {
    this.withCircuitBreaker = true;
    this.circuitBreaker = circuitBreaker;
    return this;
  }

  public ConnectionService withValidator(Validable validator) {
    this.validator = validator;
    return this;
  }



  public Single<HttpResponse<Optional<LanternLocation>>> build(Request request) {
    return webClient
        .get(request.getPort(), "localhost", request.getURI())
        .as(BodyCodec.create( buffer -> validate(buffer, request.getEndpoint())))
        .expect(statusCodeValidator)
        .rxSend();
  }

  private Optional<LanternLocation> validate(Buffer buffer, String name) {
    Optional<Integer> maybeSector = validator.validate(buffer.toString());

    return maybeSector
      .stream()
      .map( sectorNumber -> Optional.of(new LanternLocation(new GreenLantern(name), sectorNumber)))
      .flatMap(Optional::stream)
      .findFirst();
  }

  public Single<Optional<LanternLocation>> connect(Single<HttpResponse<Optional<LanternLocation>>> currentRequest) {
    if (withCircuitBreaker) {
      return circuitBreaker.rxExecute(
        promise -> {
            logger.info("Trying connecting to Shazam service..");
            currentRequest.subscribe(
              ok -> {
                  int statusCode = ok.statusCode();
                  if (statusCode == 404) {
                    promise.fail(ok.statusMessage());
                  }
                    promise.complete(ok.body());
                },
              err -> promise.fail(err));
        });
    } else {
      Promise<HttpResponse<Optional<LanternLocation>>> promise = Promise.promise();
      return AsyncResultSingle.toSingle(
          handler -> currentRequest.subscribe(promise::complete, promise::fail));
    }
  }
}
