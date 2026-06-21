package com.example.short_link.link.stats.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.link.access.application.LinkVisibilityService;
import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
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
  @Autowired private LinkStatsLifecycleReader lifecycleReader;

  @Test
  void statsForOwner_returnsAggregatedCounts() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-st1"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "st0001", owner.getId(), null));
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .userAgent("ua")
            .clientIp("1.2.3.4")
            .deviceClass("desktop")
            .bot(false)
            .build());
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .userAgent("crawler")
            .clientIp("1.2.3.5")
            .deviceClass("bot")
            .bot(true)
            .build());

    LinkStats stats = service.stats(owner.getId(), new ShortCode("st0001"));

    assertThat(stats.totalClicks()).isGreaterThanOrEqualTo(2);
    assertThat(stats.humanClicks()).isGreaterThanOrEqualTo(1);
    assertThat(stats.botClicks()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void statsThrowsForUnknownShortCode() {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-st2"));
    assertThatThrownBy(() -> service.stats(user.getId(), new ShortCode("nope9999")))
        .isInstanceOf(LinkException.class);
  }

  @Test
  void statsRejectsViewByNonOwnerForPrivateLink() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-st3"));
    UserEntity attacker = userRepository.save(new UserEntity("a@x.com", "google", "g-st3a"));
    linkRepository.save(new LinkEntity("https://example.com", "st0003", owner.getId(), null));

    assertThatThrownBy(() -> service.stats(attacker.getId(), new ShortCode("st0003")))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void publicStatsRejectsWhenNotPublic() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-pst1"));
    linkRepository.save(new LinkEntity("https://example.com", "pst0001", owner.getId(), null));

    assertThatThrownBy(() -> service.publicStats(new ShortCode("pst0001")))
        .isInstanceOf(LinkException.class);
  }

  @Test
  void publicStatsAllowsWhenPublic() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-pst2"));
    linkRepository.save(new LinkEntity("https://example.com", "pst0002", owner.getId(), null));
    visibilityService.setStatsPublic(owner.getId(), new ShortCode("pst0002"), true);

    LinkStats stats = service.publicStats(new ShortCode("pst0002"));
    assertThat(stats).isNotNull();
  }

  @Test
  void publicStatsThrowsForUnknown() {
    assertThatThrownBy(() -> service.publicStats(new ShortCode("nope9999")))
        .isInstanceOf(LinkException.class);
  }

  private void hostClick(LinkEntity link, String host, java.time.Instant at) {
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .userAgent("ua")
            .clientIp("9.9.9.9")
            .deviceClass("mobile")
            .referrer("https://" + host + "/x")
            .referrerHost(host)
            .clickedAt(at)
            .bot(false)
            .build());
  }

  @Test
  void channelJump_detectsHostThatAppearsLongAfterOrigin() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-cj"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "stcjmp", owner.getId(), null));
    java.time.Instant t0 = java.time.Instant.now().minus(java.time.Duration.ofHours(3));
    hostClick(link, "instagram.com", t0); // 원래 채널(가장 이른)
    hostClick(
        link, "twitter.com", t0.plus(java.time.Duration.ofMinutes(30))); // 30분 — gap<3600, 건너뜀
    hostClick(link, "reddit.com", t0.plus(java.time.Duration.ofHours(2))); // 2시간 — gap≥3600, 점프

    var insight = lifecycleReader.channelJump(link.linkId());

    assertThat(insight).isPresent();
    assertThat(insight.get().type()).isEqualTo("CHANNEL_JUMP");
    assertThat(insight.get().data())
        .containsEntry("origin", "instagram.com")
        .containsEntry("jumpedTo", "reddit.com");
  }

  @Test
  void channelJump_emptyWhenSingleHost() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-cj1"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "stcj1h", owner.getId(), null));
    hostClick(link, "instagram.com", java.time.Instant.now().minus(java.time.Duration.ofHours(1)));

    assertThat(lifecycleReader.channelJump(link.linkId())).isEmpty();
  }

  @Test
  void channelJump_emptyWhenAllHostsWithinAnHour() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-cj2"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "stcj2h", owner.getId(), null));
    java.time.Instant t0 = java.time.Instant.now().minus(java.time.Duration.ofHours(2));
    hostClick(link, "instagram.com", t0);
    hostClick(link, "twitter.com", t0.plus(java.time.Duration.ofMinutes(20))); // gap<3600 → 점프 아님

    // 두 host 지만 1시간 안에 다 나타나 채널 점프로 보지 않는다(루프가 점프 없이 끝나는 갈래).
    assertThat(lifecycleReader.channelJump(link.linkId())).isEmpty();
  }
}
