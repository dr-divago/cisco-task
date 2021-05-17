package com.drdivago.cisco.task.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.json.JsonObject;
import java.util.Objects;

public class GreenLantern {
  private final String name;

  public GreenLantern(@JsonProperty("name") String greenLanternName) {
      this.name = greenLanternName;
  }

  public String getName() {
    return name;
  }

  public JsonObject toJson() {
    return JsonObject.mapFrom(this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GreenLantern that = (GreenLantern) o;
    return name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }
}
