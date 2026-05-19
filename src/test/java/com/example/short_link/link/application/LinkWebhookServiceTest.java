package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LinkWebhookServiceTest {

  @Autowired private LinkWebhookService service;
  @Autowired private LinkRepository linkRepository;
  @Autowired private UserRepository userRepository;

  @Test
  void registerListsAndDeletes() {
    UserEntity user = userRepository.save(new UserEntity("wh1@local.test", "google", "g-wh1"));
    linkRepository.save(new LinkEntity("https://example.com/wh1", "wh11111", user.getId(), null));

    LinkWebhookService.IssuedWebhook issued =
        service.register(user.getId(), "wh11111", "https://example.com/hook", "myhook");
    assertThat(issued.secret()).isNotBlank();
    assertThat(issued.id()).isNotNull();

    var list = service.list(user.getId(), "wh11111");
    assertThat(list).hasSize(1);
    assertThat(list.get(0).enabled()).isTrue();

    service.delete(user.getId(), "wh11111", issued.id());
    assertThat(service.list(user.getId(), "wh11111")).isEmpty();
  }

  @Test
  void rejectsPrivateUrl() {
    UserEntity user = userRepository.save(new UserEntity("wh2@local.test", "google", "g-wh2"));
    linkRepository.save(new LinkEntity("https://example.com/wh2", "wh22222", user.getId(), null));

    assertThatThrownBy(
            () -> service.register(user.getId(), "wh22222", "http://127.0.0.1/hook", null))
        .isInstanceOf(InvalidWebhookUrlException.class);
  }

  @Test
  void rejectsNonHttpScheme() {
    UserEntity user = userRepository.save(new UserEntity("wh3@local.test", "google", "g-wh3"));
    linkRepository.save(new LinkEntity("https://example.com/wh3", "wh33333", user.getId(), null));

    assertThatThrownBy(() -> service.register(user.getId(), "wh33333", "javascript:alert(1)", null))
        .isInstanceOf(InvalidWebhookUrlException.class);
  }

  @Test
  void enforcesMaxPerLink() {
    UserEntity user = userRepository.save(new UserEntity("wh4@local.test", "google", "g-wh4"));
    linkRepository.save(new LinkEntity("https://example.com/wh4", "wh44444", user.getId(), null));

    for (int i = 0; i < LinkWebhookService.MAX_PER_LINK; i++) {
      service.register(user.getId(), "wh44444", "https://example.com/hook" + i, "h" + i);
    }
    assertThatThrownBy(
            () -> service.register(user.getId(), "wh44444", "https://example.com/hook9", null))
        .isInstanceOf(TooManyWebhooksException.class);
  }

  @Test
  void cannotTouchOtherUsersWebhook() {
    UserEntity owner = userRepository.save(new UserEntity("wh5@local.test", "google", "g-wh5"));
    UserEntity other = userRepository.save(new UserEntity("wh5b@local.test", "google", "g-wh5b"));
    linkRepository.save(new LinkEntity("https://example.com/wh5", "wh55555", owner.getId(), null));

    LinkWebhookService.IssuedWebhook issued =
        service.register(owner.getId(), "wh55555", "https://example.com/hook", null);

    assertThatThrownBy(() -> service.delete(other.getId(), "wh55555", issued.id()))
        .isInstanceOf(LinkNotOwnedException.class);
  }

  @Test
  void toggleFlipsEnabled() {
    UserEntity user = userRepository.save(new UserEntity("wh6@local.test", "google", "g-wh6"));
    linkRepository.save(new LinkEntity("https://example.com/wh6", "wh66666", user.getId(), null));
    LinkWebhookService.IssuedWebhook issued =
        service.register(user.getId(), "wh66666", "https://example.com/hook", null);

    var disabled = service.toggle(user.getId(), "wh66666", issued.id(), false);
    assertThat(disabled.enabled()).isFalse();
    var enabled = service.toggle(user.getId(), "wh66666", issued.id(), true);
    assertThat(enabled.enabled()).isTrue();
  }

  @Test
  void newWebhookHasCostFriendlyDefaults() {
    UserEntity user = userRepository.save(new UserEntity("wh7@local.test", "google", "g-wh7"));
    linkRepository.save(new LinkEntity("https://example.com/wh7", "wh77777", user.getId(), null));
    var issued = service.register(user.getId(), "wh77777", "https://example.com/hook", null);
    var summary = service.list(user.getId(), "wh77777").get(0);

    assertThat(summary.id()).isEqualTo(issued.id());
    assertThat(summary.includeBots()).isFalse();
    assertThat(summary.sampleRate()).isEqualTo(100);
    assertThat(summary.batchEnabled()).isFalse();
    assertThat(summary.dailyQuota()).isNull();
    assertThat(summary.consecutiveFailures()).isZero();
    assertThat(summary.autoDisabledReason()).isNull();
    assertThat(summary.referrerHostFilter()).isNull();
    assertThat(summary.utmSourceFilter()).isNull();
  }

  @Test
  void updateConfigPersistsAllFields() {
    UserEntity user = userRepository.save(new UserEntity("wh8@local.test", "google", "g-wh8"));
    linkRepository.save(new LinkEntity("https://example.com/wh8", "wh88888", user.getId(), null));
    var issued = service.register(user.getId(), "wh88888", "https://example.com/hook", null);

    var updated =
        service.updateConfig(
            user.getId(),
            "wh88888",
            issued.id(),
            new LinkWebhookService.ConfigPatch(true, 25, true, 1000, "twitter.com", "instagram"));

    assertThat(updated.includeBots()).isTrue();
    assertThat(updated.sampleRate()).isEqualTo(25);
    assertThat(updated.batchEnabled()).isTrue();
    assertThat(updated.dailyQuota()).isEqualTo(1000);
    assertThat(updated.referrerHostFilter()).isEqualTo("twitter.com");
    assertThat(updated.utmSourceFilter()).isEqualTo("instagram");
  }

  @Test
  void updateConfigClampsSampleRate() {
    UserEntity user = userRepository.save(new UserEntity("wh9@local.test", "google", "g-wh9"));
    linkRepository.save(new LinkEntity("https://example.com/wh9", "wh99999", user.getId(), null));
    var issued = service.register(user.getId(), "wh99999", "https://example.com/hook", null);

    assertThatThrownBy(
            () ->
                service.updateConfig(
                    user.getId(),
                    "wh99999",
                    issued.id(),
                    new LinkWebhookService.ConfigPatch(null, 0, null, null, null, null)))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () ->
                service.updateConfig(
                    user.getId(),
                    "wh99999",
                    issued.id(),
                    new LinkWebhookService.ConfigPatch(null, 101, null, null, null, null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void blankFiltersAreNormalizedToNull() {
    UserEntity user = userRepository.save(new UserEntity("wh10@local.test", "google", "g-wh10"));
    linkRepository.save(new LinkEntity("https://example.com/wh10", "wh10000", user.getId(), null));
    var issued = service.register(user.getId(), "wh10000", "https://example.com/hook", null);

    service.updateConfig(
        user.getId(),
        "wh10000",
        issued.id(),
        new LinkWebhookService.ConfigPatch(null, null, null, null, "abc", "def"));
    var withFilters = service.list(user.getId(), "wh10000").get(0);
    assertThat(withFilters.referrerHostFilter()).isEqualTo("abc");
    assertThat(withFilters.utmSourceFilter()).isEqualTo("def");

    service.updateConfig(
        user.getId(),
        "wh10000",
        issued.id(),
        new LinkWebhookService.ConfigPatch(null, null, null, null, "  ", "  "));
    var cleared = service.list(user.getId(), "wh10000").get(0);
    assertThat(cleared.referrerHostFilter()).isNull();
    assertThat(cleared.utmSourceFilter()).isNull();
  }

  @Test
  void detectsAndPersistsDiscordFormatOnRegister() {
    UserEntity user = userRepository.save(new UserEntity("wh12@local.test", "google", "g-wh12"));
    linkRepository.save(new LinkEntity("https://example.com/wh12", "wh12000", user.getId(), null));

    var issued =
        service.register(
            user.getId(), "wh12000", "https://discord.com/api/webhooks/123/abc-secret", null);
    assertThat(issued.format()).isEqualTo(WebhookFormat.DISCORD);

    var summary = service.list(user.getId(), "wh12000").get(0);
    assertThat(summary.format()).isEqualTo(WebhookFormat.DISCORD);
  }

  @Test
  void detectsSlackFormatOnRegister() {
    UserEntity user = userRepository.save(new UserEntity("wh13@local.test", "google", "g-wh13"));
    linkRepository.save(new LinkEntity("https://example.com/wh13", "wh13000", user.getId(), null));

    var issued =
        service.register(
            user.getId(), "wh13000", "https://hooks.slack.com/services/T0/B0/secret", null);
    assertThat(issued.format()).isEqualTo(WebhookFormat.SLACK);
  }

  @Test
  void defaultsToGenericFormatForOwnEndpoint() {
    UserEntity user = userRepository.save(new UserEntity("wh14@local.test", "google", "g-wh14"));
    linkRepository.save(new LinkEntity("https://example.com/wh14", "wh14000", user.getId(), null));

    var issued = service.register(user.getId(), "wh14000", "https://example.com/hook", null);
    assertThat(issued.format()).isEqualTo(WebhookFormat.GENERIC);
  }

  @Test
  void zeroDailyQuotaIsTreatedAsNoCap() {
    UserEntity user = userRepository.save(new UserEntity("wh11@local.test", "google", "g-wh11"));
    linkRepository.save(new LinkEntity("https://example.com/wh11", "wh11111h", user.getId(), null));
    var issued = service.register(user.getId(), "wh11111h", "https://example.com/hook", null);

    service.updateConfig(
        user.getId(),
        "wh11111h",
        issued.id(),
        new LinkWebhookService.ConfigPatch(null, null, null, 0, null, null));
    assertThat(service.list(user.getId(), "wh11111h").get(0).dailyQuota()).isNull();
  }
}
