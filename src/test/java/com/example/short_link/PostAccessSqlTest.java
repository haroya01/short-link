package com.example.short_link;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.post.PostAccessSql;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PostAccessSqlTest {

  private static final Path MAIN = Path.of("src/main/java");

  private static final Pattern POST_VISIBILITY_SQL =
      Pattern.compile(
          "'PUBLISHED'|\\.audience\\s*(=|<>|!=)\\s*'|\\.audience\\s+IN\\s*\\(|blocked_countries\\s+IS\\b",
          Pattern.CASE_INSENSITIVE);

  private static final String POLICY = "com/example/short_link/common/post/PostAccessSql.java";

  private static final Map<String, String> OUTSIDE_PUBLIC_READS =
      Map.of(
          "com/example/short_link/admin/infrastructure/persistence/JpaAdminBlogMetricsRepository.java",
          "operator totals count every published post, whoever may read it");

  @Test
  void onlyThePolicyWritesWhichPostsAListMayCarry() throws IOException {
    List<String> found = new ArrayList<>();
    try (Stream<Path> files = Files.walk(MAIN)) {
      for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
        String name = MAIN.relativize(file).toString().replace('\\', '/');
        if (name.startsWith("db/migration/") || name.equals(POLICY)) continue;
        if (OUTSIDE_PUBLIC_READS.containsKey(name)) continue;
        for (String line : findings(Files.readString(file))) {
          found.add(name + ": " + line.trim());
        }
      }
    }

    assertThat(found)
        .as(
            "Say which posts a list may carry with a PostAccessSql fragment, so audience and "
                + "country blocks travel with the published check")
        .isEmpty();
  }

  @Test
  void theScanCatchesAHandWrittenPredicateAndPassesAFragment() {
    assertThat(findings("\"WHERE p.status = 'PUBLISHED' AND p.user_id = :id\"")).hasSize(1);
    assertThat(findings("\" AND q.audience <> 'UNLISTED'\"")).hasSize(1);
    assertThat(findings("\" AND p.blocked_countries IS NULL\"")).hasSize(1);
    assertThat(findings("\"WHERE \" + PostAccessSql.LISTED + \" AND p.user_id = :id\"")).isEmpty();
    assertThat(findings("\"SELECT p.id, p.audience, p.blocked_countries FROM posts p\"")).isEmpty();
    assertThat(findings("    this.audience = audience;")).isEmpty();
    assertThat(findings("\" AND p.audience IN ('PUBLIC', 'FOLLOWERS')\"")).hasSize(1);
  }

  @Test
  void eachSpelledOutFragmentIsWhatTheAudienceQuestionsBuild() {
    assertThat(PostAccessSql.LISTED).isEqualTo(PostAccessSql.listed("p"));
    assertThat(PostAccessSql.LISTED_AS_Q).isEqualTo(PostAccessSql.listed("q"));
    assertThat(PostAccessSql.OPEN).isEqualTo(PostAccessSql.open("p"));
    assertThat(PostAccessSql.CARD).isEqualTo(PostAccessSql.card("p"));
    assertThat(PostAccessSql.PROFILE).isEqualTo(PostAccessSql.profile("p"));
  }

  @Test
  void aRoomPostIsNeverListedCardedOrOnAProfile() {
    for (String fragment :
        List.of(
            PostAccessSql.LISTED, PostAccessSql.OPEN, PostAccessSql.CARD, PostAccessSql.PROFILE)) {
      assertThat(fragment).doesNotContain("'ROOM'");
    }
    assertThat(PostAccessSql.inRoomsOf("rp"))
        .isEqualTo(
            "rp.status = 'PUBLISHED' AND rp.audience IN ('ROOM') AND EXISTS (SELECT 1 FROM"
                + " room_member pm WHERE pm.room_id = rp.room_id AND pm.user_id = :viewer)");
    assertThat(PostAccessSql.inFeedRooms("p")).contains("NOT fr.hidden_from_feed");
  }

  private static List<String> findings(String source) {
    return source.lines().filter(line -> POST_VISIBILITY_SQL.matcher(line).find()).toList();
  }
}
