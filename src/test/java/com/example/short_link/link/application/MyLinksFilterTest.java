package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end coverage for the my-links filter pipeline. Each test seeds a small set of links and
 * checks that the {@link MyLinksService} returns exactly the matching subset for a given filter
 * combination.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MyLinksFilterTest {

  @Autowired private MyLinksService service;
  @Autowired private LinkRepository linkRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private LinkTagService linkTagService;

  @Test
  void filtersByDomainSubstring() {
    UserEntity user = userRepository.save(new UserEntity("f1@local.test", "google", "g-f1"));
    save(user, "https://example.com/a", "fc00001", null);
    save(user, "https://blog.example.com/b", "fc00002", null);
    save(user, "https://other.io/c", "fc00003", null);

    MyLinksResult result =
        service.myLinks(
            user.getId(), MyLinksQuery.of(null, 50, null, null, "example.com", null, null, null));

    assertThat(result.items()).extracting(MyLink::shortCode).containsOnly("fc00001", "fc00002");
  }

  @Test
  void filtersByExpiryNeverActiveExpired() {
    UserEntity user = userRepository.save(new UserEntity("f2@local.test", "google", "g-f2"));
    save(user, "https://example.com/never", "fc00010", null);
    save(user, "https://example.com/active", "fc00011", Instant.now().plus(Duration.ofDays(7)));
    save(user, "https://example.com/expired", "fc00012", Instant.now().minus(Duration.ofDays(1)));

    assertThat(filterExpiry(user.getId(), "NEVER"))
        .extracting(MyLink::shortCode)
        .containsOnly("fc00010");
    assertThat(filterExpiry(user.getId(), "ACTIVE"))
        .extracting(MyLink::shortCode)
        .containsOnly("fc00010", "fc00011");
    assertThat(filterExpiry(user.getId(), "EXPIRED"))
        .extracting(MyLink::shortCode)
        .containsOnly("fc00012");
    assertThat(filterExpiry(user.getId(), "HAS_EXPIRY"))
        .extracting(MyLink::shortCode)
        .containsOnly("fc00011", "fc00012");
  }

  @Test
  void filtersByCreatedDateRange() {
    UserEntity user = userRepository.save(new UserEntity("f3@local.test", "google", "g-f3"));
    save(user, "https://example.com/recent", "fc00020", null);
    // Use a generous margin so MySQL TIMESTAMP precision (second-level on the link table) doesn't
    // matter for this test — we're verifying spec wiring, not millisecond cutoffs.
    Instant futureCutoff = Instant.now().plus(Duration.ofMinutes(5));
    Instant pastCutoff = Instant.now().minus(Duration.ofMinutes(5));

    MyLinksResult inWindow =
        service.myLinks(
            user.getId(),
            MyLinksQuery.of(null, 50, null, null, null, null, null, futureCutoff.toString()));
    assertThat(inWindow.items()).extracting(MyLink::shortCode).contains("fc00020");

    MyLinksResult excluded =
        service.myLinks(
            user.getId(),
            MyLinksQuery.of(null, 50, null, null, null, null, futureCutoff.toString(), null));
    assertThat(excluded.items()).extracting(MyLink::shortCode).doesNotContain("fc00020");

    MyLinksResult afterPast =
        service.myLinks(
            user.getId(),
            MyLinksQuery.of(null, 50, null, null, null, null, pastCutoff.toString(), null));
    assertThat(afterPast.items()).extracting(MyLink::shortCode).contains("fc00020");
  }

  @Test
  void combinesQueryTagAndDomain() {
    UserEntity user = userRepository.save(new UserEntity("f4@local.test", "google", "g-f4"));
    save(user, "https://news.example.com/a", "fc00030", null);
    save(user, "https://news.example.com/b", "fc00031", null);
    save(user, "https://other.example.com/c", "fc00032", null);

    linkTagService.replaceTags(user.getId(), "fc00030", List.of("work"));
    linkTagService.replaceTags(user.getId(), "fc00031", List.of("home"));
    linkTagService.replaceTags(user.getId(), "fc00032", List.of("work"));

    MyLinksResult result =
        service.myLinks(
            user.getId(),
            MyLinksQuery.of(null, 50, null, "work", "news.example.com", null, null, null));

    assertThat(result.items()).extracting(MyLink::shortCode).containsOnly("fc00030");
  }

  @Test
  void scopesToOwner() {
    UserEntity owner = userRepository.save(new UserEntity("f5@local.test", "google", "g-f5"));
    UserEntity stranger = userRepository.save(new UserEntity("f5b@local.test", "google", "g-f5b"));
    save(owner, "https://example.com/mine", "fc00040", null);
    save(stranger, "https://example.com/notmine", "fc00041", null);

    MyLinksResult result =
        service.myLinks(
            owner.getId(), MyLinksQuery.of(null, 50, null, null, null, null, null, null));

    assertThat(result.items()).extracting(MyLink::shortCode).containsExactly("fc00040");
  }

  private List<MyLink> filterExpiry(Long userId, String value) {
    return service
        .myLinks(userId, MyLinksQuery.of(null, 50, null, null, null, value, null, null))
        .items();
  }

  private LinkEntity save(UserEntity user, String url, String shortCode, Instant expiresAt) {
    return linkRepository.save(new LinkEntity(url, shortCode, user.getId(), expiresAt));
  }
}
