package com.drdivago.cisco.task.verticle;

import com.drdivago.cisco.task.common.LanternLocation;
import com.drdivago.cisco.task.service.AsyncCacheService;
import io.reactivex.Completable;
import io.vertx.reactivex.circuitbreaker.CircuitBreaker;
import io.vertx.reactivex.core.AbstractVerticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseVerticle extends AbstractVerticle {

  protected CircuitBreaker circuitBreaker;
  protected AsyncCacheService<LanternLocation> asyncCacheService;

  private static final Logger logger = LoggerFactory.getLogger(BaseVerticle.class);

  @Override
  public Completable rxStart() {
    logger.info("BaseVerticle Started");
    configureCircuitBreak();
    asyncCacheService = new AsyncCacheService<>(vertx, LanternLocation.class);

    return Completable.complete();
  }

  protected abstract void configureCircuitBreak();
}
