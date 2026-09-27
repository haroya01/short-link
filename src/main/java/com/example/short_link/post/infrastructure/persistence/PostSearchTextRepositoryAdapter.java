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

  // 단일 upsert로 조회 후 삽입의 경합을 피한다. JDBC는 JPA 트랜잭션의 커넥션을 사용한다.
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
