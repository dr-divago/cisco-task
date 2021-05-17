package com.drdivago.cisco.task.validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class ShazamValidator implements Validable {

  private static final Logger logger = LoggerFactory.getLogger(ShazamValidator.class);

  @Override
  public Optional<Integer> validate(String body) {
    if (body == null || body.isEmpty()) {
      logger.error("Error validation, response is empty {}", body);
      return Optional.empty();
    }

    var parts = body.split(" ");
    if (parts.length != 2) {
      logger.error("Error validation, response should in format \"Sector Number\" {}", body);
      return Optional.empty();
    }

    try {
      var sector = Integer.parseInt(parts[1]);
      if (!"Sector".equals(parts[0])) {
        logger.error(
          "Error validation, response should contain Sector string, example \"Sector 1453\" {}",
          body);
        return Optional.empty();
      }
      return Optional.of(sector);
    } catch (NumberFormatException e) {
      logger.error(
        "Error validation, response should contains a number sector, example \"Sector 1453\" {}",
        body);
      return Optional.empty();
    }
  }
}
