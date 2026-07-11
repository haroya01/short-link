package db.migration;

import com.example.short_link.post.application.write.PostSearchTextFlattener;
import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostBlockType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import tools.jackson.databind.json.JsonMapper;

/**
 * 검색 엔진 업그레이드 — posts 에 파생 검색 평문(search_text) 컬럼과 FULLTEXT(ngram) 인덱스를 얹는다.
 *
 * <p>예전 검색은 title·excerpt·태그·작가 핸들만 LIKE '%q%' 로 훑어 본문(post_block)이 통째로 빠졌다. search_text 는
 * 제목·요약·태그·본문 블록을 사람이 읽는 자연어로 펼쳐 담아, ngram FULLTEXT 한 컬럼이 본문까지 부분일치로 검색되게 한다(한글은 token size 2 로 두
 * 글자부터 매칭).
 *
 * <p>순서가 중요하다: (1) 컬럼 추가 → (2) 기존 발행글 백필 → (3) FULLTEXT 인덱스 생성. 인덱스를 마지막에 만들어 채워진 데이터 위에서 한 번에
 * 빌드하고, 백필 UPDATE 마다 인덱스가 재구성되는 것을 피한다. 백필은 블록 payload(타입별 JSON/평문)를 파싱해야 해서 순수 SQL 로는 불가능 — 앱의
 * {@link PostSearchTextFlattener} 를 그대로 재사용해 런타임 쓰기 경로와 펼치는 규칙을 단일화한다.
 *
 * <p>발행글만이 아니라 모든 상태(초안 포함)를 백필한다: 초안도 발행되면 즉시 검색 대상이 되고, 컬럼이 비어 있으면 그때까지 본문 검색에서 누락되기 때문이다(발행 자체는
 * 블록을 다시 쓰지 않는다).
 */
public class V107__post_search_text_fulltext extends BaseJavaMigration {

  private static final int BATCH = 500;

  @Override
  public void migrate(Context context) throws Exception {
    Connection conn = context.getConnection();
    addColumn(conn);
    backfill(conn);
    addFulltextIndex(conn);
  }

  private void addColumn(Connection conn) throws Exception {
    try (Statement st = conn.createStatement()) {
      st.execute("ALTER TABLE posts ADD COLUMN search_text TEXT NULL AFTER excerpt");
    }
  }

  private void addFulltextIndex(Connection conn) throws Exception {
    try (Statement st = conn.createStatement()) {
      // ngram 파서 = 한글 부분일치(token size 2). innodb_ft_min_token_size 는 ngram 인덱스엔 적용되지
      // 않으므로 두 글자 검색어도 잡힌다.
      st.execute(
          "ALTER TABLE posts ADD FULLTEXT INDEX ft_posts_search_text (search_text) WITH PARSER ngram");
    }
  }

  private void backfill(Connection conn) throws Exception {
    PostSearchTextFlattener flattener = new PostSearchTextFlattener(JsonMapper.builder().build());
    List<Long> ids = allPostIds(conn);
    try (PreparedStatement update =
        conn.prepareStatement("UPDATE posts SET search_text = ? WHERE id = ?")) {
      int inBatch = 0;
      for (Long id : ids) {
        String title = titleExcerptField(conn, id, "title");
        String excerpt = titleExcerptField(conn, id, "excerpt");
        List<String> tags = tagsOf(conn, id);
        List<PostBlockEntity> blocks = blocksOf(conn, id);
        String searchText = flattener.flatten(title, excerpt, tags, blocks);
        update.setString(1, searchText);
        update.setLong(2, id);
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

  private List<Long> allPostIds(Connection conn) throws Exception {
    List<Long> ids = new ArrayList<>();
    try (Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("SELECT id FROM posts ORDER BY id")) {
      while (rs.next()) {
        ids.add(rs.getLong("id"));
      }
    }
    return ids;
  }

  private String titleExcerptField(Connection conn, long postId, String column) throws Exception {
    try (PreparedStatement ps =
        conn.prepareStatement("SELECT " + column + " FROM posts WHERE id = ?")) {
      ps.setLong(1, postId);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getString(1) : null;
      }
    }
  }

  private List<String> tagsOf(Connection conn, long postId) throws Exception {
    List<String> tags = new ArrayList<>();
    try (PreparedStatement ps =
        conn.prepareStatement("SELECT tag FROM post_tag WHERE post_id = ? ORDER BY ordinal")) {
      ps.setLong(1, postId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          tags.add(rs.getString("tag"));
        }
      }
    }
    return tags;
  }

  private List<PostBlockEntity> blocksOf(Connection conn, long postId) throws Exception {
    List<PostBlockEntity> blocks = new ArrayList<>();
    try (PreparedStatement ps =
        conn.prepareStatement(
            "SELECT block_type, content, block_order FROM post_block "
                + "WHERE post_id = ? ORDER BY block_order")) {
      ps.setLong(1, postId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          PostBlockType type = PostBlockType.valueOf(rs.getString("block_type"));
          blocks.add(
              new PostBlockEntity(postId, type, rs.getString("content"), rs.getInt("block_order")));
        }
      }
    }
    return blocks;
  }
}
