package com.drdivago.cisco.task.model;

public enum ErrorCode {
  WRONG_RESPONSE(100, "Wrong response from the server"),
  TIMEOUT(504, "Upstream server did not produce a response within certain timeframe"),
  NOT_FOUND(404, "Member not found or doesn't exist");

  private int code;
  private String message;

  ErrorCode(int code, String message) {
    this.code = code;
    this.message = message;
  }

  public int getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }
}
