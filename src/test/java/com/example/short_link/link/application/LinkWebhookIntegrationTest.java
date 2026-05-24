package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.link.application.read.LinkWebhookQueryService;
import com.example.short_link.link.application.write.DeleteLinkWebhookCommand;
import com.example.short_link.link.application.write.DeleteLinkWebhookUseCase;
import com.example.short_link.link.application.write.ReDetectWebhookFormatsUseCase;
import com.example.short_link.link.application.write.RegisterLinkWebhookCommand;
import com.example.short_link.link.application.write.RegisterLinkWebhookUseCase;
import com.example.short_link.link.application.write.ToggleLinkWebhookCommand;
import com.example.short_link.link.application.write.ToggleLinkWebhookUseCase;
import com.example.short_link.link.application.write.UpdateLinkWebhookConfigCommand;
import com.example.short_link.link.application.write.UpdateLinkWebhookConfigUseCase;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.link.domain.LinkWebhookEntity;
import com.example.short_link.link.domain.LinkWebhookRepository;
import com.example.short_link.link.exception.InvalidWebhookUrlException;
import com.example.short_link.link.exception.LinkNotOwnedException;
import com.example.short_link.link.exception.TooManyWebhooksException;
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
class LinkWebhookIntegrationTest {

  @Autowired private LinkWebhookQueryService query;
  @Autowired private RegisterLinkWebhookUseCase registerUseCase;
  @Autowired private ToggleLinkWebhookUseCase toggleUseCase;
  @Autowired private UpdateLinkWebhookConfigUseCase updateConfigUseCase;
  @Autowired private DeleteLinkWebhookUseCase deleteUseCase;
  @Autowired private ReDetectWebhookFormatsUseCase reDetectUseCase;
  @Autowired private LinkRepository linkRepository;
  @Autowired private LinkWebhookRepository webhookRepository;
  @Autowired private UserRepository userRepository;

  private IssuedWebhook register(Long userId, String shortCode, String url, String name) {
    return registerUseCase.execute(new RegisterLinkWebhookCommand(userId, shortCode, url, name));
  }

  @Test
  void registerListsAndDeletes() {
    UserEntity user = userRepository.save(new UserEntity("wh1@local.test", "google", "g-wh1"));
    linkRepository.save(new LinkEntity("https://example.com/wh1", "wh11111", user.getId(), null));

    IssuedWebhook issued = register(user.getId(), "wh11111", "https://example.com/hook", "myhook");
    assertThat(issued.secret()).isNotBlank();
    assertThat(issued.id()).isNotNull();

    var list = query.list(user.getId(), "wh11111");
    assertThat(list).hasSize(1);
    assertThat(list.get(0).enabled()).isTrue();

    deleteUseCase.execute(new DeleteLinkWebhookCommand(user.getId(), "wh11111", issued.id()));
    assertThat(query.list(user.getId(), "wh11111")).isEmpty();
  }

  @Test
  void rejectsPrivateUrl() {
    UserEntity user = userRepository.save(new UserEntity("wh2@local.test", "google", "g-wh2"));
    linkRepository.save(new LinkEntity("https://example.com/wh2", "wh22222", user.getId(), null));

    assertThatThrownBy(() -> register(user.getId(), "wh22222", "http://127.0.0.1/hook", null))
        .isInstanceOf(InvalidWebhookUrlException.class);
  }

  @Test
  void rejectsNonHttpScheme() {
    UserEntity user = userRepository.save(new UserEntity("wh3@local.test", "google", "g-wh3"));
    linkRepository.save(new LinkEntity("https://example.com/wh3", "wh33333", user.getId(), null));

    assertThatThrownBy(() -> register(user.getId(), "wh33333", "javascript:alert(1)", null))
        .isInstanceOf(InvalidWebhookUrlException.class);
  }

