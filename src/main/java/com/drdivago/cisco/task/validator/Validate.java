package com.drdivago.cisco.task.validator;

import java.util.Optional;

public interface Validate {
  Optional<Integer> validate(String body);
}
