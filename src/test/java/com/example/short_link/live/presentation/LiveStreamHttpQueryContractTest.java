package com.example.short_link.live.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.testsupport.OperationalHttpJourneySupport;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;

class LiveStreamHttpQueryContractTest extends OperationalHttpJourneySupport {

  @LocalServerPort private int port;

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  @Test
  void aReaderWatchesAThreadACommentListAndARoomUntilTheyLeave() throws Exception {
    Actor owner = actor("live-owner", false);
    Actor member = actor("live-member", false);
    Actor stranger = actor("live-stranger", false);

    long roomId =
        setup("POST", "/api/v1/rooms", owner, Map.of("name", "실시간 방")).path("id").asLong();
    String code =
        setup("POST", "/api/v1/rooms/" + roomId + "/invites", owner, Map.of())
            .path("code")
            .asText();
    setup("POST", "/api/v1/room-invites/" + code + "/join", member, null);
    long noteId =
        setup("POST", "/api/v1/notes", owner, Map.of("body", "물어볼게요")).path("id").asLong();
    long postId =
        setup("POST", "/api/v1/posts", owner, Map.of("slug", "live-post", "title", "실시간 글"))
            .path("id")
            .asLong();
    setup("POST", "/api/v1/posts/" + postId + "/publish", owner, null);

    String notePath = "/api/v1/live/notes/" + noteId;
    String commentsPath = "/api/v1/live/posts/" + postId + "/comments";
    String roomPath = "/api/v1/live/rooms/" + roomId;

    String noteToken =
        step("live-note-stream-token", "POST", notePath + "/stream-token", member, null, 200)
            .path("streamToken")
            .asText();
    String commentsToken =
        step(
                "live-comments-stream-token",
                "POST",
                commentsPath + "/stream-token",
                member,
                null,
                200)
            .path("streamToken")
            .asText();
    String roomToken =
        step("live-room-stream-token", "POST", roomPath + "/stream-token", member, null, 200)
            .path("streamToken")
            .asText();

    JsonNode hidden =
        step(
            "live-room-stream-token-hidden",
            "POST",
            roomPath + "/stream-token",
            stranger,
            null,
            404);
    JsonNode missing =
        step(
            "live-note-stream-token-missing",
            "POST",
            "/api/v1/live/notes/" + (noteId + 1_000_000) + "/stream-token",
            member,
            null,
            404);
    assertThat(hidden.path("code").asText()).isEqualTo("LIVE_SCOPE_NOT_FOUND");
    assertThat(missing.path("code").asText()).isEqualTo(hidden.path("code").asText());
    assertThat(
            capturedStatus(
                "live-room-stream-unauthorized", roomPath + "/stream?streamToken=" + noteToken))
        .isEqualTo(401);

    try (EventStream thread =
            open("live-note-stream", notePath + "/stream?streamToken=" + noteToken);
        EventStream comments =
            open("live-comments-stream", commentsPath + "/stream?streamToken=" + commentsToken);
        EventStream room =
            open("live-room-stream", roomPath + "/stream?streamToken=" + roomToken)) {
      long replyId =
          setup("POST", "/api/v1/notes", stranger, Map.of("body", "답이에요", "inReplyToId", noteId))
              .path("id")
              .asLong();
      long commentId =
          setup("POST", "/api/v1/posts/" + postId + "/comments", owner, Map.of("body", "첫 댓글"))
              .path("id")
              .asLong();
      long roomNoteId =
          setup("POST", "/api/v1/notes", owner, Map.of("body", "방 소식", "roomId", roomId))
              .path("id")
              .asLong();

      assertChanged(thread.awaitEvent("changed"), "note", noteId, "reply", replyId);
      assertChanged(comments.awaitEvent("changed"), "post", postId, "comment", commentId);
      assertChanged(room.awaitEvent("changed"), "room", roomId, "note", roomNoteId);

      JsonNode newReplies =
          step(
              "live-note-replies-after",
              "GET",
              "/api/v1/public/notes/" + noteId + "/replies?after=" + thread.cursor(),
              member,
              null,
              200);
      assertThat(newReplies.path("items").get(0).path("id").asLong()).isEqualTo(replyId);
      assertThat(newReplies.path("items")).hasSize(1);
      assertThat(newReplies.path("latest").asText()).isEqualTo(String.valueOf(replyId));
      JsonNode newComments =
          step(
              "live-comments-after",
              "GET",
              "/api/v1/public/posts/" + postId + "/comments/page?after=" + comments.cursor(),
              member,
              null,
              200);
      assertThat(newComments.path("items").get(0).path("id").asLong()).isEqualTo(commentId);
      assertThat(newComments.path("next").isNull()).isTrue();
      JsonNode firstPage =
          step(
              "live-comments-first-page",
              "GET",
              "/api/v1/public/posts/" + postId + "/comments/page?limit=20",
              member,
              null,
              200);
      assertThat(firstPage.path("latest").asText()).isEqualTo(String.valueOf(commentId));
      JsonNode newRoomNotes =
          step(
              "live-room-notes-after",
              "GET",
              "/api/v1/rooms/" + roomId + "/notes?after=" + room.cursor(),
              member,
              null,
              200);
      assertThat(newRoomNotes.path("items").get(0).path("id").asLong()).isEqualTo(roomNoteId);
      assertThat(newRoomNotes.path("items")).hasSize(1);

      setup("POST", "/api/v1/rooms/" + roomId + "/leave", member, null);
      JsonNode revoked = json.readTree(room.awaitEvent("revoked"));
      assertThat(revoked.path("scope").asText()).isEqualTo("room");
      assertThat(revoked.path("scopeId").asLong()).isEqualTo(roomId);
    }

    String again =
        step("live-note-stream-token-again", "POST", notePath + "/stream-token", member, null, 200)
            .path("streamToken")
            .asText();
    String renewed;
    try (EventStream behind =
        open("live-note-stream-since", notePath + "/stream?streamToken=" + again + "&since=0")) {
      JsonNode changed = json.readTree(behind.awaitEvent("changed"));
      assertThat(changed.path("more").asBoolean()).isTrue();
      assertThat(changed.path("items")).isEmpty();
      renewed = behind.ready().path("streamToken").asText();
      assertThat(behind.ready().path("retryMs").asLong()).isBetween(1_000L, 5_000L);
      assertThat(behind.retry()).isBetween(1_000L, 5_000L);
    }
    try (EventStream reused =
        open(
            "live-note-stream-reconnect",
            notePath + "/stream?streamToken=" + renewed + "&since=" + replyIdOf(noteId))) {
      assertThat(reused.ready().path("cursor").asText()).isEqualTo(replyIdOf(noteId));
    }
  }

