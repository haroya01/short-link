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

/**
 * 글 본문 블록 교체 경로의 쓰기 프로파일을 query-audit 로 관측하는 하네스. saveAll() 시절엔 블록 수만큼 단일 INSERT 가 나갔고 (IDENTITY 라
 * Hibernate 가 배치 못 함 — batch_size 도 무효), query-audit 의 repeated-single-insert 로 드러났다. 지금은
 * ReplacePostBlocksUseCase 가 insertAll() 로 한 번의 multi-row INSERT 를 쏘므로 그 경고가 사라진다.
 */
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
