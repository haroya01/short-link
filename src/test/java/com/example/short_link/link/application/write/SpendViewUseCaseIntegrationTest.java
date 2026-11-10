package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

// 의도적으로 @Transactional 을 붙이지 않는다 — redirect 경로는 트랜잭션 없이 이 use case 를 호출하고, @Modifying UPDATE 는 그
// 상태에서 TransactionRequiredException 으로 죽었던 이력이 있다. 테스트 트랜잭션을 두르면 바로 그 회귀가 가려진다.
@SpringBootTest
@Tag("integration")
@ActiveProfiles("test")
class SpendViewUseCaseIntegrationTest {

  @Autowired private SpendViewUseCase views;
  @Autowired private LinkRepository links;
  @Autowired private JdbcTemplate jdbc;

  private LinkEntity link;

  @BeforeEach
  void aLink() {
    String code = "vc" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    link = links.save(new LinkEntity("https://example.com/views", code, null, null));
  }

  @AfterEach
  void cleanUp() {
    jdbc.update("DELETE FROM link WHERE id = ?", link.getId());
  }

  @Test
  void spendsOutsideAnyCallerTransaction() {
    limit(1, 0);

    assertThat(views.spend(link.linkId())).isTrue();
  }

  @Test
  void concurrentVisitorsNeverSpendMoreThanTheLimit() throws Exception {
    limit(3, 0);
    int visitors = 16;
    ExecutorService pool = Executors.newFixedThreadPool(visitors);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Boolean>> attempts = new ArrayList<>();
    try {
      for (int i = 0; i < visitors; i++) {
        attempts.add(
            pool.submit(
                () -> {
                  start.await();
                  return views.spend(link.linkId());
                }));
      }
      start.countDown();
      int granted = 0;
      for (Future<Boolean> attempt : attempts) {
        if (attempt.get(30, TimeUnit.SECONDS)) granted++;
      }
      assertThat(granted).isEqualTo(3);
    } finally {
      pool.shutdownNow();
    }
    assertThat(viewCount()).isEqualTo(3);
  }

  @ParameterizedTest(name = "max {0}, spent {1}")
  @CsvSource({",0", ",7", "0,0", "1,0", "3,2", "3,3", "3,4"})
  void theEntityTheProfileAndTheSpendAgreeOnAnExhaustedLink(Integer maxViews, int viewCount) {
    limit(maxViews, viewCount);

    boolean exhaustedForTheEntity = links.findById(link.getId()).orElseThrow().isViewLimitReached();
    boolean exhaustedForTheProfile =
        links
            .findShortCodesWithViewLimitReached(List.of(link.getShortCode()))
            .contains(link.getShortCode());
    boolean spent = views.spend(link.linkId());

    assertThat(exhaustedForTheProfile).isEqualTo(exhaustedForTheEntity);
    assertThat(spent).isEqualTo(!exhaustedForTheEntity);
  }

  private void limit(Integer maxViews, int viewCount) {
    jdbc.update(
        "UPDATE link SET max_views = ?, view_count = ? WHERE id = ?",
        maxViews,
        viewCount,
        link.getId());
  }

  private int viewCount() {
    return jdbc.queryForObject(
        "SELECT view_count FROM link WHERE id = ?", Integer.class, link.getId());
  }
}
