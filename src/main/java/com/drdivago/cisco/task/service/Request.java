package com.drdivago.cisco.task.service;

public class Request {


  private int port;
  private String endpoint;
  private String baseURL;

  public int getPort() {
    return port;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public String getBaseURL() {
    return baseURL;
  }

  public String getURI() {
    return baseURL+endpoint;
  }

  public static class Builder {
    private int port;
    private String endpoint;
    private String baseURL;

    public Builder withPort(int port) {
      this.port = port;
      return this;
    }

    public Builder withBaseURL(String baseURL) {
      this.baseURL = baseURL;
      return this;
    }

    public Builder withEndpoint(String endpoint) {
      this.endpoint = endpoint;
      return this;
    }

    public Request build() {
      return new Request(baseURL, endpoint, port);
    }
  }

  private Request(String baseURL, String endpoint, int port) {
    this.baseURL = baseURL;
    this.endpoint = endpoint;
    this.port = port;
  }

}
