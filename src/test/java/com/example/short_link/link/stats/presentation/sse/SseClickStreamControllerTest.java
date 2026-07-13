package com.example.short_link.link.stats.presentation.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.security.ClickStreamTokenService;
import com.example.short_link.link.access.application.LinkAccessGuard;
import com.example.short_link.link.application.read.LinkLookupQueryService;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.support.TestEntities;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class SseClickStreamControllerTest {

  @Mock private ClickStreamTokenService tokens;
  @Mock private LinkLookupQueryService lookup;
  @Mock private SseClickStreamRegistry registry;
  @Mock private LinkAccessGuard accessGuard;
  @Mock private HttpServletResponse response;

  private SimpleMeterRegistry meterRegistry;
  private SseClickStreamController controller;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    controller = new SseClickStreamController(tokens, lookup, registry, meterRegistry, accessGuard);
  }

  @Test
  void missingLinkThrowsLinkNotFound() {
    when(tokens.parseStreamToken("stok", "abc")).thenReturn(7L);
    when(lookup.findEntity(new ShortCode("abc"))).thenReturn(Optional.empty());
    assertThatThrownBy(() -> controller.stream(new ShortCode("abc"), "stok", null, response))
        .isInstanceOf(LinkException.class);
  }

  @Test
  void deniedAccessThrowsNotOwned() {
    LinkEntity link = new LinkEntity("https://x", "abc", 99L, null);
    TestEntities.withId(link, 1L);
    when(tokens.parseStreamToken("stok", "abc")).thenReturn(7L);
    when(lookup.findEntity(new ShortCode("abc"))).thenReturn(Optional.of(link));
    when(accessGuard.canView(7L, link)).thenReturn(false);
    assertThatThrownBy(() -> controller.stream(new ShortCode("abc"), "stok", null, response))
        .isInstanceOf(LinkException.class);
  }

  @Test
  void registryRejectsReturns429FailFast() {
    LinkEntity link = new LinkEntity("https://x", "abc", 7L, null);
    TestEntities.withId(link, 1L);
    when(tokens.parseStreamToken("stok", "abc")).thenReturn(7L);
    when(lookup.findEntity(new ShortCode("abc"))).thenReturn(Optional.of(link));
    when(accessGuard.canView(7L, link)).thenReturn(true);
    when(registry.register(eq(new LinkId(1L)), any(SseEmitter.class))).thenReturn(false);
    SseEmitter out = controller.stream(new ShortCode("abc"), "stok", null, response);
    verify(response).setStatus(429);
    assertThat(out).isNotNull();
  }

  @Test
  void streamTokenConnectsWithoutLegacyAccessTokenInUrl() {
    LinkEntity link = new LinkEntity("https://x", "abc", 7L, null);
    TestEntities.withId(link, 1L);
    when(tokens.parseStreamToken("stok", "abc")).thenReturn(7L);
    when(lookup.findEntity(new ShortCode("abc"))).thenReturn(Optional.of(link));
    when(accessGuard.canView(7L, link)).thenReturn(true);
    when(registry.register(eq(new LinkId(1L)), any(SseEmitter.class))).thenReturn(true);

    SseEmitter out = controller.stream(new ShortCode("abc"), "stok", null, response);

    assertThat(out).isNotNull();
    assertThat(meterRegistry.counter("sse.click_stream.connected").count()).isEqualTo(1.0);
  }

  @Test
  void invalidStreamTokenReturns401EmitterAndCompletes() {
    when(tokens.parseStreamToken("bad-stream", "abc")).thenThrow(new RuntimeException("nope"));

    SseEmitter emitter = controller.stream(new ShortCode("abc"), "bad-stream", null, response);

    verify(response).setStatus(401);
    assertThat(emitter).isNotNull();
  }

  @Test
  void noTokenAndNoClaimReturns401() {
    SseEmitter emitter = controller.stream(new ShortCode("abc"), null, null, response);
    verify(response).setStatus(401);
    assertThat(emitter).isNotNull();
  }

  @Test
  void blankParamsTreatedAsMissing() {
    SseEmitter emitter = controller.stream(new ShortCode("abc"), "", "", response);
    verify(response).setStatus(401);
    assertThat(emitter).isNotNull();
  }

  @Test
  void matchingClaimTokenOnAnonymousLinkConnects() {
    LinkEntity link = new LinkEntity("https://x", "abc", null, null);
    link.setClaimToken("ctok");
    TestEntities.withId(link, 1L);
    when(lookup.findEntity(new ShortCode("abc"))).thenReturn(Optional.of(link));
    when(registry.register(eq(new LinkId(1L)), any(SseEmitter.class))).thenReturn(true);

    SseEmitter out = controller.stream(new ShortCode("abc"), null, "ctok", response);

    assertThat(out).isNotNull();
    assertThat(meterRegistry.counter("sse.click_stream.connected").count()).isEqualTo(1.0);
  }

  @Test
  void wrongClaimTokenReturns401() {
    LinkEntity link = new LinkEntity("https://x", "abc", null, null);
    link.setClaimToken("real-ctok");
    TestEntities.withId(link, 1L);
    when(lookup.findEntity(new ShortCode("abc"))).thenReturn(Optional.of(link));

    SseEmitter emitter = controller.stream(new ShortCode("abc"), null, "wrong-ctok", response);

    verify(response).setStatus(401);
    assertThat(emitter).isNotNull();
  }

  @Test
  void claimTokenOnClaimedLinkReturns401() {
    LinkEntity link = new LinkEntity("https://x", "abc", 7L, null);
    TestEntities.withId(link, 1L);
    when(lookup.findEntity(new ShortCode("abc"))).thenReturn(Optional.of(link));

    SseEmitter emitter = controller.stream(new ShortCode("abc"), null, "ctok", response);

    verify(response).setStatus(401);
    assertThat(emitter).isNotNull();
  }

  @Test
  void claimTokenWhenStoredIsNullReturns401() {
    LinkEntity link = new LinkEntity("https://x", "abc", null, null);
    TestEntities.withId(link, 1L);
    when(lookup.findEntity(new ShortCode("abc"))).thenReturn(Optional.of(link));

    SseEmitter emitter = controller.stream(new ShortCode("abc"), null, "anything", response);

    verify(response).setStatus(401);
    assertThat(emitter).isNotNull();
  }

  @Test
  void streamTokenTakesPrecedenceOverClaimToken() {
    LinkEntity link = new LinkEntity("https://x", "abc", 7L, null);
    TestEntities.withId(link, 1L);
    when(tokens.parseStreamToken("stok", "abc")).thenReturn(7L);
    when(lookup.findEntity(new ShortCode("abc"))).thenReturn(Optional.of(link));
    when(accessGuard.canView(7L, link)).thenReturn(true);
    when(registry.register(eq(new LinkId(1L)), any(SseEmitter.class))).thenReturn(true);

    SseEmitter out = controller.stream(new ShortCode("abc"), "stok", "ignored-claim", response);

    assertThat(out).isNotNull();
    assertThat(meterRegistry.counter("sse.click_stream.connected").count()).isEqualTo(1.0);
  }

  @Test
  void issueStreamTokenChecksAccessAndReturnsScopedToken() {
    LinkEntity link = new LinkEntity("https://x", "abc", 7L, null);
    TestEntities.withId(link, 1L);
    when(lookup.findEntity(new ShortCode("abc"))).thenReturn(Optional.of(link));
    when(accessGuard.canView(7L, link)).thenReturn(true);
    when(tokens.createStreamToken(7L, "abc")).thenReturn("stream-token");

    StreamTokenResponse out = controller.issueStreamToken(new ShortCode("abc"), 7L);

    assertThat(out.streamToken()).isEqualTo("stream-token");
  }

  @Test
  void issueStreamTokenRejectsDeniedAccess() {
    LinkEntity link = new LinkEntity("https://x", "abc", 99L, null);
    TestEntities.withId(link, 1L);
    when(lookup.findEntity(new ShortCode("abc"))).thenReturn(Optional.of(link));
    when(accessGuard.canView(7L, link)).thenReturn(false);

    assertThatThrownBy(() -> controller.issueStreamToken(new ShortCode("abc"), 7L))
        .isInstanceOf(LinkException.class);
  }
}
