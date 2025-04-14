package com.eatthepath.pushy.apns;

import com.eatthepath.json.JsonSerializer;
import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import com.eatthepath.pushy.apns.auth.KeyPairUtil;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.ssl.SSLException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

class ApnsChannelManagementClientTest {

  private ApnsChannelManagementClient channelManagementClient;

  private static ApnsClientResources CLIENT_RESOURCES;

  @RegisterExtension
  static WireMockExtension wireMockExtension = WireMockExtension.newInstance()
      .options(wireMockConfig()
          .keystoreType("PKCS12")
          .keystorePath("server.p12")
          .keystorePassword("pushy-test")
          .keyManagerPassword("pushy-test")
          .dynamicHttpsPort())
      .build();

  protected static final String TEAM_ID = "team-id";
  protected static final String KEY_ID = "key-id";

  private static final String CA_CERTIFICATE_FILENAME = "/ca.pem";

  @BeforeAll
  public static void setUpBeforeClass() {
    CLIENT_RESOURCES = new ApnsClientResources(new NioEventLoopGroup(1));
  }

  @BeforeEach
  void setUp() throws NoSuchAlgorithmException, InvalidKeyException, SSLException {
    final KeyPair keyPair = KeyPairUtil.generateKeyPair();
    final ApnsSigningKey signingKey = new ApnsSigningKey(KEY_ID, TEAM_ID, (ECPrivateKey) keyPair.getPrivate());

    final ApnsClientBuilder clientBuilder = new ApnsClientBuilder()
        .setApnsServer("localhost", wireMockExtension.getRuntimeInfo().getHttpsPort())
        .setTrustedServerCertificateChain(getClass().getResourceAsStream(CA_CERTIFICATE_FILENAME))
        .setSigningKey(signingKey)
        .setApnsClientResources(CLIENT_RESOURCES)
        .setUseAlpn(true);

    channelManagementClient =
        new ApnsChannelManagementClient(clientBuilder.buildClientConfiguration(), CLIENT_RESOURCES);
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    channelManagementClient.close().await();
  }

