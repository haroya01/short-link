package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 피드 썸네일은 작성자가 고른 표지만 쓴다. 출처를 기록한 적이 없으니, 표지가 본문의 첫 이미지와 같으면 자동으로
// 채운 표지로 보고 그 밖의 표지만 고른 것으로 둔다. 첫 이미지는 IMAGE 블록과 문단 안의 마크다운 이미지를 순서대로 본다.
public class V182__post_cover_chosen extends BaseJavaMigration {

  private static final int BATCH = 500;
  private static final Pattern MARKDOWN_IMAGE =
      Pattern.compile("!\\[[^\\]]*\\]\\(\\s*<?([^)\\s>]+)");
  private static final Set<String> TEXT_BLOCKS =
      Set.of("PARAGRAPH", "H1", "H2", "H3", "QUOTE", "LIST_BULLET", "LIST_NUMBERED");

  @Override
  public void migrate(Context context) throws Exception {
    Connection conn = context.getConnection();
    try (Statement st = conn.createStatement()) {
      st.execute(
          "ALTER TABLE posts ADD COLUMN cover_chosen BOOLEAN NOT NULL DEFAULT FALSE"
              + " AFTER og_image_key");
    }
    backfill(conn);
  }

  static void backfill(Connection conn) throws Exception {
    Map<Long, String> covers = covers(conn);
    Map<Long, String> firstImages = firstImages(conn);
    List<Long> chosen = new ArrayList<>();
    covers.forEach(
        (postId, cover) -> {
          if (!cover.equals(firstImages.get(postId))) {
            chosen.add(postId);
          }
        });
    markChosen(conn, chosen);
  }

  private static Map<Long, String> covers(Connection conn) throws Exception {
    Map<Long, String> covers = new HashMap<>();
    try (Statement st = conn.createStatement();
        ResultSet rs =
            st.executeQuery("SELECT id, og_image_url FROM posts WHERE og_image_url IS NOT NULL")) {
      while (rs.next()) {
        covers.put(rs.getLong("id"), rs.getString("og_image_url"));
      }
    }
    return covers;
  }

  private static Map<Long, String> firstImages(Connection conn) throws Exception {
    JsonMapper json = JsonMapper.builder().build();
    Map<Long, String> first = new HashMap<>();
    try (Statement st = conn.createStatement();
        ResultSet rs =
            st.executeQuery(
                "SELECT b.post_id, b.block_type, b.content FROM post_block b"
                    + " JOIN posts p ON p.id = b.post_id WHERE p.og_image_url IS NOT NULL"
                    + " ORDER BY b.post_id, b.block_order")) {
      while (rs.next()) {
        long postId = rs.getLong("post_id");
        if (first.containsKey(postId)) {
          continue;
        }
        String url = imageOf(json, rs.getString("block_type"), rs.getString("content"));
        if (url != null) {
          first.put(postId, url);
        }
      }
    }
    return first;
  }

  static String imageOf(JsonMapper json, String type, String content) {
    if (content == null) {
      return null;
    }
    if ("IMAGE".equals(type)) {
      try {
        JsonNode url = json.readTree(content).get("url");
        return url == null || url.asString().isBlank() ? null : url.asString();
      } catch (RuntimeException unreadable) {
        return null;
      }
    }
    if (!TEXT_BLOCKS.contains(type)) {
      return null;
    }
    Matcher m = MARKDOWN_IMAGE.matcher(content);
    return m.find() ? m.group(1) : null;
  }

  private static void markChosen(Connection conn, List<Long> postIds) throws Exception {
    try (PreparedStatement update =
        conn.prepareStatement("UPDATE posts SET cover_chosen = TRUE WHERE id = ?")) {
      int inBatch = 0;
      for (Long postId : postIds) {
        update.setLong(1, postId);
        update.addBatch();
        if (++inBatch == BATCH) {
          update.executeBatch();
          inBatch = 0;
        }
      }
      if (inBatch > 0) {
        update.executeBatch();
      }
    }
  }
}
