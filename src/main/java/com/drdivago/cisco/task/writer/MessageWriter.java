package com.drdivago.cisco.task.writer;

import io.vertx.core.json.JsonArray;
import io.vertx.reactivex.ext.web.RoutingContext;

public class MessageWriter {
    private RoutingContext ctx;

    public static final String ERROR_504 = "\"Upstream server did not produce a response with a certain time\"";

    public MessageWriter(RoutingContext ctx) {
        this.ctx = ctx;
    }

    private boolean isResponseSent() {
      return ctx.response().ended();
    }

    public void okResponse(String resp) {
    if (!isResponseSent()) {
      ctx.response().setStatusCode(200).putHeader("Content-Type", "text/plain").end(resp);
        }
    }

  public void okResponse(JsonArray resp) {
    if (!isResponseSent()) {
      ctx.response().setStatusCode(200).putHeader("Content-Type", "application/json").end(resp.encode());
    }
  }

    public void errorResponse() {
        errResponse(ERROR_504);
    }

  public void notFound(String resp) {
    if (!isResponseSent()) {
      ctx.response().setStatusCode(404).end(resp);
    }
  }

    public void errResponse(String resp) {
    if (!isResponseSent()) {
      ctx.response().setStatusCode(504).end(resp);
        }
    }
}
