package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.short_link.federation.application.signature.Signer;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.repository.FederationDomainBlockRepository;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RemoteDocumentsTest {

  private static final URI ALICE = URI.create("https://mastodon.example/users/alice");

  @Mock private FederationHttp http;
  @Mock private SigningKeys signingKeys;
  @Mock private FederationDomainBlockRepository serverBlocks;

  private final Signer instance = new Signer("https://kurl.me/ap/instance#main-key", null);

  @BeforeEach
  void instanceSigner() {
    lenient().when(signingKeys.forInstance()).thenReturn(instance);
  }

  private RemoteDocuments documents() {
    return new RemoteDocuments(http, signingKeys, serverBlocks, JsonMapper.builder().build());
  }

  private static FederationHttp.Result ok(String body) {
    return new FederationHttp.Result.Ok(200, body.getBytes(StandardCharsets.UTF_8));
  }

  private static FederationErrorCode codeOf(Runnable fetch) {
    try {
      fetch.run();
    } catch (FederationException e) {
      return e.errorCode();
    }
    throw new AssertionError("expected a FederationException");
  }

  @Test
  void anObjectTheServerAnsweredWithIsTheDocument() {
    when(http.get(ALICE, instance)).thenReturn(ok("{\"type\":\"Person\"}"));

    assertThat(documents().fetch(ALICE, "@alice@mastodon.example").path("type").asString())
        .isEqualTo("Person");
  }

  @Test
  void aSuspendedServerIsNeverAsked() {
    when(serverBlocks.suspends("mastodon.example")).thenReturn(true);

    assertThatThrownBy(() -> documents().fetch(ALICE, "@alice@mastodon.example"))
        .isInstanceOfSatisfying(
            FederationException.class,
            e -> assertThat(e.errorCode()).isEqualTo(FederationErrorCode.REMOTE_ACCOUNT_NOT_FOUND));
    verifyNoInteractions(http);
  }

  @Test
  void anAnswerWithoutTheThingIsNotFound() {
    when(http.get(ALICE, instance))
        .thenReturn(
            ok("null"),
            ok("[1]"),
            ok("{not json"),
            new FederationHttp.Result.Failed(404, "HTTP 404"),
            new FederationHttp.Result.Failed(410, "HTTP 410"),
            new FederationHttp.Result.Failed(401, "HTTP 401"),
            new FederationHttp.Result.Refused("not a public address"));

    RemoteDocuments documents = documents();
    for (int i = 0; i < 7; i++) {
      assertThat(codeOf(() -> documents.fetch(ALICE, "x")))
          .isEqualTo(FederationErrorCode.REMOTE_ACCOUNT_NOT_FOUND);
    }
  }

  @Test
  void webFingerAsksTheSuspensionOfEveryHostItIsSentTo() {
    URI webFinger =
        URI.create("https://example.com/.well-known/webfinger?resource=acct%3Aalice%40example.com");
    when(serverBlocks.suspends(any()))
        .thenAnswer(call -> "social.example.com".equals(call.getArgument(0)));
    when(http.webFinger(eq(webFinger), eq(instance), any()))
        .thenAnswer(
            call -> {
              Predicate<String> allowed = call.getArgument(2);
              assertThat(allowed.test("example.com")).isTrue();
              assertThat(allowed.test("social.example.com")).isFalse();
              return ok("{\"subject\":\"acct:alice@example.com\"}");
            });

    assertThat(documents().webFinger(webFinger, "alice@example.com").path("subject").asString())
        .isEqualTo("acct:alice@example.com");
  }

  @Test
  void aServerThatDeniesUsIsAWarningNamingItsHost() {
    Logger logger = (Logger) LoggerFactory.getLogger(RemoteDocuments.class);
    ListAppender<ILoggingEvent> logged = new ListAppender<>();
    logged.start();
    logger.addAppender(logged);
    when(http.get(ALICE, instance))
        .thenReturn(
            new FederationHttp.Result.Failed(401, "HTTP 401"),
            new FederationHttp.Result.Failed(403, "HTTP 403"),
            new FederationHttp.Result.Failed(404, "HTTP 404"));
    try {
      RemoteDocuments documents = documents();
      for (int i = 0; i < 3; i++) {
        assertThat(codeOf(() -> documents.fetch(ALICE, "x")))
            .isEqualTo(FederationErrorCode.REMOTE_ACCOUNT_NOT_FOUND);
      }
    } finally {
      logger.detachAppender(logged);
    }

    assertThat(logged.list)
        .extracting(ILoggingEvent::getLevel, ILoggingEvent::getFormattedMessage)
        .containsExactly(
            tuple(Level.WARN, "federation resolve denied status=401 host=mastodon.example"),
            tuple(Level.WARN, "federation resolve denied status=403 host=mastodon.example"),
            tuple(
                Level.INFO,
                "federation resolve outcome=Failed[status=404, reason=HTTP 404] uri=" + ALICE));
  }

  @Test
  void noAnswerAnErrorOfItsOwnOrARateLimitIsAServerThatDidNotAnswer() {
    when(http.get(ALICE, instance))
        .thenReturn(
            new FederationHttp.Result.Unreachable("SocketTimeoutException"),
            new FederationHttp.Result.Failed(500, "HTTP 500"),
            new FederationHttp.Result.Failed(503, "HTTP 503"),
            new FederationHttp.Result.Failed(429, "HTTP 429"));

    RemoteDocuments documents = documents();
    for (int i = 0; i < 4; i++) {
      assertThat(codeOf(() -> documents.fetch(ALICE, "x")))
          .isEqualTo(FederationErrorCode.REMOTE_SERVER_UNREACHABLE);
    }
  }
}
