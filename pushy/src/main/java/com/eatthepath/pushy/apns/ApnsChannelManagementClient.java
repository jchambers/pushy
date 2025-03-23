package com.eatthepath.pushy.apns;

import com.eatthepath.json.JsonParser;
import com.eatthepath.json.JsonSerializer;
import com.eatthepath.uuid.FastUUID;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.EmptyHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.AsciiString;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * An APNs channel management client issues requests to an APNs channel management server. APNs channel management
 * clients share credentials with their parent {@link ApnsClient}.
 *
 * @see <a href="https://developer.apple.com/documentation/usernotifications/sending-channel-management-requests-to-apns">Sending channel management requests to APNs</a>
 */
class ApnsChannelManagementClient {

  private static final Logger log = LoggerFactory.getLogger(ApnsChannelManagementClient.class);
  private final ApnsChannelPool channelPool;

  private static final AsciiString PATH_PREFIX = AsciiString.of("/1/apps/");
  private static final AsciiString SINGLE_CHANNEL_PATH_SUFFIX = AsciiString.of("/channels");
  private static final AsciiString ALL_CHANNELS_PATH_SUFFIX = AsciiString.of("/all-channels");

  private static final AsciiString CHANNEL_ID_HEADER = AsciiString.of("apns-channel-id");

  private static final String CHANNELS_BODY_KEY = "channels";
  private static final String MESSAGE_STORAGE_POLICY_BODY_KEY = "message-storage-policy";
  private static final String PUSH_TYPE_BODY_KEY = "push-type";

  private static final ApnsChannelPoolMetricsListener NO_OP_METRICS_LISTENER = new ApnsChannelPoolMetricsListener() {

    @Override
    public void handleConnectionAdded() {
    }

    @Override
    public void handleConnectionRemoved() {
    }

    @Override
    public void handleConnectionCreationFailed() {
    }
  };

  private static class SimpleCreateChannelResponse implements CreateChannelResponse {

    private final String channelId;

    private final int status;
    private final UUID requestId;

    private SimpleCreateChannelResponse(final String channelId, final int status, final UUID requestId) {
      this.channelId = channelId;
      this.status = status;
      this.requestId = requestId;
    }

    @Override
    public String getChannelId() {
      return channelId;
    }

    @Override
    public int getStatus() {
      return status;
    }

    @Override
    public UUID getRequestId() {
      return requestId;
    }
  }

  private static class SimpleGetChannelConfigurationResponse implements GetChannelConfigurationResponse {

    private final MessageStoragePolicy messageStoragePolicy;

    private final int status;
    private final UUID requestId;

    private SimpleGetChannelConfigurationResponse(final MessageStoragePolicy messageStoragePolicy,
                                                  final int status,
                                                  final UUID requestId) {

      this.messageStoragePolicy = messageStoragePolicy;
      this.status = status;
      this.requestId = requestId;
    }

    @Override
    public MessageStoragePolicy getMessageStoragePolicy() {
      return messageStoragePolicy;
    }

    public int getStatus() {
      return status;
    }

    public UUID getRequestId() {
      return requestId;
    }
  }

  private static class SimpleDeleteChannelResponse implements DeleteChannelResponse {

    private final int status;
    private final UUID requestId;

    private SimpleDeleteChannelResponse(final int status, final UUID requestId) {
      this.status = status;
      this.requestId = requestId;
    }

    @Override
    public int getStatus() {
      return status;
    }

    @Override
    public UUID getRequestId() {
      return requestId;
    }
  }

  private static class SimpleGetChannelIdsResponse implements GetChannelIdsResponse {

    private final List<String> channelIds;

    private final int status;
    private final UUID requestId;

    private SimpleGetChannelIdsResponse(final List<String> channelIds, final int status, final UUID requestId) {
      this.channelIds = channelIds;
      this.status = status;
      this.requestId = requestId;
    }

