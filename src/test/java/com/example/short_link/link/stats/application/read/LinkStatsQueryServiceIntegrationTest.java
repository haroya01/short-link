package com.example.short_link.link.stats.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.example.short_link.link.access.application.LinkVisibilityService;
import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.domain.repository.LinkDestinationRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.stats.application.LinkInsights;
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
  @Autowired private LinkDestinationRepository destinationRepository;
  @Autowired private ClickEventRepository clickRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private LinkVisibilityService visibilityService;
  @Autowired private LinkStatsLifecycleReader lifecycleReader;
  @Autowired private LinkInsights insights;
  @Autowired private LinkStatsDimensionBreakdownsReader dimensionsReader;

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
  void publicStatsKeepDestinationLabelsAndCountsButNotTheirUrls() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-pst3"));
    LinkEntity link =
        linkRepository.save(
            new LinkEntity("https://secret.example.com/plan", "pst0003", owner.getId(), null));
    LinkDestinationEntity variant =
        destinationRepository.save(
            new LinkDestinationEntity(
                link.linkId(), "https://secret.example.com/variant", 1, "Variant", null));
    destinationClick(link, null);
    destinationClick(link, variant.getId());
    destinationClick(link, variant.getId());
    visibilityService.setStatsPublic(owner.getId(), new ShortCode("pst0003"), true);

    LinkStats ownerView = service.stats(owner.getId(), new ShortCode("pst0003"));
    LinkStats publicView = service.publicStats(new ShortCode("pst0003"));

    assertThat(ownerView.destinationClicks())
        .extracting(LinkStats.DestinationClick::url)
        .containsExactlyInAnyOrder(
            "https://secret.example.com/plan", "https://secret.example.com/variant");
    assertThat(publicView.destinationClicks())
        .extracting(
            LinkStats.DestinationClick::url,
            LinkStats.DestinationClick::label,
            LinkStats.DestinationClick::count)
        .containsExactlyInAnyOrder(tuple(null, "default", 1L), tuple(null, "Variant", 2L));
  }

  private void destinationClick(LinkEntity link, Long destinationId) {
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .destinationId(destinationId)
            .userAgent("ua")
            .clientIp("9.9.9.9")
            .deviceClass("mobile")
            .bot(false)
            .build());
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
    hostClick(link, "instagram.com", t0);
    hostClick(
        link, "twitter.com", t0.plus(java.time.Duration.ofMinutes(30))); // 30분 — gap<3600, 건너뜀
    hostClick(link, "reddit.com", t0.plus(java.time.Duration.ofHours(2))); // 2시간 — gap≥3600, 점프

    var insight = insights.channelJump(lifecycleReader.channelFirstSeen(link.linkId()));

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

    assertThat(insights.channelJump(lifecycleReader.channelFirstSeen(link.linkId()))).isEmpty();
  }

  @Test
  void channelJump_emptyWhenAllHostsWithinAnHour() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-cj2"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "stcj2h", owner.getId(), null));
    java.time.Instant t0 = java.time.Instant.now().minus(java.time.Duration.ofHours(2));
    hostClick(link, "instagram.com", t0);
    hostClick(link, "twitter.com", t0.plus(java.time.Duration.ofMinutes(20)));

    // 두 host 지만 1시간 안에 다 나타나 채널 점프로 보지 않는다(루프가 점프 없이 끝나는 갈래).
    assertThat(insights.channelJump(lifecycleReader.channelFirstSeen(link.linkId()))).isEmpty();
  }

  @Test
  void clientAppClicks_countHumanInAppClicksOnly() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-ca1"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "stcapp", owner.getId(), null));
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .clientIp("1.1.1.1")
            .clientApp("kakaotalk")
            .bot(false)
            .build());
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .clientIp("1.1.1.2")
            .clientApp("kakaotalk")
            .bot(false)
            .build());
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .clientIp("1.1.1.3")
            .clientApp("instagram")
            .bot(false)
            .build());
    clickRepository.save(
        ClickEventEntity.builder().linkId(link.linkId()).clientIp("1.1.1.4").bot(false).build());
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .clientIp("1.1.1.5")
            .clientApp("kakaotalk")
            .bot(true)
            .build());

    var apps = dimensionsReader.clientApps(link.linkId());

    assertThat(apps)
        .extracting(LinkStats.ClientAppClick::app, LinkStats.ClientAppClick::count)
        .containsExactly(
            org.assertj.core.api.Assertions.tuple("kakaotalk", 2L),
            org.assertj.core.api.Assertions.tuple("instagram", 1L));
  }

  @Test
  void fetchSiteClicks_groupHumanClicksAndSkipMissingHeader() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-fs1"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "stfsit", owner.getId(), null));
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .clientIp("2.1.1.1")
            .fetchSite("none")
            .bot(false)
            .build());
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .clientIp("2.1.1.2")
            .fetchSite("none")
            .bot(false)
            .build());
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .clientIp("2.1.1.3")
            .fetchSite("cross-site")
            .bot(false)
            .build());
    // 헤더를 안 보낸 클릭은 어느 버킷에도 안 들어간다(백필 불가 = 모르는 것).
    clickRepository.save(
        ClickEventEntity.builder().linkId(link.linkId()).clientIp("2.1.1.4").bot(false).build());

    var sites = dimensionsReader.fetchSites(link.linkId());

    assertThat(sites)
        .extracting(LinkStats.FetchSiteClick::fetchSite, LinkStats.FetchSiteClick::count)
        .containsExactly(
            org.assertj.core.api.Assertions.tuple("none", 2L),
            org.assertj.core.api.Assertions.tuple("cross-site", 1L));
  }

  @Test
  void utmTermClicks_aggregateLikeTheOtherUtmDimensions() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-ut1"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "stterm", owner.getId(), null));
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .clientIp("3.1.1.1")
            .utmTerm("리텐션")
            .bot(false)
            .build());
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .clientIp("3.1.1.2")
            .utmTerm("리텐션")
            .bot(false)
            .build());
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .clientIp("3.1.1.3")
            .utmTerm("분석")
            .bot(false)
            .build());
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .clientIp("3.1.1.4")
            .utmTerm("리텐션")
            .bot(true)
            .build());

    var terms = dimensionsReader.utm(link.linkId()).terms();

    assertThat(terms)
        .extracting(LinkStats.UtmTermClick::term, LinkStats.UtmTermClick::count)
        .containsExactly(
            org.assertj.core.api.Assertions.tuple("리텐션", 2L),
            org.assertj.core.api.Assertions.tuple("분석", 1L));
  }

  @Test
  void postClicks_attributeHumanClicksAndKeepCountForDeletedPost() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-pc1"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "stpost", owner.getId(), null));
    // post 슬라이스를 직접 만들지 않고 존재하지 않는 id 로 둔다 — 제목은 null, 클릭 수는 남는다는 계약을 그대로 친다.
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .clientIp("4.1.1.1")
            .postId(987_654_321L)
            .bot(false)
            .build());
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .clientIp("4.1.1.2")
            .postId(987_654_321L)
            .bot(false)
            .build());
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .clientIp("4.1.1.3")
            .postId(987_654_321L)
            .bot(true)
            .build());
    clickRepository.save(
        ClickEventEntity.builder().linkId(link.linkId()).clientIp("4.1.1.4").bot(false).build());

    var posts = dimensionsReader.postClicks(link.linkId());

    assertThat(posts).hasSize(1);
    assertThat(posts.get(0).postId()).isEqualTo(987_654_321L);
    assertThat(posts.get(0).count()).isEqualTo(2L);
    assertThat(posts.get(0).title()).isNull();
  }

  private void depthClick(
      LinkEntity link, String host, String visitor, java.time.Instant at, boolean bot) {
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .clientIp("9.9.9.9")
            .referrer("https://" + host + "/x")
            .referrerHost(host)
            .visitorHash(visitor)
            .clickedAt(at)
            .bot(bot)
            .build());
  }

  @Test
  void channelDepth_reportsFirstSeenAndSessionizedReturnRatePerHost() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-cd1"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "stcdep", owner.getId(), null));
    java.time.Instant t0 = java.time.Instant.now().minus(java.time.Duration.ofDays(3));

    // instagram: 방문자 2명, 그중 1명만 하루 뒤 다시 옴 → 재방문 1 / 방문자 2 = 0.5
    depthClick(link, "instagram.com", "ig-a", t0, false);
    depthClick(link, "instagram.com", "ig-b", t0.plus(java.time.Duration.ofMinutes(5)), false);
    depthClick(link, "instagram.com", "ig-a", t0.plus(java.time.Duration.ofDays(1)), false);
    // 같은 자리 더블탭(2분 뒤)은 세션이 갈리지 않아 재방문으로 세지 않는다.
    depthClick(link, "instagram.com", "ig-b", t0.plus(java.time.Duration.ofMinutes(7)), false);
    depthClick(link, "notion.so", "nt-a", t0.plus(java.time.Duration.ofDays(1)), false);
    depthClick(link, "instagram.com", "bot-a", t0, true);

    var depth = lifecycleReader.channelDepth(link.linkId());

    assertThat(depth)
        .extracting(LinkStats.ChannelDepth::host)
        .containsExactly("instagram.com", "notion.so");
    var instagram = depth.get(0);
    assertThat(instagram.count()).isEqualTo(4L);
    assertThat(instagram.returningVisitors()).isEqualTo(1L);
    assertThat(instagram.returnRatio()).isEqualTo(0.5);
    assertThat(instagram.firstSeenAt()).isNotNull();
    var notion = depth.get(1);
    assertThat(notion.count()).isEqualTo(1L);
    assertThat(notion.returningVisitors()).isZero();
    assertThat(notion.returnRatio()).isZero();
    assertThat(notion.firstSeenAt()).isAfter(instagram.firstSeenAt());
  }

  @Test
  void channelDepth_isEmptyWithoutReferrerHosts() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-cd2"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "stcdmt", owner.getId(), null));
    clickRepository.save(
        ClickEventEntity.builder().linkId(link.linkId()).clientIp("5.1.1.1").bot(false).build());

    assertThat(lifecycleReader.channelDepth(link.linkId())).isEmpty();
  }

  @Test
  void channelDepth_ignoresVisitorsWithoutHash() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-cd3"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "stcdgp", owner.getId(), null));
    java.time.Instant t0 = java.time.Instant.now().minus(java.time.Duration.ofDays(2));
    depthClick(link, "instagram.com", null, t0, false);
    depthClick(link, "instagram.com", null, t0.plus(java.time.Duration.ofDays(1)), false);

    var depth = lifecycleReader.channelDepth(link.linkId());

    assertThat(depth).hasSize(1);
    assertThat(depth.get(0).count()).isEqualTo(2L);
    assertThat(depth.get(0).returningVisitors()).isZero();
    assertThat(depth.get(0).returnRatio()).isZero();
  }
}
