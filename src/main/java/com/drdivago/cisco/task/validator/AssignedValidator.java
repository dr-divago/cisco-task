package com.drdivago.cisco.task.validator;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AssignedValidator  implements Validate {

  private static final Logger logger = LoggerFactory.getLogger(AssignedValidator.class);
  @Override
  public Optional<Integer> validate(String body) {
    if (body == null || body.isEmpty()) {
      logger.error("Error validation, response is empty {}", body);
      return Optional.empty();
    }

    try {
      var sector = Integer.parseInt(body);
      return Optional.of(sector);
    } catch (NumberFormatException e) {
      logger.error(
        "Error validation, response should contains a number sector, example \"1453\" {}",
        body);
      return Optional.empty();
    }
  }
}
