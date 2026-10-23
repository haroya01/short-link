package com.example.short_link.home;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.federation.application.trends.RemoteSeen;
import com.example.short_link.federation.domain.SeenItem;
import com.example.short_link.federation.domain.SeenUri;
import com.example.short_link.home.HomeMixVectorsTest.Input;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class SeenRulesVectorsTest {

  static final Path VECTORS = Path.of("src/test/resources/feed-vectors/seen-rules.json");
  static final long KEEP_SECONDS = 14L * 24 * 60 * 60;

  private static JsonNode doc;

  @BeforeAll
  static void read() throws IOException {
    doc = JsonMapper.builder().build().readTree(VECTORS.toFile());
  }

  private static JsonNode cases(String table) {
    return doc.get("tables").get(table).get("cases");
  }

  private static String text(JsonNode node) {
    return node == null || node.isNull() ? null : node.asString();
  }

  @Test
  void theFileNamesItsSchemaVersionRulesAndEveryTable() {
    assertThat(doc.get("schema").asString()).isEqualTo("kurl.seen-rules");
    assertThat(doc.get("version").asInt()).isEqualTo(1);
    assertThat(doc.get("rules")).isNotEmpty();
    assertThat(doc.get("tables").propertyNames())
        .containsExactlyInAnyOrder(
            "keyFormat", "accepted", "exposure", "dwell", "requests", "hide", "cache", "pull");
    Set<String> ids = new HashSet<>();
    for (JsonNode table : doc.get("tables").values()) {
      assertThat(table.get("platforms")).isNotEmpty();
      for (JsonNode c : table.get("cases")) {
        assertThat(ids.add(c.get("id").asString())).as(c.get("id").asString()).isTrue();
      }
    }
  }

  @Test
  void whatTheServerTakesIsWhatTheFileSaysItTakes() {
    for (JsonNode c : cases("accepted")) {
      String sent = text(c.get("sent"));
      Optional<SeenItem> local = SeenItem.sent(sent);
      Optional<SeenUri> remote = sent == null ? Optional.empty() : SeenUri.sent(sent);
      switch (c.get("as").asString()) {
        case "local" -> {
          int kind = c.get("kind").asString().equals("note") ? SeenItem.NOTE : SeenItem.POST;
          assertThat(local)
              .as(c.get("id").asString())
              .contains(new SeenItem(kind, Long.parseLong(c.get("itemId").asString())));
          assertThat(remote).as(c.get("id").asString()).isEmpty();
        }
        case "remote" -> {
          assertThat(local).as(c.get("id").asString()).isEmpty();
          assertThat(remote).as(c.get("id").asString()).isPresent();
        }
        default -> {
          assertThat(c.get("as").asString()).isEqualTo("dropped");
          assertThat(local).as(c.get("id").asString()).isEmpty();
          assertThat(remote).as(c.get("id").asString()).isEmpty();
        }
      }
    }
  }

  @Test
  void everyKeyAClientIsToldToSendIsOneTheServerTakesAsTheSameRow() {
    for (JsonNode c : cases("keyFormat")) {
      JsonNode row = c.get("row");
      String key = text(c.get("key"));
      switch (row.get("kind").asString()) {
        case "note", "post" -> {
          assertThat(key)
              .isEqualTo("kurl:" + row.get("kind").asString() + ":" + row.get("id").asString());
          assertThat(SeenItem.sent(key).map(SeenItem::id))
              .contains(Long.parseLong(row.get("id").asString()));
        }
        default -> {
          assertThat(key).isEqualTo(text(row.get("uri")));
          if (key != null) {
            assertThat(SeenUri.sent(key)).isPresent();
          }
        }
      }
    }
  }

  @Test
  void dwellIsKeptOnlyForRemoteUrisFromTheServersFloorAndCutAtItsCap() {
    for (JsonNode c : cases("dwell")) {
      String key = c.get("key").asString();
      long total = c.get("totalMs").asLong();
      Long expected =
          SeenUri.sent(key).isEmpty() || total < RemoteSeen.MIN_DWELL_MS
              ? null
              : Math.min(total, RemoteSeen.MAX_DWELL_MS);
      JsonNode sent = c.get("sentMs");
      assertThat(sent.isNull() ? null : sent.asLong())
          .as(c.get("id").asString())
          .isEqualTo(expected);
    }
  }

  @Test
  void exposureFollowsTheSurfaceRule() {
    for (JsonNode c : cases("exposure")) {
      String surface = c.get("surface").asString();
      double ratio = c.get("visibleRatio").asDouble();
      long ms = c.get("visibleMs").asLong();
      boolean counted =
          c.get("appActive").asBoolean()
              && switch (surface) {
                case "home-latest" -> ratio > 0;
                case "explore-remote" -> ratio >= 0.5 && ms >= 1_000;
                default -> false;
              };
      assertThat(c.get("counted").asBoolean()).as(c.get("id").asString()).isEqualTo(counted);
    }
  }

  @Test
  void onlyHomeLatestAndTheForYouMixedIntoItAskToHideAndEveryRequestNamesTheDevice() {
    for (JsonNode c : cases("requests")) {
      assertThat(c.get("seenHide").asBoolean())
          .as(c.get("id").asString())
          .isEqualTo(c.get("surface").asString().equals("home-latest"));
      assertThat(c.get("deviceHeader").asBoolean()).isTrue();
    }
  }

  @Test
  void theServerHidesOnlyTheSameViewersRecordFromBeforeAsOfOnHiddenLatestAndNeverTheirOwn() {
    for (JsonNode c : cases("hide")) {
      JsonNode viewer = c.get("viewer");
      JsonNode request = c.get("request");
      JsonNode record = c.get("record");
      String cursor = request.get("cursor").asString();
      assertThat(cursor).isIn("none", "h", "h-malformed", "old");
      boolean hiding =
          !viewer.get("kind").asString().equals("none")
              && request.get("tab").asString().equals("latest")
              && (cursor.startsWith("h")
                  || (cursor.equals("none") && request.get("seenHide").asBoolean()));
      boolean recorded =
          record != null
              && !record.isNull()
              && record.get("viewer").equals(viewer)
              && record.get("item").asString().equals(c.get("item").get("ref").asString())
              && record.get("receivedBeforeAsOf").asBoolean();
      boolean own =
          viewer.get("kind").asString().equals("member")
              && c.get("item").get("authorId").asLong() == viewer.get("id").asLong();
      assertThat(c.get("shown").asBoolean())
          .as(c.get("id").asString())
          .isEqualTo(!(hiding && recorded && !own));
    }
  }

  @Test
  void theCachedFirstPageLeavesOutWhatWasSeenWithinFourteenDaysExceptMine() {
    for (JsonNode c : cases("cache")) {
      JsonNode ago = c.get("seenSecondsAgo");
      boolean leftOut =
          !ago.isNull() && ago.asLong() < KEEP_SECONDS && !c.get("authorIsMe").asBoolean();
      assertThat(c.get("leftOut").asBoolean()).as(c.get("id").asString()).isEqualTo(leftOut);
    }
  }

  private static Set<String> keys(List<String> rows) {
    Set<String> keys = new HashSet<>();
    rows.forEach(row -> keys.add(row.substring(row.indexOf(':') + 1)));
    return keys;
  }

  private static String notice(JsonNode c) {
    JsonNode own = c.get("own");
    JsonNode remote = c.get("remote");
    if (own.get("result").asString().equals("failed")) {
      return "failed";
    }
    boolean remoteOk = remote.get("result").asString().equals("ok");
    State after =
        new State(own.get("items"), remoteOk ? remote.get("items") : null, own.get("done"));
    List<String> rows = HomeMixVectorsTest.mix(after.input());
    if (rows.isEmpty()) {
      return remoteOk ? "caughtUp" : "failed";
    }
    JsonNode before = c.get("before");
    Set<String> shown =
        keys(
            HomeMixVectorsTest.mix(
                new State(before.get("own"), before.get("remote"), before.get("ownDone")).input()));
    return keys(rows).stream().anyMatch(key -> !shown.contains(key)) ? "newRows" : "nothingNew";
  }

  private record State(JsonNode own, JsonNode remote, JsonNode done) {

    Input input() {
      ObjectNode node = JsonMapper.builder().build().createObjectNode();
      node.set("own", own);
      node.set("remote", remote == null ? node.arrayNode() : remote);
      node.set("ownDone", done);
      return HomeMixVectorsTest.input(node);
    }
  }

  @Test
  void everyPullEndsInExactlyTheNoticeTheRulesGive() {
    for (JsonNode c : cases("pull")) {
      assertThat(c.get("notice").asString())
          .as(c.get("id").asString())
          .isIn("newRows", "nothingNew", "caughtUp", "failed")
          .isEqualTo(notice(c));
    }
  }
}
