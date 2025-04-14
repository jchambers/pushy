package com.eatthepath.pushy.apns;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.AsciiString;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

class ApnsChannelManagementRequest {
  private final HttpMethod method;
  private final Http2Headers headers;
  private final AsciiString path;
  private final String payload;
  private final UUID apnsRequestId;
  private final CompletableFuture<Http2Response> responseFuture;

  ApnsChannelManagementRequest(final HttpMethod method,
                               final Http2Headers headers,
                               final AsciiString path,
                               final String payload,
                               final UUID apnsRequestId) {

    this.method = method;
    this.headers = headers;
    this.path = path;
    this.payload = payload;
    this.apnsRequestId = apnsRequestId;
    this.responseFuture = new CompletableFuture<>();
  }

  public HttpMethod getMethod() {
    return method;
  }

  public AsciiString getPath() {
    return path;
  }

  public Http2Headers getHeaders() {
    return headers;
  }

  public String getPayload() {
    return payload;
  }

  public UUID getApnsRequestId() {
    return apnsRequestId;
  }

  public CompletableFuture<Http2Response> getResponseFuture() {
    return responseFuture;
  }
}
