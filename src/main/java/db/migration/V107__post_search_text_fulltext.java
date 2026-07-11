package db.migration;

import com.example.short_link.post.application.write.PostSearchTextFlattener;
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

/**
 * 검색 엔진 업그레이드 — 파생 검색 평문(search_text)을 담는 곁 테이블 post_search_text 와 그 위의 FULLTEXT(ngram) 인덱스를 만든다.
 *
 * <p>예전 검색은 title·excerpt·태그·작가 핸들만 LIKE '%q%' 로 훑어 본문(post_block)이 통째로 빠졌다. search_text 는
 * 제목·요약·태그·본문 블록을 사람이 읽는 자연어로 펼쳐 담아, ngram FULLTEXT 한 컬럼이 본문까지 부분일치로 검색되게 한다(한글은 token size 2 로 두
 * 글자부터 매칭).
 *
 * <p>평문을 posts 컬럼이 아니라 별도 테이블에 두는 이유는 읽기 성능이다: search_text 는 FULLTEXT 인덱스만 훑는 파생 캐시일 뿐 응답에 실리지 않는데,
 * posts 컬럼으로 두면 피드·상세 등 모든 PostEntity 로드가 최대 수십 KB 본문 평문을 함께 끌어온다. 곁 테이블(post_id = 공유 PK, FK ON
 * DELETE CASCADE)로 옮기면 posts 로드는 이 컬럼을 아예 만지지 않고 검색만 필요할 때 JOIN 으로 붙는다.
 *
 * <p>순서가 중요하다: (1) 테이블 생성 → (2) 기존 글 백필 → (3) FULLTEXT 인덱스 생성. 인덱스를 마지막에 만들어 채워진 데이터 위에서 한 번에 빌드하고,
 * 백필 INSERT 마다 인덱스가 재구성되는 것을 피한다. 백필은 블록 payload(타입별 JSON/평문)를 파싱해야 해서 순수 SQL 로는 불가능 — 앱의 {@link
 * PostSearchTextFlattener} 를 그대로 재사용해 런타임 쓰기 경로와 펼치는 규칙을 단일화한다.
 *
 * <p>발행글만이 아니라 모든 상태(초안 포함)를 백필한다: 초안도 발행되면 즉시 검색 대상이 되고, 행이 없으면 그때까지 본문 검색에서 누락되기 때문이다.
 *
 * <p>각 DDL 은 재실행 안전하게 짠다(IF NOT EXISTS / 인덱스 존재 확인). 백필 중간에 실패해 재배포되어도 "Duplicate column/table" 로
 * 깨지지 않고, upsert(INSERT ... ON DUPLICATE KEY UPDATE) 백필이 이미 채운 행을 덮어써 멱등하다.
 */
public class V107__post_search_text_fulltext extends BaseJavaMigration {

  private static final int BATCH = 500;
  private static final String FULLTEXT_INDEX = "ft_post_search_text";

  @Override
  public void migrate(Context context) throws Exception {
    Connection conn = context.getConnection();
    createTable(conn);
    backfill(conn);
    addFulltextIndex(conn);
  }

  private void createTable(Connection conn) throws Exception {
    try (Statement st = conn.createStatement()) {
      // post_id = posts.id 공유 PK(1:1). FK ON DELETE CASCADE 로 글이 지워지면 이 행도 자동 정리된다.
      // IF NOT EXISTS 로 재실행 안전. 문자셋은 posts 와 맞춰 utf8mb4(한글·이모지).
      st.execute(
          "CREATE TABLE IF NOT EXISTS post_search_text ("
              + "post_id BIGINT NOT NULL PRIMARY KEY, "
              + "search_text TEXT NULL, "
              + "created_at DATETIME(6) NOT NULL, "
              + "updated_at DATETIME(6) NOT NULL, "
              + "CONSTRAINT fk_post_search_text_post FOREIGN KEY (post_id) "
              + "REFERENCES posts (id) ON DELETE CASCADE"
              + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    }
  }

  private void addFulltextIndex(Connection conn) throws Exception {
    if (fulltextIndexExists(conn)) {
      // 중간 실패 후 재배포 — 인덱스가 이미 있으면 "Duplicate key name" 없이 넘어간다.
      return;
    }
    try (Statement st = conn.createStatement()) {
      // ngram 파서 = 한글 부분일치(token size 2). innodb_ft_min_token_size 는 ngram 인덱스엔 적용되지
      // 않으므로 두 글자 검색어도 잡힌다.
      st.execute(
          "ALTER TABLE post_search_text ADD FULLTEXT INDEX "
              + FULLTEXT_INDEX
              + " (search_text) WITH PARSER ngram");
    }
  }

  private boolean fulltextIndexExists(Connection conn) throws Exception {
    try (PreparedStatement ps =
        conn.prepareStatement(
            "SELECT 1 FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = 'post_search_text' "
                + "AND index_name = ?")) {
      ps.setString(1, FULLTEXT_INDEX);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next();
      }
    }
  }

