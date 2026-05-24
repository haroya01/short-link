package com.example.short_link.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.admin.exception.InvalidActivePeriodException;
import com.example.short_link.link.domain.ClickEventEntity;
import com.example.short_link.link.domain.ClickEventRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminAnalyticsServiceIntegrationTest {

  @Autowired private AdminAnalyticsService service;
  @Autowired private UserRepository userRepository;
  @Autowired private LinkRepository linkRepository;
  @Autowired private ClickEventRepository clickRepository;
  @Autowired private CacheManager cacheManager;

  @BeforeEach
  void clearCache() {
    var cache = cacheManager.getCache("admin-overview");
    if (cache != null) cache.clear();
  }

  @Test
  void cohortReturnsRowsForRecentSignups() {
    UserEntity u1 = userRepository.save(new UserEntity("c1@x.com", "google", "g-coh1"));
    UserEntity u2 = userRepository.save(new UserEntity("c2@x.com", "google", "g-coh2"));
    LinkEntity l1 =
        linkRepository.save(new LinkEntity("https://example.com/1", "coh0001", u1.getId(), null));
    LinkEntity l2 =
        linkRepository.save(new LinkEntity("https://example.com/2", "coh0002", u2.getId(), null));
    clickRepository.save(humanClick(l1.getId()));
    clickRepository.save(humanClick(l2.getId()));

    AdminCohort cohort = service.cohort(4);

    assertThat(cohort.weeks()).isEqualTo(4);
    assertThat(cohort.rows()).isNotEmpty();
    assertThat(cohort.rows().get(0).cells()).hasSize(4);
  }

  @Test
  void cohortClampsWeeksToValidRange() {
    AdminCohort low = service.cohort(0);
    AdminCohort high = service.cohort(999);
    assertThat(low.weeks()).isEqualTo(1);
    assertThat(high.weeks()).isEqualTo(26);
  }

  @Test
  void lifecycleAggregatesByDayDifference() {
    UserEntity u = userRepository.save(new UserEntity("l@x.com", "google", "g-life"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "lif0001", u.getId(), null));
    clickRepository.save(humanClick(link.getId()));
    clickRepository.save(humanClick(link.getId()));

    AdminLifecycle lifecycle = service.lifecycle(7);

    assertThat(lifecycle.maxDay()).isEqualTo(7);
    assertThat(lifecycle.days()).isNotEmpty();
    assertThat(lifecycle.days().get(0).day()).isZero();
    assertThat(lifecycle.days().get(0).clicks()).isGreaterThanOrEqualTo(2);
  }

  @Test
  void lifecycleClampsDays() {
    AdminLifecycle low = service.lifecycle(0);
    AdminLifecycle high = service.lifecycle(9999);
    assertThat(low.maxDay()).isEqualTo(1);
    assertThat(high.maxDay()).isEqualTo(90);
  }

  @Test
  void activeUsersDayPeriod() {
    seedActiveUser("a-day");
    AdminActiveUsers result = service.activeUsers("day");
    assertThat(result.period()).isEqualTo("day");
    assertThat(result.buckets()).isNotEmpty();
  }

  @Test
  void activeUsersWeekPeriod() {
    seedActiveUser("a-week");
    AdminActiveUsers result = service.activeUsers("week");
    assertThat(result.period()).isEqualTo("week");
    assertThat(result.buckets()).isNotEmpty();
  }

  @Test
  void activeUsersMonthPeriod() {
    seedActiveUser("a-month");
    AdminActiveUsers result = service.activeUsers("month");
    assertThat(result.period()).isEqualTo("month");
    assertThat(result.buckets()).isNotEmpty();
  }

  @Test
  void activeUsersDefaultsToDayWhenNull() {
    AdminActiveUsers result = service.activeUsers(null);
    assertThat(result.period()).isEqualTo("day");
  }

  @Test
  void activeUsersRejectsUnknownPeriod() {
    assertThatThrownBy(() -> service.activeUsers("annual"))
        .isInstanceOf(InvalidActivePeriodException.class);
  }

  private void seedActiveUser(String code) {
    UserEntity u = userRepository.save(new UserEntity(code + "@x.com", "google", code));
    String shortCode = "act" + Math.abs(code.hashCode() % 10000);
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", shortCode, u.getId(), null));
    clickRepository.save(humanClick(link.getId()));
  }

  private static ClickEventEntity humanClick(Long linkId) {
    return ClickEventEntity.builder()
        .linkId(linkId)
        .userAgent("ua")
        .clientIp("1.2.3.4")
        .deviceClass("desktop")
        .bot(false)
        .build();
  }
}