  private String replyIdOf(long noteId) {
    return String.valueOf(
        jdbc.queryForObject(
            "SELECT MAX(id) FROM note WHERE in_reply_to_id = ?", Long.class, noteId));
  }

  private static void assertChanged(String raw, String scope, long scopeId, String kind, long id)
      throws Exception {
    JsonNode changed = tools.jackson.databind.json.JsonMapper.builder().build().readTree(raw);
    assertThat(changed.path("scope").asText()).isEqualTo(scope);
    assertThat(changed.path("scopeId").asLong()).isEqualTo(scopeId);
    assertThat(changed.path("items").get(0).path("kind").asText()).isEqualTo(kind);
    assertThat(changed.path("items").get(0).path("id").asLong()).isEqualTo(id);
    assertThat(changed.path("cursor").asText()).isEqualTo(String.valueOf(id));
  }

  private JsonNode setup(String method, String path, Actor actor, Object body) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer " + actor.token());
    HttpRequest.BodyPublisher payload = HttpRequest.BodyPublishers.noBody();
    if (body != null) {
      request.header("Content-Type", "application/json");
      payload = HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
    }
    HttpResponse<String> response =
        http.send(request.method(method, payload).build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode())
        .as("%s %s: %s", method, path, response.body())
        .isLessThan(300);
    awaitAsyncWork();
    return response.body().isBlank() ? json.nullNode() : json.readTree(response.body());
  }

  private int capturedStatus(String contractId, String path) throws Exception {
    var captured =
        contracts.capture(
            contractId,
            () ->
                http.send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build(),
                        HttpResponse.BodyHandlers.discarding())
                    .statusCode());
    contracts.verify(captured);
    return captured.response();
  }

  private EventStream open(String contractId, String path) throws Exception {
    var captured =
        contracts.capture(
            contractId,
            () -> {
              HttpResponse<InputStream> response =
                  http.send(
                      HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                          .timeout(Duration.ofSeconds(10))
                          .header("X-Query-Contract-ID", contractId)
                          .GET()
                          .build(),
                      HttpResponse.BodyHandlers.ofInputStream());
              EventStream stream = new EventStream(response.body());
              try {
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.headers().firstValue("X-Accel-Buffering")).contains("no");
                JsonNode ready = json.readTree(stream.awaitEvent("ready"));
                assertThat(ready.path("cursor").isString()).isTrue();
                stream.ready = ready;
                return stream;
              } catch (Exception | AssertionError failure) {
                stream.close();
                throw failure;
              }
            });
    contracts.verify(captured);
    return captured.response();
  }

  private static final class EventStream implements AutoCloseable {
    private final InputStream body;
    private final BufferedReader reader;
    private JsonNode ready;
    private long retry = -1;

    private EventStream(InputStream body) {
      this.body = body;
      this.reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
    }

    private String awaitEvent(String expectedName) throws Exception {
      FutureTask<String> pending = new FutureTask<>(() -> readEvent(expectedName));
      Thread.startVirtualThread(pending);
      try {
        return pending.get(10, TimeUnit.SECONDS);
      } catch (TimeoutException timeout) {
        body.close();
        pending.cancel(true);
        throw new AssertionError(
            "SSE did not deliver the " + expectedName + " event within ten seconds", timeout);
      }
    }

    private String readEvent(String expectedName) throws IOException {
      String name = null;
      StringBuilder data = new StringBuilder();
      for (String line; (line = reader.readLine()) != null; ) {
        if (line.isEmpty()) {
          if (name == null && data.isEmpty()) continue;
          assertThat(name).isEqualTo(expectedName);
          return data.toString();
        }
        if (line.startsWith("event:")) name = line.substring("event:".length()).stripLeading();
        if (line.startsWith("retry:"))
          retry = Long.parseLong(line.substring("retry:".length()).strip());
        if (line.startsWith("data:")) {
          if (!data.isEmpty()) data.append('\n');
          data.append(line.substring("data:".length()).stripLeading());
        }
      }
      throw new AssertionError("SSE closed before delivering the " + expectedName + " event");
    }

    private JsonNode ready() {
      return ready;
    }

    private long retry() {
      return retry;
    }

    private String cursor() {
      return ready.path("cursor").asText();
    }

    @Override
    public void close() throws IOException {
      body.close();
    }
  }
}
