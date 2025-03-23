package com.eatthepath.pushy.apns;

import io.netty.handler.codec.http2.Http2Headers;

class Http2Response {
  private final Http2Headers headers;
  private final byte[] data;

  public Http2Response(final Http2Headers headers, final byte[] data) {
    this.headers = headers;
    this.data = data;
  }
}
