package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.repository.PostSearchTextRepository;
import java.sql.Timestamp;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class PostSearchTextRepositoryAdapter implements PostSearchTextRepository {

  private final JdbcTemplate jdbcTemplate;

  // 한 글당 한 행(post_id = 공유 PK). 있으면 search_text·updated_at 만 갱신, 없으면 삽입. 단일 왕복 upsert 라
  // "먼저 조회 후 저장" 의 경합·2쿼리를 피한다. JPA 트랜잭션 커넥션 위에서 돈다(JpaTransactionManager 노출).
  private static final String UPSERT =
      "INSERT INTO post_search_text (post_id, search_text, created_at, updated_at) "
          + "VALUES (?, ?, ?, ?) "
          + "ON DUPLICATE KEY UPDATE search_text = VALUES(search_text), updated_at = VALUES(updated_at)";

  @Override
  public void upsert(Long postId, String searchText) {
    Timestamp now = Timestamp.from(Instant.now());
    jdbcTemplate.update(UPSERT, postId, searchText, now, now);
  }
}
