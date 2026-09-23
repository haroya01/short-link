package com.example.short_link.admin.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.admin.domain.repository.AdminMetricsRepository.DailyRow;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository.RecentClickRow;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminSmokeClicksExclusionIntegrationTest {
  private static final String SEOUL = "+09:00";

  @Autowired private UserRepository users;
  @Autowired private LinkRepository links;
  @Autowired private ClickEventRepository clicks;
  @Autowired private JpaAdminMetricsRepository metrics;
  @PersistenceContext private EntityManager em;

  private Instant since;
  private UserEntity smoke;
  private LinkEntity smokeLink;
  private LinkEntity realLink;

  @BeforeEach
  void aSmokeAccountAndARealAccount() {
    since = Instant.now().minus(1, ChronoUnit.HOURS);
    smoke = user();
    smokeLink = link(smoke);
    realLink = link(user());
    em.flush();
  }

  @Test
  void clickCountsLeaveOutTheSmokeAccountsLinks() {
    long totalBefore = metrics.totalClicks(smoke.getId());
    long sinceBefore = metrics.clicksSince(since, smoke.getId());
    long todayBefore = today();

    click(smokeLink);
    click(smokeLink);
    click(realLink);

    assertThat(metrics.totalClicks(smoke.getId()) - totalBefore).isEqualTo(1);
    assertThat(metrics.clicksSince(since, smoke.getId()) - sinceBefore).isEqualTo(1);
    assertThat(today() - todayBefore).isEqualTo(1);
    assertThat(metrics.totalClicks(-1L) - totalBefore).isEqualTo(3);
  }

  @Test
  void recentClicksLeaveOutTheSmokeAccountsLinks() {
    click(smokeLink);
    click(realLink);

    assertThat(metrics.recentClicks(smoke.getId(), PageRequest.ofSize(50)))
        .extracting(RecentClickRow::getShortCode)
        .contains(realLink.getShortCode().value())
        .doesNotContain(smokeLink.getShortCode().value());
  }

  private long today() {
    return metrics.dailyClicksSince(since, SEOUL, smoke.getId()).stream()
        .mapToLong(DailyRow::getCount)
        .sum();
  }

  private UserEntity user() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    return users.save(new UserEntity(suffix + "@x.com", "google", "g-" + suffix));
  }

  private LinkEntity link(UserEntity owner) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    return links.save(
        new LinkEntity("https://example.com/" + suffix, "s" + suffix, owner.getId(), null));
  }

  private void click(LinkEntity link) {
    clicks.save(ClickEventEntity.builder().linkId(link.linkId()).clickedAt(Instant.now()).build());
    em.flush();
  }
}
