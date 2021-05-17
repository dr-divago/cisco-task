package com.drdivago.cisco.task;

import com.drdivago.cisco.task.verticle.AssignedServiceVerticle;
import com.drdivago.cisco.task.verticle.CacheVerticle;
import com.drdivago.cisco.task.verticle.PublicApiVerticle;
import com.drdivago.cisco.task.verticle.ShazamVerticle;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    public static void main(String... args) {
        DatabindCodec.mapper().registerModule(new Jdk8Module());
        var vertx = Vertx.vertx();

        vertx.rxDeployVerticle(new PublicApiVerticle())
            .subscribe(
                ok -> logger.info("Public Api Service running on port {}", 8080),
                error -> logger.error("Error starting PublicApi {}", error)
            );

        vertx.rxDeployVerticle(new ShazamVerticle())
            .subscribe(
                ok -> logger.info("ShazamVerticle running..."),
                error -> logger.error("Error starting ShazamVerticle {}", error)
            );

      vertx.rxDeployVerticle(new AssignedServiceVerticle())
        .subscribe(
          ok -> logger.info("AssignedVerticle running..."),
          error -> logger.error("Error starting AssignedVerticle {}", error)
        );

        vertx.rxDeployVerticle(new CacheVerticle())
            .subscribe(
                ok -> logger.info("CacheVerticle running..."),
                error -> logger.error("Error starting CacheVerticle {}", error)
            );
    }
}
