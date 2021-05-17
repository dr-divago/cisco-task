package com.drdivago.cisco.task.validator;

import java.util.Optional;

public interface Validable {
  Optional<Integer> validate(String body);
}