  private void backfill(Connection conn) throws Exception {
    PostSearchTextFlattener flattener = new PostSearchTextFlattener(JsonMapper.builder().build());
    List<Long> ids = allPostIds(conn);
    Map<Long, String[]> meta = titleExcerptByPost(conn); // id → [title, excerpt] (단일 SELECT)
    Map<Long, List<String>> tagsByPost = tagsByPost(conn); // (단일 SELECT)
    Map<Long, List<PostBlockEntity>> blocksByPost = blocksByPost(conn); // (단일 SELECT)
    // upsert 백필 — 재실행돼도 이미 채운 행을 덮어써 멱등. created_at/updated_at 은 앱 규약대로 채운다.
    try (PreparedStatement update =
        conn.prepareStatement(
            "INSERT INTO post_search_text (post_id, search_text, created_at, updated_at) "
                + "VALUES (?, ?, NOW(6), NOW(6)) "
                + "ON DUPLICATE KEY UPDATE search_text = VALUES(search_text), "
                + "updated_at = VALUES(updated_at)")) {
      int inBatch = 0;
      for (Long id : ids) {
        String[] te = meta.getOrDefault(id, new String[2]);
        List<String> tags = tagsByPost.getOrDefault(id, List.of());
        List<PostBlockEntity> blocks = blocksByPost.getOrDefault(id, List.of());
        String searchText = flattener.flatten(te[0], te[1], tags, blocks);
        update.setLong(1, id);
        update.setString(2, searchText);
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

  // 글당 별도 SELECT(4N+1) 대신 테이블당 한 번씩 스캔해 메모리로 그룹핑한다 — 백필은 배포 시 1회성이라 한 방에 읽는 게
  // 커넥션·라운드트립을 아낀다.
  private Map<Long, String[]> titleExcerptByPost(Connection conn) throws Exception {
    Map<Long, String[]> byId = new HashMap<>();
    try (Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("SELECT id, title, excerpt FROM posts")) {
      while (rs.next()) {
        byId.put(rs.getLong("id"), new String[] {rs.getString("title"), rs.getString("excerpt")});
      }
    }
    return byId;
  }

  private Map<Long, List<String>> tagsByPost(Connection conn) throws Exception {
    Map<Long, List<String>> byPost = new HashMap<>();
    try (Statement st = conn.createStatement();
        ResultSet rs =
            st.executeQuery("SELECT post_id, tag FROM post_tag ORDER BY post_id, ordinal")) {
      while (rs.next()) {
        byPost
            .computeIfAbsent(rs.getLong("post_id"), k -> new ArrayList<>())
            .add(rs.getString("tag"));
      }
    }
    return byPost;
  }

  private Map<Long, List<PostBlockEntity>> blocksByPost(Connection conn) throws Exception {
    Map<Long, List<PostBlockEntity>> byPost = new HashMap<>();
    try (Statement st = conn.createStatement();
        ResultSet rs =
            st.executeQuery(
                "SELECT post_id, block_type, content, block_order FROM post_block "
                    + "ORDER BY post_id, block_order")) {
      while (rs.next()) {
        // enum 밖 legacy block_type 행이 섞여 있으면(과거 스키마 잔재) 마이그레이션 전체가 크래시하지 않도록
        // 방어적으로 스킵한다 — 알 수 없는 타입은 검색 평문에 기여하지 않을 뿐, 백필은 계속된다.
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
