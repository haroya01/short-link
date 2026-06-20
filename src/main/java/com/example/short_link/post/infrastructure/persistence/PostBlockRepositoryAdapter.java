package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class PostBlockRepositoryAdapter implements PostBlockRepository {

  private static final String INSERT_PREFIX =
      "INSERT INTO post_block"
          + " (post_id, block_type, content, block_order, created_at, updated_at) VALUES ";
  private static final String VALUE_TUPLE = "(?, ?, ?, ?, ?, ?)";

  private final JpaPostBlockRepository jpa;
  private final JdbcTemplate jdbcTemplate;

  @Override
  public void insertAll(List<PostBlockEntity> blocks) {
    if (blocks.isEmpty()) {
      return;
    }
    // One multi-row INSERT instead of saveAll's per-row INSERTs. PostBlockEntity is IDENTITY-keyed,
    // so Hibernate must read each auto-increment id back and can't batch — it emits one INSERT per
    // block. created_at/updated_at are normally stamped by Hibernate, so we set them here. Runs on
    // the JPA transaction's connection (JpaTransactionManager exposes it to JDBC).
    StringBuilder sql = new StringBuilder(INSERT_PREFIX);
    List<Object> args = new ArrayList<>(blocks.size() * 6);
    Timestamp now = Timestamp.from(Instant.now());
    for (int i = 0; i < blocks.size(); i++) {
      sql.append(i == 0 ? VALUE_TUPLE : ", " + VALUE_TUPLE);
      PostBlockEntity block = blocks.get(i);
      args.add(block.getPostId());
      args.add(block.getType().name());
      args.add(block.getContent());
      args.add(block.getBlockOrder());
      args.add(now);
      args.add(now);
    }
    jdbcTemplate.update(sql.toString(), args.toArray());
  }

  @Override
  public List<PostBlockEntity> findAllByPostIdOrderByBlockOrderAsc(Long postId) {
    return jpa.findAllByPostIdOrderByBlockOrderAsc(postId);
  }

  @Override
  public void deleteAllByPostId(Long postId) {
    jpa.deleteAllByPostId(postId);
  }
}