    @Override
    public List<String> getChannelIds() {
      return channelIds;
    }

    @Override
    public int getStatus() {
      return status;
    }

    @Override
    public UUID getRequestId() {
      return requestId;
    }
  }

  ApnsChannelManagementClient(final ApnsClientConfiguration clientConfiguration, final ApnsClientResources clientResources) {
    final ApnsChannelManagementChannelFactory channelFactory =
        new ApnsChannelManagementChannelFactory(clientConfiguration, clientResources);

    this.channelPool = new ApnsChannelPool(channelFactory,
        1,
        clientResources.getEventLoopGroup().next(),
        NO_OP_METRICS_LISTENER);
  }

  public CompletableFuture<CreateChannelResponse> createChannel(final String bundleId,
                                                                final MessageStoragePolicy messageStoragePolicy,
                                                                final UUID apnsRequestId) {

    final Map<String, Object> requestBody = new HashMap<>();
    requestBody.put(MESSAGE_STORAGE_POLICY_BODY_KEY, messageStoragePolicy.getCode());
    requestBody.put(PUSH_TYPE_BODY_KEY, "LiveActivity");

    final ApnsChannelManagementRequest request = new ApnsChannelManagementRequest(HttpMethod.POST,
        EmptyHttp2Headers.INSTANCE,
        PATH_PREFIX.concat(bundleId).concat(SINGLE_CHANNEL_PATH_SUFFIX),
        JsonSerializer.writeJsonTextAsString(requestBody),
        apnsRequestId);

    sendRequest(request);

    return request.getResponseFuture()
        .thenApply(http2Response -> {
          final HttpResponseStatus status = HttpResponseStatus.parseLine(http2Response.getHeaders().status());
          final UUID apnsRequestIdFromResponse = getApnsRequestId(http2Response.getHeaders());

          if (status.code() == 201) {
            return new SimpleCreateChannelResponse(http2Response.getHeaders().get(CHANNEL_ID_HEADER).toString(),
                status.code(),
                apnsRequestIdFromResponse);
          } else {
            throw new ChannelManagementException(status.code(), apnsRequestIdFromResponse, getErrorReason(http2Response));
          }
        });
  }

  public CompletableFuture<GetChannelConfigurationResponse> getChannelConfiguration(final String bundleId,
                                                                                    final String channelId,
                                                                                    final UUID apnsRequestId) {

    final Http2Headers headers = new DefaultHttp2Headers();
    headers.add(CHANNEL_ID_HEADER, channelId);

    final ApnsChannelManagementRequest request = new ApnsChannelManagementRequest(HttpMethod.GET,
        headers,
        PATH_PREFIX.concat(bundleId).concat(SINGLE_CHANNEL_PATH_SUFFIX),
        null,
        apnsRequestId);

    sendRequest(request);

    return request.getResponseFuture()
        .thenApply(http2Response -> {
          final HttpResponseStatus status = HttpResponseStatus.parseLine(http2Response.getHeaders().status());
          final UUID apnsRequestIdFromResponse = getApnsRequestId(http2Response.getHeaders());

          if (status.code() == 200) {
            final Map<String, Object> parsedResponse;

            try {
              parsedResponse = new JsonParser().parseJsonObject(new String(http2Response.getData(), StandardCharsets.UTF_8));
            } catch (final ParseException e) {
              throw new CompletionException(e);
            }

            final MessageStoragePolicy messageStoragePolicy =
                MessageStoragePolicy.getFromCode(((Long) parsedResponse.get(MESSAGE_STORAGE_POLICY_BODY_KEY)).intValue());

            return new SimpleGetChannelConfigurationResponse(messageStoragePolicy, status.code(), apnsRequestIdFromResponse);
          } else {
            throw new ChannelManagementException(status.code(), apnsRequestIdFromResponse, getErrorReason(http2Response));
          }
        });
  }