  @Test
  void enforcesMaxPerLink() {
    UserEntity user = userRepository.save(new UserEntity("wh4@local.test", "google", "g-wh4"));
    linkRepository.save(new LinkEntity("https://example.com/wh4", "wh44444", user.getId(), null));

    for (int i = 0; i < RegisterLinkWebhookUseCase.MAX_PER_LINK; i++) {
      register(user.getId(), "wh44444", "https://example.com/hook" + i, "h" + i);
    }
    assertThatThrownBy(() -> register(user.getId(), "wh44444", "https://example.com/hook9", null))
        .isInstanceOf(TooManyWebhooksException.class);
  }

  @Test
  void cannotTouchOtherUsersWebhook() {
    UserEntity owner = userRepository.save(new UserEntity("wh5@local.test", "google", "g-wh5"));
    UserEntity other = userRepository.save(new UserEntity("wh5b@local.test", "google", "g-wh5b"));
    linkRepository.save(new LinkEntity("https://example.com/wh5", "wh55555", owner.getId(), null));

    IssuedWebhook issued = register(owner.getId(), "wh55555", "https://example.com/hook", null);

    assertThatThrownBy(
            () ->
                deleteUseCase.execute(
                    new DeleteLinkWebhookCommand(other.getId(), "wh55555", issued.id())))
        .isInstanceOf(LinkNotOwnedException.class);
  }

  @Test
  void toggleFlipsEnabled() {
    UserEntity user = userRepository.save(new UserEntity("wh6@local.test", "google", "g-wh6"));
    linkRepository.save(new LinkEntity("https://example.com/wh6", "wh66666", user.getId(), null));
    IssuedWebhook issued = register(user.getId(), "wh66666", "https://example.com/hook", null);

    var disabled =
        toggleUseCase.execute(
            new ToggleLinkWebhookCommand(user.getId(), "wh66666", issued.id(), false));
    assertThat(disabled.enabled()).isFalse();
    var enabled =
        toggleUseCase.execute(
            new ToggleLinkWebhookCommand(user.getId(), "wh66666", issued.id(), true));
    assertThat(enabled.enabled()).isTrue();
  }

  @Test
  void newWebhookHasCostFriendlyDefaults() {
    UserEntity user = userRepository.save(new UserEntity("wh7@local.test", "google", "g-wh7"));
    linkRepository.save(new LinkEntity("https://example.com/wh7", "wh77777", user.getId(), null));
    var issued = register(user.getId(), "wh77777", "https://example.com/hook", null);
    var summary = query.list(user.getId(), "wh77777").get(0);

    assertThat(summary.id()).isEqualTo(issued.id());
    assertThat(summary.includeBots()).isFalse();
    assertThat(summary.sampleRate()).isEqualTo(100);
    assertThat(summary.batchEnabled()).isFalse();
    assertThat(summary.dailyQuota()).isNull();
    assertThat(summary.consecutiveFailures()).isZero();
    assertThat(summary.autoDisabledReason()).isNull();
  }

  @Test
  void updateConfigPersistsAllFields() {
    UserEntity user = userRepository.save(new UserEntity("wh8@local.test", "google", "g-wh8"));
    linkRepository.save(new LinkEntity("https://example.com/wh8", "wh88888", user.getId(), null));
    var issued = register(user.getId(), "wh88888", "https://example.com/hook", null);

    var updated =
        updateConfigUseCase.execute(
            new UpdateLinkWebhookConfigCommand(
                user.getId(),
                "wh88888",
                issued.id(),
                true,
                25,
                true,
                1000,
                "twitter.com",
                "instagram"));

    assertThat(updated.includeBots()).isTrue();
    assertThat(updated.sampleRate()).isEqualTo(25);
    assertThat(updated.batchEnabled()).isTrue();
    assertThat(updated.dailyQuota()).isEqualTo(1000);
    assertThat(updated.referrerHostFilter()).isEqualTo("twitter.com");
    assertThat(updated.utmSourceFilter()).isEqualTo("instagram");
  }

