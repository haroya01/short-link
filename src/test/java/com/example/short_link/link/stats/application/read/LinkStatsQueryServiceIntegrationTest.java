package com.example.short_link.link.stats.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.link.access.application.LinkVisibilityService;
import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LinkStatsQueryServiceIntegrationTest {

  @Autowired private LinkStatsQueryService service;
  @Autowired private LinkRepository linkRepository;
  @Autowired private ClickEventRepository clickRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private LinkVisibilityService visibilityService;

  @Test
  void statsForOwner_returnsAggregatedCounts() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-st1"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "st0001", owner.getId(), null));
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.getId())
            .userAgent("ua")
            .clientIp("1.2.3.4")
            .deviceClass("desktop")
            .bot(false)
            .build());
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.getId())
            .userAgent("crawler")
            .clientIp("1.2.3.5")
            .deviceClass("bot")
            .bot(true)
            .build());

    LinkStats stats = service.stats(owner.getId(), "st0001");

    assertThat(stats.totalClicks()).isGreaterThanOrEqualTo(2);
    assertThat(stats.humanClicks()).isGreaterThanOrEqualTo(1);
    assertThat(stats.botClicks()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void statsThrowsForUnknownShortCode() {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-st2"));
    assertThatThrownBy(() -> service.stats(user.getId(), "nope9999"))
        .isInstanceOf(LinkException.class);
  }

  @Test
  void statsRejectsViewByNonOwnerForPrivateLink() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-st3"));
    UserEntity attacker = userRepository.save(new UserEntity("a@x.com", "google", "g-st3a"));
    linkRepository.save(new LinkEntity("https://example.com", "st0003", owner.getId(), null));

    assertThatThrownBy(() -> service.stats(attacker.getId(), "st0003"))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void publicStatsRejectsWhenNotPublic() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-pst1"));
    linkRepository.save(new LinkEntity("https://example.com", "pst0001", owner.getId(), null));

    assertThatThrownBy(() -> service.publicStats("pst0001")).isInstanceOf(LinkException.class);
  }

  @Test
  void publicStatsAllowsWhenPublic() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-pst2"));
    linkRepository.save(new LinkEntity("https://example.com", "pst0002", owner.getId(), null));
    visibilityService.setStatsPublic(owner.getId(), "pst0002", true);

    LinkStats stats = service.publicStats("pst0002");
    assertThat(stats).isNotNull();
  }

  @Test
  void publicStatsThrowsForUnknown() {
    assertThatThrownBy(() -> service.publicStats("nope9999")).isInstanceOf(LinkException.class);
  }
}
