package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.infrastructure.persistence.JpaLinkRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 의도적으로 @Transactional 을 붙이지 않는다 — redirect 경로는 트랜잭션 없이 이 use case 를 호출하고, @Modifying UPDATE 는 그
 * 상태에서 TransactionRequiredException 으로 죽었던 이력이 있다. 테스트 트랜잭션을 두르면 바로 그 회귀가 가려진다.
 */
@SpringBootTest
@ActiveProfiles("test")
class IncrementViewCountUseCaseIntegrationTest {

  @Autowired private IncrementViewCountUseCase useCase;
  @Autowired private LinkRepository linkRepository;
  @Autowired private JpaLinkRepository jpaLinkRepository;

  private Long linkId;

  @AfterEach
  void cleanUp() {
    if (linkId != null) {
      jpaLinkRepository.deleteById(linkId);
    }
  }

  @Test
  void incrementsOutsideAnyCallerTransaction() {
    String code = "vc" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com/views", code, null, null));
    linkId = link.getId();

    int updated = useCase.execute(new IncrementViewCountCommand(new LinkId(linkId)));

    assertThat(updated).isEqualTo(1);
  }
}
