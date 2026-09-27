package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostBlockType;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import io.queryaudit.junit5.QueryAudit;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// IDENTITY라 Hibernate가 INSERT를 배치하지 못해 블록 수만큼 단일 INSERT가 나갔다(batch_size도 소용없다).
// insertAll()이 multi-row INSERT 한 번으로 보내는지 query-audit로 확인한다.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@QueryAudit
class ReplacePostBlocksBatchQueryAuditTest {

  @Autowired private ReplacePostBlocksUseCase replaceBlocks;
  @Autowired private PostRepository postRepository;
  @Autowired private UserRepository userRepository;
  @PersistenceContext private EntityManager em;

  @Test
  void replacingBodyWithSixBlocks() {
    Long userId =
        userRepository.save(new UserEntity("blocks-author@x.com", "google", "blocks-gid")).getId();
    Long postId = postRepository.save(new PostEntity(userId, "blocky", "Blocky", "ko")).getId();
    em.flush();
    em.clear();

    List<ReplacePostBlocksCommand.BlockInput> blocks = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      blocks.add(new ReplacePostBlocksCommand.BlockInput(PostBlockType.PARAGRAPH, "para " + i));
    }
    List<PostBlockEntity> saved =
        replaceBlocks.execute(new ReplacePostBlocksCommand(userId, postId, blocks));
    em.flush();

    // The multi-row INSERT returns no generated keys, so the use case re-reads — callers still get
    // the persisted blocks with ids in order. This guards the "계약 보존" choice, not just the count.
    assertThat(saved).hasSize(6);
    assertThat(saved).allSatisfy(block -> assertThat(block.getId()).isNotNull());
    assertThat(saved).extracting(PostBlockEntity::getBlockOrder).containsExactly(0, 1, 2, 3, 4, 5);
    assertThat(saved)
        .extracting(PostBlockEntity::getContent)
        .containsExactly("para 0", "para 1", "para 2", "para 3", "para 4", "para 5");
  }
}
