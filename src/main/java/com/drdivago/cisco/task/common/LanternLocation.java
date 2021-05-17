package com.drdivago.cisco.task.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.json.JsonObject;

public class LanternLocation {

    private final GreenLantern lantern;
    private final Integer location;

    public LanternLocation(@JsonProperty("name")GreenLantern lantern, @JsonProperty("sector")Integer location) {
        this.lantern = lantern;
        this.location = location;
    }

    public GreenLantern getLantern() {
        return lantern;
    }

    public Integer getLocation() {
        return location;
    }

    public JsonObject toJson() {
    return JsonObject.mapFrom(this);
  }
}