  public CompletableFuture<DeleteChannelResponse> deleteChannel(final String bundleId,
                                                                final String channelId,
                                                                final UUID apnsRequestId) {

    final Http2Headers headers = new DefaultHttp2Headers();
    headers.add(CHANNEL_ID_HEADER, channelId);

    final ApnsChannelManagementRequest request = new ApnsChannelManagementRequest(HttpMethod.DELETE,
        headers,
        PATH_PREFIX.concat(bundleId).concat(SINGLE_CHANNEL_PATH_SUFFIX),
        null,
        apnsRequestId);

    sendRequest(request);

    return request.getResponseFuture()
        .thenApply(http2Response -> {
          final HttpResponseStatus status = HttpResponseStatus.parseLine(http2Response.getHeaders().status());
          final UUID apnsRequestIdFromResponse = getApnsRequestId(http2Response.getHeaders());

          if (status.code() == 204) {
            return new SimpleDeleteChannelResponse(status.code(), apnsRequestIdFromResponse);
          } else {
            throw new ChannelManagementException(status.code(), apnsRequestIdFromResponse, getErrorReason(http2Response));
          }
        });
  }

  public CompletableFuture<GetChannelIdsResponse> getChannelIds(final String bundleId, final UUID apnsRequestId) {
    final ApnsChannelManagementRequest request = new ApnsChannelManagementRequest(HttpMethod.GET,
        EmptyHttp2Headers.INSTANCE,
        PATH_PREFIX.concat(bundleId).concat(ALL_CHANNELS_PATH_SUFFIX),
        null,
        apnsRequestId);

    sendRequest(request);

    return request.getResponseFuture()
        .thenApply(http2Response -> {
          final HttpResponseStatus status = HttpResponseStatus.parseLine(http2Response.getHeaders().status());
          final UUID apnsRequestIdFromResponse = getApnsRequestId(http2Response.getHeaders());

          if (status.code() == 200) {
            final Map<String, Object> parsedResponse;

            try {
              parsedResponse = new JsonParser().parseJsonObject(new String(http2Response.getData(), StandardCharsets.UTF_8));
            } catch (final ParseException e) {
              throw new CompletionException(e);
            }

            @SuppressWarnings("unchecked") final List<String> channelIds =
                (List<String>) parsedResponse.get(CHANNELS_BODY_KEY);

            return new SimpleGetChannelIdsResponse(channelIds, status.code(), apnsRequestIdFromResponse);
          } else {
            throw new ChannelManagementException(status.code(), apnsRequestIdFromResponse, getErrorReason(http2Response));
          }
        });
  }

  private void sendRequest(final ApnsChannelManagementRequest request) {
    this.channelPool.acquire().addListener((GenericFutureListener<Future<Channel>>) acquireFuture -> {
      if (acquireFuture.isSuccess()) {
        final Channel channel = acquireFuture.getNow();

        channel.writeAndFlush(request);
        channelPool.release(channel);
      } else {
        request.getResponseFuture().completeExceptionally(acquireFuture.cause());
      }
    });
  }

  private static UUID getApnsRequestId(final Http2Headers headers) {
    final CharSequence uuidSequence = headers.get(ApnsChannelManagementHandler.APNS_REQUEST_ID_HEADER);

    try {
      return uuidSequence != null ? FastUUID.parseUUID(uuidSequence) : null;
    } catch (final IllegalArgumentException e) {
      return null;
    }
  }

  private static String getErrorReason(final Http2Response http2Response) {
    if (http2Response.getData() == null) {
      return null;
    }

    try {
      final Map<String, Object> parsedResponse =
          new JsonParser().parseJsonObject(new String(http2Response.getData(), StandardCharsets.UTF_8));

      return (String) parsedResponse.get("reason");
    } catch (final Exception e) {
      log.warn("Could not extract error reason from response", e);
      return null;
    }
  }

  Future<Void> close() {
    return channelPool.close();
  }
}
