package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.signature.Signer;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.repository.FederationDomainBlockRepository;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
