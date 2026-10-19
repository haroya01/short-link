package com.example.short_link;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.note.NoteAccessSql;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class NoteAccessSqlTest {

  private static final Path MAIN = Path.of("src/main/java");

  private static final Pattern VISIBILITY_SQL =
      Pattern.compile(
          "\\.visibility\\s*(=|<>|!=)\\s*'|\\.visibility\\s+(NOT\\s+)?IN\\s*\\(",
          Pattern.CASE_INSENSITIVE);

  private static final String POLICY = "com/example/short_link/common/note/NoteAccessSql.java";

  private static final Map<String, String> NOT_A_NOTE =
      Map.of(
          "com/example/short_link/post/collection/infrastructure/CollectionConnectionRepositoryAdapter.java",
          "a collection's own visibility",
          "com/example/short_link/post/collection/infrastructure/JpaCollectionConnectionRepository.java",
          "a collection's own visibility");

  @Test
  void onlyThePolicyWritesWhichNotesAQueryMayCarry() throws IOException {
    List<String> found = new ArrayList<>();
    try (Stream<Path> files = Files.walk(MAIN)) {
      for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
        String name = MAIN.relativize(file).toString().replace('\\', '/');
        if (name.equals(POLICY) || NOT_A_NOTE.containsKey(name)) continue;
        for (String line : findings(Files.readString(file))) {
          found.add(name + ": " + line.trim());
        }
      }
    }

    assertThat(found)
        .as(
            "Say which notes a query may carry with a NoteAccessSql fragment, so a new boundary "
                + "reaches every list at once")
        .isEmpty();
  }

  @Test
  void theScanCatchesAHandWrittenPredicateAndPassesAFragment() {
    assertThat(findings("\" AND n.visibility = 'PUBLIC'\"")).hasSize(1);
    assertThat(findings("\" AND (o.visibility <> 'DIRECT' OR o.user_id = :viewer\"")).hasSize(1);
    assertThat(findings("\" AND s.visibility IN ('PUBLIC', 'UNLISTED')\"")).hasSize(1);
    assertThat(findings("\" AND n.visibility NOT IN ('DIRECT')\"")).hasSize(1);
    assertThat(findings("\" AND \" + NoteAccessSql.listed(\"n\")")).isEmpty();
    assertThat(findings("\" content_warning, marked_sensitive, visibility, in_reply_to_id,\""))
        .isEmpty();
    assertThat(findings("    this.visibility = visibility;")).isEmpty();
  }

  @Test
  void eachFragmentAsksTheEnumUnderTheGivenAlias() {
    assertThat(NoteAccessSql.listed("gn")).isEqualTo("gn.visibility IN ('PUBLIC')");
    assertThat(NoteAccessSql.shareable("s")).isEqualTo("s.visibility IN ('PUBLIC', 'UNLISTED')");
    assertThat(NoteAccessSql.direct("d")).isEqualTo("d.visibility IN ('DIRECT')");
    assertThat(NoteAccessSql.readable("o"))
        .startsWith(
            "(o.visibility IN ('PUBLIC', 'UNLISTED')"
                + " OR (o.visibility IN ('PRIVATE', 'DIRECT') AND o.user_id = :viewer)")
        .contains("(o.visibility IN ('PRIVATE') AND (EXISTS (SELECT 1 FROM user_follow")
        .doesNotContain(" n.");
  }

  private static List<String> findings(String source) {
    return source.lines().filter(line -> VISIBILITY_SQL.matcher(line).find()).toList();
  }
}