  @AfterAll
  public static void tearDownAfterAll() throws Exception {
    CLIENT_RESOURCES.shutdownGracefully().await();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void createChannel(final boolean specifyApnsRequestId) {
    final String bundleId = "com.example.Test";
    final UUID apnsRequestId = UUID.randomUUID();
    final String channelId = Base64.getEncoder().encodeToString("channel-id".getBytes(StandardCharsets.UTF_8));

    stubFor(post("/1/apps/com.example.Test/channels")
        .willReturn(status(201)
            .withHeader("apns-channel-id", channelId)
            .withHeader("apns-request-id", apnsRequestId.toString())));

    final CreateChannelResponse createChannelResponse = channelManagementClient.createChannel(bundleId,
        MessageStoragePolicy.MOST_RECENT_MESSAGE_STORED,
        specifyApnsRequestId ? apnsRequestId : null)
        .join();

    assertEquals(201, createChannelResponse.getStatus());
    assertEquals(apnsRequestId, createChannelResponse.getRequestId());
    assertEquals(channelId, createChannelResponse.getChannelId());

    RequestPatternBuilder requestPatternBuilder =
        postRequestedFor(urlEqualTo(String.format("/1/apps/%s/channels", bundleId)))
            .withHeader("authorization", matching("bearer .+"))
            .withRequestBody(equalToJson("{\"message-storage-policy\":1, \"push-type\":\"LiveActivity\"}"));

    if (specifyApnsRequestId) {
      requestPatternBuilder = requestPatternBuilder.withHeader("apns-request-id", equalTo(apnsRequestId.toString()));
    }

    verify(requestPatternBuilder);
  }

  @Test
  void createChannelError() {
    final String bundleId = "com.example.Test";
    final UUID apnsRequestId = UUID.randomUUID();

    stubFor(post("/1/apps/com.example.Test/channels")
        .willReturn(badRequest()
            .withHeader("apns-request-id", apnsRequestId.toString())
            .withBody("{\"reason\":\"BadChannelId\"}")));

    final CompletionException completionException = assertThrows(CompletionException.class, () ->
        channelManagementClient.createChannel(bundleId, MessageStoragePolicy.MOST_RECENT_MESSAGE_STORED, apnsRequestId).join());

    final ChannelManagementException channelManagementException =
        assertInstanceOf(ChannelManagementException.class, completionException.getCause());

    assertEquals(400, channelManagementException.getStatus());
    assertEquals(apnsRequestId, channelManagementException.getApnsRequestId());
    assertEquals("BadChannelId", channelManagementException.getReason());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void getChannelConfiguration(final boolean specifyApnsRequestId) {
    final String bundleId = "com.example.Test";
    final UUID apnsRequestId = UUID.randomUUID();
    final String channelId = Base64.getEncoder().encodeToString("channel-id".getBytes(StandardCharsets.UTF_8));

    stubFor(get("/1/apps/com.example.Test/channels")
        .willReturn(ok("{\"message-storage-policy\":1, \"push-type\":\"LiveActivity\"}")
            .withHeader("apns-request-id", apnsRequestId.toString())));

    final GetChannelConfigurationResponse getChannelConfigurationResponse =
        channelManagementClient.getChannelConfiguration(
            bundleId,
            channelId,
            specifyApnsRequestId ? apnsRequestId : null)
            .join();

    assertEquals(200, getChannelConfigurationResponse.getStatus());
    assertEquals(apnsRequestId, getChannelConfigurationResponse.getRequestId());
    assertEquals(MessageStoragePolicy.MOST_RECENT_MESSAGE_STORED, getChannelConfigurationResponse.getMessageStoragePolicy());

    RequestPatternBuilder requestPatternBuilder =
        getRequestedFor(urlEqualTo(String.format("/1/apps/%s/channels", bundleId)))
            .withHeader("authorization", matching("bearer .+"))
            .withHeader("apns-channel-id", equalTo(channelId));

    if (specifyApnsRequestId) {
      requestPatternBuilder = requestPatternBuilder.withHeader("apns-request-id", equalTo(apnsRequestId.toString()));
    }

    verify(requestPatternBuilder);
  }

  @Test
  void getChannelConfigurationError() {
    final String bundleId = "com.example.Test";
    final UUID apnsRequestId = UUID.randomUUID();
    final String channelId = Base64.getEncoder().encodeToString("channel-id".getBytes(StandardCharsets.UTF_8));

    stubFor(get("/1/apps/com.example.Test/channels")
        .willReturn(badRequest()
            .withHeader("apns-request-id", apnsRequestId.toString())
            .withBody("{\"reason\":\"BadChannelId\"}")));

    final CompletionException completionException = assertThrows(CompletionException.class, () ->
        channelManagementClient.getChannelConfiguration(bundleId, channelId, apnsRequestId).join());

    final ChannelManagementException channelManagementException =
        assertInstanceOf(ChannelManagementException.class, completionException.getCause());

    assertEquals(400, channelManagementException.getStatus());
    assertEquals(apnsRequestId, channelManagementException.getApnsRequestId());
    assertEquals("BadChannelId", channelManagementException.getReason());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void deleteChannel(final boolean specifyApnsRequestId) {
    final String bundleId = "com.example.Test";
    final UUID apnsRequestId = UUID.randomUUID();
    final String channelId = Base64.getEncoder().encodeToString("channel-id".getBytes(StandardCharsets.UTF_8));

    stubFor(delete("/1/apps/com.example.Test/channels")
        .willReturn(noContent()
            .withHeader("apns-request-id", apnsRequestId.toString())));

    final DeleteChannelResponse deleteChannelResponse = channelManagementClient.deleteChannel(bundleId,
        channelId,
        specifyApnsRequestId ? apnsRequestId : null)
        .join();

    assertEquals(204, deleteChannelResponse.getStatus());
    assertEquals(apnsRequestId, deleteChannelResponse.getRequestId());

    RequestPatternBuilder requestPatternBuilder =
        deleteRequestedFor(urlEqualTo(String.format("/1/apps/%s/channels", bundleId)))
            .withHeader("authorization", matching("bearer .+"))
            .withHeader("apns-channel-id", equalTo(channelId));

    if (specifyApnsRequestId) {
      requestPatternBuilder = requestPatternBuilder.withHeader("apns-request-id", equalTo(apnsRequestId.toString()));
    }

    verify(requestPatternBuilder);
  }

  @Test
  void deleteChannelError() {
    final String bundleId = "com.example.Test";
    final UUID apnsRequestId = UUID.randomUUID();
    final String channelId = Base64.getEncoder().encodeToString("channel-id".getBytes(StandardCharsets.UTF_8));

    stubFor(delete("/1/apps/com.example.Test/channels")
        .willReturn(badRequest()
            .withHeader("apns-request-id", apnsRequestId.toString())
            .withBody("{\"reason\":\"BadChannelId\"}")));

    final CompletionException completionException = assertThrows(CompletionException.class, () ->
        channelManagementClient.deleteChannel(bundleId, channelId, apnsRequestId).join());

    final ChannelManagementException channelManagementException =
        assertInstanceOf(ChannelManagementException.class, completionException.getCause());

    assertEquals(400, channelManagementException.getStatus());
    assertEquals(apnsRequestId, channelManagementException.getApnsRequestId());
    assertEquals("BadChannelId", channelManagementException.getReason());
  }

  @ParameterizedTest
  @MethodSource
  void getChannelIds(final boolean specifyApnsRequestId, final List<String> channelIds) {
    final String bundleId = "com.example.Test";
    final UUID apnsRequestId = UUID.randomUUID();

    final Map<String, List<String>> channelResponse = new HashMap<>();
    channelResponse.put("channels", channelIds);

    stubFor(get("/1/apps/com.example.Test/all-channels")
        .willReturn(ok(JsonSerializer.writeJsonTextAsString(channelResponse))
            .withHeader("apns-request-id", apnsRequestId.toString())));

    final GetChannelIdsResponse getChannelIdsResponse = channelManagementClient.getChannelIds(bundleId,
            specifyApnsRequestId ? apnsRequestId : null)
        .join();

    assertEquals(200, getChannelIdsResponse.getStatus());
    assertEquals(apnsRequestId, getChannelIdsResponse.getRequestId());
    assertEquals(channelIds, getChannelIdsResponse.getChannelIds());

    RequestPatternBuilder requestPatternBuilder =
        getRequestedFor(urlEqualTo(String.format("/1/apps/%s/all-channels", bundleId)))
            .withHeader("authorization", matching("bearer .+"));

    if (specifyApnsRequestId) {
      requestPatternBuilder = requestPatternBuilder.withHeader("apns-request-id", equalTo(apnsRequestId.toString()));
    }

    verify(requestPatternBuilder);
  }

  private static Stream<Arguments> getChannelIds() {
    return Stream.of(
        Arguments.argumentSet("Single channel, specified request ID",
            true, generateChannelIds(1)),

        Arguments.argumentSet("Single channel, unspecified request ID",
            false, generateChannelIds(1)),

        // The upstream service has a limit of 10,000 active channels
        Arguments.argumentSet("Large channel batch, specified request ID",
            true, generateChannelIds(10_000)));
  }

  private static List<String> generateChannelIds(final int channelIdCount) {
    final List<String> channelIds = new ArrayList<>(channelIdCount);

    for (int i = 0; i < channelIdCount; i++) {
      channelIds.add(Base64.getEncoder().encodeToString(("channel-id-" + i).getBytes(StandardCharsets.UTF_8)));
    }

    return channelIds;
  }

  @Test
  void getChannelIdsError() {
    final String bundleId = "com.example.Test";
    final UUID apnsRequestId = UUID.randomUUID();

    stubFor(get("/1/apps/com.example.Test/all-channels")
        .willReturn(badRequest()
            .withHeader("apns-request-id", apnsRequestId.toString())
            .withBody("{\"reason\":\"BadChannelId\"}")));

    final CompletionException completionException = assertThrows(CompletionException.class, () ->
        channelManagementClient.getChannelIds(bundleId, apnsRequestId).join());

    final ChannelManagementException channelManagementException =
        assertInstanceOf(ChannelManagementException.class, completionException.getCause());

    assertEquals(400, channelManagementException.getStatus());
    assertEquals(apnsRequestId, channelManagementException.getApnsRequestId());
    assertEquals("BadChannelId", channelManagementException.getReason());
  }
}
