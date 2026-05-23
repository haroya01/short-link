package com.example.short_link.link.api.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.LinkAccessGuard;
import com.example.short_link.link.application.LinkNotFoundException;
import com.example.short_link.link.application.LinkNotOwnedException;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.user.application.JwtTokenService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class SseClickStreamControllerTest {

  @Mock private JwtTokenService jwt;
  @Mock private LinkRepository linkRepository;
  @Mock private SseClickStreamRegistry registry;
  @Mock private LinkAccessGuard accessGuard;
  @Mock private HttpServletResponse response;

  private SimpleMeterRegistry meterRegistry;
  private SseClickStreamController controller;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    controller =
        new SseClickStreamController(jwt, linkRepository, registry, meterRegistry, accessGuard);
  }

  @Test
  void invalidTokenReturns401EmitterAndCompletes() {
    when(jwt.parseAccessToken("bad")).thenThrow(new RuntimeException("nope"));
    SseEmitter emitter = controller.stream("abc", "bad", null, response);
    verify(response).setStatus(401);
    assertThat(emitter).isNotNull();
  }

  @Test
  void missingLinkThrowsLinkNotFound() {
    when(linkRepository.findByShortCode("abc")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> controller.stream("abc", "tok", null, response))
        .isInstanceOf(LinkNotFoundException.class);
  }

  @Test
  void deniedAccessThrowsNotOwned() {
    LinkEntity link = new LinkEntity("https://x", "abc", 99L, null);
    writeField(link, "id", 1L);
    when(jwt.parseAccessToken("tok")).thenReturn(7L);
    when(linkRepository.findByShortCode("abc")).thenReturn(Optional.of(link));
    when(accessGuard.canView(7L, link)).thenReturn(false);
    assertThatThrownBy(() -> controller.stream("abc", "tok", null, response))
        .isInstanceOf(LinkNotOwnedException.class);
  }

  @Test
  void registryRejectsReturns429FailFast() {
    LinkEntity link = new LinkEntity("https://x", "abc", 7L, null);
    writeField(link, "id", 1L);
    when(jwt.parseAccessToken("tok")).thenReturn(7L);
    when(linkRepository.findByShortCode("abc")).thenReturn(Optional.of(link));
    when(accessGuard.canView(7L, link)).thenReturn(true);
    when(registry.register(eq(1L), any(SseEmitter.class))).thenReturn(false);
    SseEmitter out = controller.stream("abc", "tok", null, response);
    verify(response).setStatus(429);
    assertThat(out).isNotNull();
  }

  @Test
  void acceptedConnectionSendsReadyAndCounts() {
    LinkEntity link = new LinkEntity("https://x", "abc", 7L, null);
    writeField(link, "id", 1L);
    when(jwt.parseAccessToken("tok")).thenReturn(7L);
    when(linkRepository.findByShortCode("abc")).thenReturn(Optional.of(link));
    when(accessGuard.canView(7L, link)).thenReturn(true);
    when(registry.register(eq(1L), any(SseEmitter.class))).thenReturn(true);
    SseEmitter out = controller.stream("abc", "tok", null, response);
    assertThat(out).isNotNull();
    assertThat(meterRegistry.counter("sse.click_stream.connected").count()).isEqualTo(1.0);
  }

  @Test
  void noTokenAndNoClaimReturns401() {
    SseEmitter emitter = controller.stream("abc", null, null, response);
    verify(response).setStatus(401);
    assertThat(emitter).isNotNull();
  }

  @Test
  void blankParamsTreatedAsMissing() {
    SseEmitter emitter = controller.stream("abc", "  ", "", response);
    verify(response).setStatus(401);
    assertThat(emitter).isNotNull();
  }

  @Test
  void matchingClaimTokenOnAnonymousLinkConnects() {
    LinkEntity link = new LinkEntity("https://x", "abc", null, null);
    link.setClaimToken("ctok");
    writeField(link, "id", 1L);
    when(linkRepository.findByShortCode("abc")).thenReturn(Optional.of(link));
    when(registry.register(eq(1L), any(SseEmitter.class))).thenReturn(true);

    SseEmitter out = controller.stream("abc", null, "ctok", response);

    assertThat(out).isNotNull();
    assertThat(meterRegistry.counter("sse.click_stream.connected").count()).isEqualTo(1.0);
  }

  @Test
  void wrongClaimTokenReturns401() {
    LinkEntity link = new LinkEntity("https://x", "abc", null, null);
    link.setClaimToken("real-ctok");
    writeField(link, "id", 1L);
    when(linkRepository.findByShortCode("abc")).thenReturn(Optional.of(link));

    SseEmitter emitter = controller.stream("abc", null, "wrong-ctok", response);

    verify(response).setStatus(401);
    assertThat(emitter).isNotNull();
  }

  @Test
  void claimTokenOnClaimedLinkReturns401() {
    LinkEntity link = new LinkEntity("https://x", "abc", 7L, null);
    writeField(link, "id", 1L);
    when(linkRepository.findByShortCode("abc")).thenReturn(Optional.of(link));

    SseEmitter emitter = controller.stream("abc", null, "ctok", response);

    verify(response).setStatus(401);
    assertThat(emitter).isNotNull();
  }

  @Test
  void claimTokenWhenStoredIsNullReturns401() {
    LinkEntity link = new LinkEntity("https://x", "abc", null, null);
    writeField(link, "id", 1L);
    when(linkRepository.findByShortCode("abc")).thenReturn(Optional.of(link));

    SseEmitter emitter = controller.stream("abc", null, "anything", response);

    verify(response).setStatus(401);
    assertThat(emitter).isNotNull();
  }

  @Test
  void bothTokensProvidedPrefersJwt() {
    LinkEntity link = new LinkEntity("https://x", "abc", 7L, null);
    writeField(link, "id", 1L);
    when(jwt.parseAccessToken("tok")).thenReturn(7L);
    when(linkRepository.findByShortCode("abc")).thenReturn(Optional.of(link));
    when(accessGuard.canView(7L, link)).thenReturn(true);
    when(registry.register(eq(1L), any(SseEmitter.class))).thenReturn(true);

    SseEmitter out = controller.stream("abc", "tok", "ignored-claim", response);

    assertThat(out).isNotNull();
    assertThat(meterRegistry.counter("sse.click_stream.connected").count()).isEqualTo(1.0);
  }

  private static void writeField(Object target, String name, Object value) {
    try {
      Field f = target.getClass().getDeclaredField(name);
      f.setAccessible(true);
      f.set(target, value);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }
}
