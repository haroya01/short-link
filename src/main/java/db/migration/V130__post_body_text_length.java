package db.migration;

import com.example.short_link.post.application.write.PostSearchTextFlattener;
import com.example.short_link.post.domain.DiscoveryQuality;
import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostBlockType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import tools.jackson.databind.json.JsonMapper;

// 발견 품질 하한선이 읽는 본문 글자 수. 앱이 쓸 때 재는 값과 같도록 같은 변환기(bodyText)와 같은 셈으로 채운다.
public class V130__post_body_text_length extends BaseJavaMigration {

  private static final int BATCH = 500;

  @Override
  public void migrate(Context context) throws Exception {
    Connection conn = context.getConnection();
    if (!columnExists(conn)) {
      try (Statement st = conn.createStatement()) {
        st.execute("ALTER TABLE posts ADD COLUMN body_text_length INT NOT NULL DEFAULT 0");
      }
    }
    backfill(conn);
  }

  private boolean columnExists(Connection conn) throws Exception {
    try (PreparedStatement ps =
        conn.prepareStatement(
            "SELECT 1 FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'posts' "
                + "AND column_name = 'body_text_length'")) {
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next();
      }
    }
  }

  private void backfill(Connection conn) throws Exception {
    PostSearchTextFlattener flattener = new PostSearchTextFlattener(JsonMapper.builder().build());
    Map<Long, List<PostBlockEntity>> blocksByPost = blocksByPost(conn);
    try (PreparedStatement update =
        conn.prepareStatement("UPDATE posts SET body_text_length = ? WHERE id = ?")) {
      int inBatch = 0;
      for (Map.Entry<Long, List<PostBlockEntity>> entry : blocksByPost.entrySet()) {
        update.setInt(1, DiscoveryQuality.meaningfulLength(flattener.bodyText(entry.getValue())));
        update.setLong(2, entry.getKey());
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

  private Map<Long, List<PostBlockEntity>> blocksByPost(Connection conn) throws Exception {
    Map<Long, List<PostBlockEntity>> byPost = new HashMap<>();
    try (Statement st = conn.createStatement();
        ResultSet rs =
            st.executeQuery(
                "SELECT post_id, block_type, content, block_order FROM post_block "
                    + "ORDER BY post_id, block_order")) {
      while (rs.next()) {
        // enum 밖 legacy block_type 행은 건너뛴다 — 글자 수에 기여하지 않을 뿐 백필은 계속된다.
        PostBlockType type = parseBlockType(rs.getString("block_type"));
        if (type == null) {
          continue;
        }
        long postId = rs.getLong("post_id");
        byPost
            .computeIfAbsent(postId, k -> new ArrayList<>())
            .add(
                new PostBlockEntity(
                    postId, type, rs.getString("content"), rs.getInt("block_order")));
      }
    }
    return byPost;
  }

  private static PostBlockType parseBlockType(String raw) {
    if (raw == null) {
      return null;
    }
    try {
      return PostBlockType.valueOf(raw);
    } catch (IllegalArgumentException unknown) {
      return null;
    }
  }
}
