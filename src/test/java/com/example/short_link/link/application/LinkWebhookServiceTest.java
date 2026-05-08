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
}
