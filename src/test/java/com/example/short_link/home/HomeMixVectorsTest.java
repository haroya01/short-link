package com.example.short_link.home;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class HomeMixVectorsTest {

  static final Path VECTORS = Path.of("src/test/resources/feed-vectors/home-mix.json");
  static final int EVERY = 5;
  static final int TAKE = 2;

  private static JsonNode doc;

  @BeforeAll
  static void read() throws IOException {
    doc = JsonMapper.builder().build().readTree(VECTORS.toFile());
  }

  record Own(String key, String kind, long id) {}

  record Remote(String key, String uri, Long keptNoteId) {}

  record Input(List<Own> own, List<Remote> remote, boolean ownDone, Set<Integer> cards) {}

  static Input input(JsonNode node) {
    List<Own> own = new ArrayList<>();
    for (JsonNode o : node.get("own")) {
      own.add(new Own(o.get("key").asString(), o.get("kind").asString(), o.get("id").asLong()));
    }
    List<Remote> remote = new ArrayList<>();
    for (JsonNode r : node.get("remote")) {
      remote.add(
          new Remote(
              r.get("key").asString(),
              r.get("uri").isNull() ? null : r.get("uri").asString(),
              r.get("keptNoteId").isNull() ? null : r.get("keptNoteId").asLong()));
    }
    Set<Integer> cards = new HashSet<>();
    if (node.has("cards")) {
      node.get("cards").forEach(card -> cards.add(card.asInt()));
    }
    return new Input(own, remote, node.get("ownDone").asBoolean(), cards);
  }

  static List<String> rows(JsonNode expected) {
    List<String> rows = new ArrayList<>();
    expected.forEach(row -> rows.add(row.asString()));
    return rows;
  }

  private static List<Own> uniqueOwn(List<Own> own) {
    Set<String> seen = new HashSet<>();
    return own.stream().filter(o -> seen.add(o.kind() + ":" + o.id())).toList();
  }

  private static List<Remote> candidates(List<Own> own, List<Remote> remote) {
    Set<Long> myNotes = new HashSet<>();
    own.stream().filter(o -> o.kind().equals("note")).forEach(o -> myNotes.add(o.id()));
    Set<String> uris = new HashSet<>();
    Set<Long> kept = new HashSet<>();
    List<Remote> out = new ArrayList<>();
    for (Remote r : remote) {
      if ((r.uri() != null && !uris.add(r.uri()))
          || (r.keptNoteId() != null && !kept.add(r.keptNoteId()))) {
        continue;
      }
      if (r.keptNoteId() == null || !myNotes.contains(r.keptNoteId())) {
        out.add(r);
      }
    }
    return out;
  }

  static List<String> mix(Input in) {
    List<Own> own = uniqueOwn(in.own());
    List<Remote> pool = candidates(own, in.remote());
    List<String> rows = new ArrayList<>();
    int next = 0;
    int slot = EVERY - 1;
    for (int i = 0; i < own.size(); i++) {
      rows.add("own:" + own.get(i).key());
      if (i != slot) {
        continue;
      }
      if (in.cards().contains(i - 1) || in.cards().contains(i) || in.cards().contains(i + 1)) {
        slot = i + 1;
        continue;
      }
      slot = i + EVERY;
      for (int k = 0; k < TAKE && next < pool.size(); k++) {
        rows.add("mixed:" + pool.get(next++).key());
      }
    }
    if (in.ownDone()) {
      while (next < pool.size()) {
        rows.add("tail:" + pool.get(next++).key());
      }
    }
    return rows;
  }

  private static List<JsonNode> states(JsonNode table) {
    List<JsonNode> states = new ArrayList<>();
    for (JsonNode c : table.get("cases")) {
      if (c.has("steps")) {
        c.get("steps").forEach(states::add);
      } else {
        states.add(c);
      }
    }
    return states;
  }

  @Test
  void theFileNamesItsSchemaVersionRulesAndBothTables() {
    assertThat(doc.get("schema").asString()).isEqualTo("kurl.home-mix");
    assertThat(doc.get("version").asInt()).isEqualTo(2);
    assertThat(doc.get("rules")).isNotEmpty();
    assertThat(doc.get("tables").get("base").get("platforms").toString())
        .isEqualTo("[\"ios\",\"web\"]");
    assertThat(doc.get("tables").get("webCards").get("platforms").toString())
        .isEqualTo("[\"web\"]");
    assertThat(states(doc.get("tables").get("base")).size()).isGreaterThanOrEqualTo(100);
  }

  @Test
  void everyCaseHasAUniqueIdANameAnInputAndAnAnswer() {
    Set<String> ids = new HashSet<>();
    for (JsonNode table :
        List.of(doc.get("tables").get("base"), doc.get("tables").get("webCards"))) {
      for (JsonNode c : table.get("cases")) {
        assertThat(ids.add(c.get("id").asString())).as(c.get("id").asString()).isTrue();
        assertThat(c.get("name").asString()).isNotBlank();
        assertThat(c.has("steps") || (c.has("input") && c.has("expected")))
            .as(c.get("id").asString())
            .isTrue();
      }
    }
    for (JsonNode c : doc.get("tables").get("base").get("cases")) {
      JsonNode in = c.has("steps") ? c.get("steps").get(0).get("input") : c.get("input");
      assertThat(in.has("cards")).as(c.get("id").asString()).isFalse();
    }
  }

  @Test
  void everyAnswerIsWhatTheRulesGiveWhenWorkedOutAgainHere() {
    for (JsonNode table :
        List.of(doc.get("tables").get("base"), doc.get("tables").get("webCards"))) {
      for (JsonNode state : states(table)) {
        assertThat(rows(state.get("expected")))
            .as("%.120s", state.get("input"))
            .isEqualTo(mix(input(state.get("input"))));
      }
    }
  }

  @Test
  void everyAnswerKeepsMyRowsInOrderOnceAndTheirsInOrderOnce() {
    for (JsonNode table :
        List.of(doc.get("tables").get("base"), doc.get("tables").get("webCards"))) {
      for (JsonNode state : states(table)) {
        Input in = input(state.get("input"));
        List<String> rows = rows(state.get("expected"));
        List<String> mine =
            rows.stream().filter(r -> r.startsWith("own:")).map(r -> r.substring(4)).toList();
        List<String> theirs =
            rows.stream()
                .filter(r -> !r.startsWith("own:"))
                .map(r -> r.substring(r.indexOf(':') + 1))
                .toList();

        assertThat(mine).isEqualTo(uniqueOwn(in.own()).stream().map(Own::key).toList());
        List<String> pool =
            candidates(uniqueOwn(in.own()), in.remote()).stream().map(Remote::key).toList();
        assertThat(theirs).isEqualTo(pool.subList(0, theirs.size()));
        if (in.ownDone()) {
          assertThat(theirs).hasSameSizeAs(pool);
        } else {
          assertThat(rows).noneMatch(r -> r.startsWith("tail:"));
        }
      }
    }
  }

  @Test
  void withoutCardsAPairOfAtMostTwoFollowsEveryFifthOfMineAndNothingElseIsMixed() {
    for (JsonNode state : states(doc.get("tables").get("base"))) {
      List<String> rows = rows(state.get("expected"));
      int mine = 0;
      int run = 0;
      boolean tail = false;
      for (String row : rows) {
        if (row.startsWith("own:")) {
          assertThat(tail).isFalse();
          mine++;
          run = 0;
        } else if (row.startsWith("mixed:")) {
          run++;
          assertThat(mine % EVERY).as(rows.toString()).isZero();
          assertThat(mine).isPositive();
          assertThat(run).isLessThanOrEqualTo(TAKE);
        } else {
          assertThat(row).startsWith("tail:");
          tail = true;
        }
      }
    }
  }

  @Test
  void aLaterPageOfMineKeepsTheEarlierLayoutAsItsStartWhereTheFileSaysSo() {
    for (JsonNode table :
        List.of(doc.get("tables").get("base"), doc.get("tables").get("webCards"))) {
      for (JsonNode c : table.get("cases")) {
        if (!c.has("steps")) {
          continue;
        }
        JsonNode steps = c.get("steps");
        for (int i = 0; i + 1 < steps.size(); i++) {
          List<String> now = rows(steps.get(i).get("expected"));
          List<String> after = rows(steps.get(i + 1).get("expected"));
          boolean prefix = after.size() >= now.size() && after.subList(0, now.size()).equals(now);
          assertThat(steps.get(i).get("expectedIsPrefixOfNext").asBoolean())
              .as(c.get("id").asString())
              .isEqualTo(prefix);
          Input before = input(steps.get(i).get("input"));
          Input later = input(steps.get(i + 1).get("input"));
          if (before.remote().equals(later.remote())
              && candidates(uniqueOwn(later.own()), later.remote())
                  .containsAll(candidates(uniqueOwn(before.own()), before.remote()))) {
            assertThat(prefix).as(c.get("id").asString()).isTrue();
          }
        }
      }
    }
  }
}