  @Test
  void updateConfigRejectsBadSampleRate() {
    UserEntity user = userRepository.save(new UserEntity("wh9@local.test", "google", "g-wh9"));
    linkRepository.save(new LinkEntity("https://example.com/wh9", "wh99999", user.getId(), null));
    var issued = register(user.getId(), "wh99999", "https://example.com/hook", null);

    assertThatThrownBy(
            () ->
                updateConfigUseCase.execute(
                    new UpdateLinkWebhookConfigCommand(
                        user.getId(), "wh99999", issued.id(), null, 0, null, null, null, null)))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () ->
                updateConfigUseCase.execute(
                    new UpdateLinkWebhookConfigCommand(
                        user.getId(), "wh99999", issued.id(), null, 101, null, null, null, null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void detectsAndPersistsDiscordFormatOnRegister() {
    UserEntity user = userRepository.save(new UserEntity("wh12@local.test", "google", "g-wh12"));
    linkRepository.save(new LinkEntity("https://example.com/wh12", "wh12000", user.getId(), null));

    var issued =
        register(user.getId(), "wh12000", "https://discord.com/api/webhooks/123/abc-secret", null);
    assertThat(issued.format()).isEqualTo(WebhookFormat.DISCORD);

    var summary = query.list(user.getId(), "wh12000").get(0);
    assertThat(summary.format()).isEqualTo(WebhookFormat.DISCORD);
  }

  @Test
  void reDetectFormatsReactivatesAutoDisabledDiscordRow() {
    UserEntity user = userRepository.save(new UserEntity("rd1@local.test", "google", "g-rd1"));
    LinkEntity link =
        linkRepository.save(
            new LinkEntity("https://example.com/rd1", "rd11111", user.getId(), null));

    LinkWebhookEntity hook =
        webhookRepository.save(
            new LinkWebhookEntity(
                link.getId(),
                "https://discord.com/api/webhooks/1/abc",
                "secret-rd1",
                "discord",
                WebhookFormat.GENERIC));
    for (int i = 0; i < LinkWebhookEntity.AUTO_DISABLE_FAILURE_THRESHOLD; i++) {
      hook.recordFailure(400, "Cannot send an empty message");
    }
    webhookRepository.saveAndFlush(hook);
    assertThat(hook.isEnabled()).isFalse();

    WebhookReDetectResult result = reDetectUseCase.execute();
    assertThat(result.formatChanged()).isGreaterThanOrEqualTo(1);
    assertThat(result.reactivated()).isGreaterThanOrEqualTo(1);

    LinkWebhookEntity after = webhookRepository.findById(hook.getId()).orElseThrow();
    assertThat(after.getFormat()).isEqualTo(WebhookFormat.DISCORD);
    assertThat(after.isEnabled()).isTrue();
  }

  @Test
  void reDetectFormatsLeavesGenericAutoDisabledAlone() {
    UserEntity user = userRepository.save(new UserEntity("rd2@local.test", "google", "g-rd2"));
    LinkEntity link =
        linkRepository.save(
            new LinkEntity("https://example.com/rd2", "rd22222", user.getId(), null));

    LinkWebhookEntity hook =
        webhookRepository.save(
            new LinkWebhookEntity(
                link.getId(),
                "https://hook.example.com/in",
                "secret-rd2",
                null,
                WebhookFormat.GENERIC));
    for (int i = 0; i < LinkWebhookEntity.AUTO_DISABLE_FAILURE_THRESHOLD; i++) {
      hook.recordFailure(500, "receiver unreachable");
    }
    webhookRepository.saveAndFlush(hook);

    WebhookReDetectResult result = reDetectUseCase.execute();
    LinkWebhookEntity after = webhookRepository.findById(hook.getId()).orElseThrow();
    assertThat(after.getFormat()).isEqualTo(WebhookFormat.GENERIC);
    assertThat(after.isEnabled()).isFalse();
    assertThat(result.reactivated()).isZero();
  }
}
