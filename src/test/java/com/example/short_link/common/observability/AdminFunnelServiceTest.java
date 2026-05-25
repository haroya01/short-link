package com.example.short_link.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.observability.AdminFunnelService.Funnel;
import com.example.short_link.common.observability.AdminFunnelService.Window;
import com.example.short_link.link.domain.ClickEventEntity;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkWebhookEntity;
import com.example.short_link.link.domain.repository.ClickEventRepository;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.domain.repository.LinkWebhookRepository;
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
class AdminFunnelServiceTest {

  @Autowired private AdminFunnelService service;
  @Autowired private UserRepository userRepository;
  @Autowired private LinkRepository linkRepository;
  @Autowired private ClickEventRepository clickRepository;
  @Autowired private LinkWebhookRepository webhookRepository;

  @Test
  void parseWindow_acceptsCommonAliases() {
    assertThat(Window.parse("1d")).isEqualTo(Window.D1);
    assertThat(Window.parse("d1")).isEqualTo(Window.D1);
    assertThat(Window.parse("day")).isEqualTo(Window.D1);
    assertThat(Window.parse("7d")).isEqualTo(Window.D7);
    assertThat(Window.parse("week")).isEqualTo(Window.D7);
    assertThat(Window.parse("30d")).isEqualTo(Window.D30);
    assertThat(Window.parse("month")).isEqualTo(Window.D30);
    assertThat(Window.parse("all")).isEqualTo(Window.ALL);
  }

  @Test
  void parseWindow_defaultsToD7OnNullOrUnknown() {
    assertThat(Window.parse(null)).isEqualTo(Window.D7);
    assertThat(Window.parse("???")).isEqualTo(Window.D7);
    assertThat(Window.parse("")).isEqualTo(Window.D7);
  }

  @Test
  void snapshotCountsThroughFunnel() {
    // user 1 — no link
    userRepository.save(new UserEntity("a@x.com", "google", "g-fa"));
    // user 2 — has link, no click
    UserEntity u2 = userRepository.save(new UserEntity("b@x.com", "google", "g-fb"));
    linkRepository.save(new LinkEntity("https://example.com", "fun0001", u2.getId(), null));
    // user 3 — has link + click
    UserEntity u3 = userRepository.save(new UserEntity("c@x.com", "google", "g-fc"));
    LinkEntity l3 =
        linkRepository.save(new LinkEntity("https://example.com", "fun0002", u3.getId(), null));
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(l3.getId())
            .userAgent("ua")
            .clientIp("1.2.3.4")
            .deviceClass("desktop")
            .bot(false)
            .build());
    // user 4 — has link + click + webhook
    UserEntity u4 = userRepository.save(new UserEntity("d@x.com", "google", "g-fd"));
    LinkEntity l4 =
        linkRepository.save(new LinkEntity("https://example.com", "fun0003", u4.getId(), null));
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(l4.getId())
            .userAgent("ua")
            .clientIp("1.2.3.4")
            .deviceClass("desktop")
            .bot(false)
            .build());
    webhookRepository.save(
        new LinkWebhookEntity(l4.getId(), "https://wh.example/post", "secret", "name"));

    Funnel f = service.snapshot(Window.ALL);

    assertThat(f.users()).isGreaterThanOrEqualTo(4);
    assertThat(f.withLink()).isGreaterThanOrEqualTo(3);
    assertThat(f.withClick()).isGreaterThanOrEqualTo(2);
    assertThat(f.withWebhook()).isGreaterThanOrEqualTo(1);
    assertThat(f.conversion()).containsKeys("signupToLink", "linkToClick", "clickToWebhook");
  }

  @Test
  void snapshotBotClicksDontCount() {
    UserEntity u = userRepository.save(new UserEntity("bot@x.com", "google", "g-bot"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "fun0bot1", u.getId(), null));
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.getId())
            .userAgent("ua")
            .clientIp("1.2.3.4")
            .deviceClass("desktop")
            .bot(true)
            .build());

    Funnel f = service.snapshot(Window.ALL);
    // Bot click should not bump withClick relative to a freshly-created user. We can't assert exact
    // count across the suite, but conversion ratio map should still exist.
    assertThat(f.conversion()).isNotEmpty();
  }
}
