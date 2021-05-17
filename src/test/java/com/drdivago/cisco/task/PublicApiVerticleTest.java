package com.drdivago.cisco.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.drdivago.cisco.task.common.GreenLantern;
import com.drdivago.cisco.task.common.LanternLocation;
import com.drdivago.cisco.task.service.MessageRouterService;
import com.drdivago.cisco.task.verticle.CacheVerticle;
import com.drdivago.cisco.task.verticle.PublicApiVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.codec.BodyCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(VertxExtension.class)
public class PublicApiVerticleTest {

  private static final int SHAZAM_SERVICE_PORT = 9998;
  private static final String CURRENT_LOCATION_ENDPOINT = "/shazam/v1/location/";


  private static final Logger logger = LoggerFactory.getLogger(PublicApiVerticleTest.class);

  @Test
  public void  whenRequestIsTimeout_ThenError504(Vertx vertx, VertxTestContext testContext) {

      io.vertx.reactivex.core.Vertx vertx1 = new io.vertx.reactivex.core.Vertx(vertx);
      WebClient webClient = WebClient.create(vertx1);

      Future<String> f1 = vertx.deployVerticle(CacheVerticle.class.getName());
      Future<String> f2 = vertx.deployVerticle(PublicApiVerticle.class.getName());

      CompositeFuture.all(f1, f2).onComplete(testContext.succeeding(id -> {

            vertx.eventBus().consumer(MessageRouterService.SHAZAM_CURRENT_LOCATION_ADDRESS, message -> {
              JsonArray replyMessage = new JsonArray().add(new LanternLocation(new GreenLantern("Test"), 0));
              vertx.setTimer(1100, idx -> message.reply(replyMessage));
            });

            webClient
              .get(8080, "localhost", "/locator/v1/location/current/single/Ganthet")
              .as(BodyCodec.string())
              .putHeader("Content-Type", "text/plain")
              .send(testContext.succeeding(resp -> testContext.verify(() -> {
                System.out.println(resp.statusCode());
                assertThat(resp.statusCode()).isEqualTo(504);
                assertThat(resp.body()).contains("Upstream server did not produce a response with a certain time");
                testContext.completeNow();
              })));
          }));
    }

  @Test
  public void whenValueIsCached_ThenOk(Vertx vertx, VertxTestContext testContext) {

    io.vertx.reactivex.core.Vertx vertx1 = new io.vertx.reactivex.core.Vertx(vertx);
    WebClient webClient = WebClient.create(vertx1);

    Future<String> f1 = vertx.deployVerticle(CacheVerticle.class.getName());
    Future<String> f2 = vertx.deployVerticle(PublicApiVerticle.class.getName());

    CompositeFuture.all(f1, f2).onComplete(testContext.succeeding(id -> {
      LanternLocation lanternLocation = new LanternLocation(new GreenLantern("Ganthet"), 10);
      vertx.eventBus().request(CacheVerticle.PUT_ADDRESS, JsonObject.mapFrom(lanternLocation));
        webClient
          .get(8080, "localhost", "/locator/v1/location/current/single/Ganthet")
          .as(BodyCodec.string())
          .putHeader("Content-Type", "text/plain")
          .send(testContext.succeeding(resp -> testContext.verify(
            () -> {
              System.out.println(resp.statusCode());
              assertThat(resp.statusCode()).isEqualTo(200);
              assertThat(resp.body()).contains("Sector " + 10);
              testContext.completeNow();
            })));
    }
    ));
    }

  @Test
  public void  whenWrongParameter_ThenError404(Vertx vertx, VertxTestContext testContext) {

    io.vertx.reactivex.core.Vertx vertx1 = new io.vertx.reactivex.core.Vertx(vertx);
    WebClient webClient = WebClient.create(vertx1);

    Future<String> f1 = vertx.deployVerticle(CacheVerticle.class.getName());
    Future<String> f2 = vertx.deployVerticle(PublicApiVerticle.class.getName());

    CompositeFuture.all(f1, f2).onComplete(testContext.succeeding(id -> {

      LanternLocation lanternLocation = new LanternLocation(new GreenLantern("Ganthet"), 10);
      vertx.eventBus().request(CacheVerticle.PUT_ADDRESS, JsonObject.mapFrom(lanternLocation));

      webClient
        .get(8080, "localhost", "/locator/v1/location/current/single/test")
        .as(BodyCodec.string())
        .putHeader("Content-Type", "text/plain")
        .send(testContext.succeeding(resp -> testContext.verify(() -> {
          System.out.println(resp.statusCode());
          assertThat(resp.statusCode()).isEqualTo(404);
          testContext.completeNow();
        })));
    }));
  }
}
