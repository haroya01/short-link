package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.repository.AuthorPostNumberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class AuthorPostNumberRepositoryAdapter implements AuthorPostNumberRepository {

  // 한 문장으로 작가의 카운터 행을 잠그고 올린다. 같은 작가의 동시 발행은 여기서 줄을 선다.
  // INSERT IGNORE 뒤 FOR UPDATE 는 첫 행에서 공유 잠금 두 개가 서로를 기다릴 수 있다.
  private static final String NEXT =
      "INSERT INTO author_post_number (user_id, last_number) VALUES (?, 1)"
          + " ON DUPLICATE KEY UPDATE last_number = last_number + 1";
  private static final String CURRENT =
      "SELECT last_number FROM author_post_number WHERE user_id = ? FOR UPDATE";

  private final JdbcTemplate jdbcTemplate;

  @Override
  public long next(Long userId) {
    jdbcTemplate.update(NEXT, userId);
    return jdbcTemplate.queryForObject(CURRENT, Long.class, userId);
  }
}
