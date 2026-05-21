package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import io.queryaudit.junit5.QueryAudit;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@QueryAudit
class LinkManagementServiceTest {

  @Autowired private LinkManagementService service;
  @Autowired private LinkRepository linkRepository;
  @Autowired private UserRepository userRepository;

  @Test
  void updateChangesUrlAndPublishesOgFetchEvent() {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-lmu"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://old.com", "lmu0001", user.getId(), null));

    MyLink result = service.update(user.getId(), "lmu0001", "https://new.com", null, null, null);

    assertThat(result.shortCode()).isEqualTo("lmu0001");
    assertThat(result.originalUrl()).isEqualTo("https://new.com");
  }

  @Test
  void updateExpiresAtAndNoteAndExpiredMessage() {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-lmu2"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://x.com", "lmu0002", user.getId(), null));

    Instant future = Instant.now().plusSeconds(3600);
    service.update(user.getId(), "lmu0002", null, future, "personal note", "Sale ended");

    LinkEntity reloaded = linkRepository.findByShortCode("lmu0002").orElseThrow();
    assertThat(reloaded.getExpiresAt()).isEqualTo(future);
    assertThat(reloaded.getExpiredMessage()).isEqualTo("Sale ended");
  }

  @Test
  void updateOnUnknownShortCodeThrows() {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-lmu3"));
    assertThatThrownBy(
            () -> service.update(user.getId(), "nope9999", "https://x.com", null, null, null))
        .isInstanceOf(LinkNotFoundException.class);
  }

  @Test
  void updateByNonOwnerThrows() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-lmuo"));
    UserEntity attacker = userRepository.save(new UserEntity("a@x.com", "google", "g-lmua"));
    linkRepository.save(new LinkEntity("https://x.com", "lmu0003", owner.getId(), null));

    assertThatThrownBy(
            () -> service.update(attacker.getId(), "lmu0003", "https://hax.com", null, null, null))
        .isInstanceOf(LinkNotOwnedException.class);
  }

  @Test
  void deleteRemovesLink() {
    UserEntity user = userRepository.save(new UserEntity("d@x.com", "google", "g-lmd"));
    linkRepository.save(new LinkEntity("https://x.com", "lmd0001", user.getId(), null));

    service.delete(user.getId(), "lmd0001");

    assertThat(linkRepository.findByShortCode("lmd0001")).isEmpty();
  }

  @Test
  void deleteByNonOwnerThrows() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-lmdo"));
    UserEntity attacker = userRepository.save(new UserEntity("a@x.com", "google", "g-lmda"));
    linkRepository.save(new LinkEntity("https://x.com", "lmd0002", owner.getId(), null));

    assertThatThrownBy(() -> service.delete(attacker.getId(), "lmd0002"))
        .isInstanceOf(LinkNotOwnedException.class);
  }

  @Test
  void bulkDeleteRemovesOwnedSkipsOthersAndReturnsCount() {
    UserEntity me = userRepository.save(new UserEntity("m@x.com", "google", "g-lmbm"));
    UserEntity other = userRepository.save(new UserEntity("o@x.com", "google", "g-lmbo"));
    linkRepository.save(new LinkEntity("https://a.com", "bulk0001", me.getId(), null));
    linkRepository.save(new LinkEntity("https://b.com", "bulk0002", me.getId(), null));
    linkRepository.save(new LinkEntity("https://c.com", "bulk0003", other.getId(), null));

    int removed =
        service.bulkDelete(me.getId(), List.of("bulk0001", "bulk0002", "bulk0003", "nope"));

    assertThat(removed).isEqualTo(2);
    assertThat(linkRepository.findByShortCode("bulk0001")).isEmpty();
    assertThat(linkRepository.findByShortCode("bulk0002")).isEmpty();
    assertThat(linkRepository.findByShortCode("bulk0003")).isPresent();
  }

  @Test
  void bulkDeleteEmptyOrNullReturnsZero() {
    assertThat(service.bulkDelete(1L, null)).isZero();
    assertThat(service.bulkDelete(1L, List.of())).isZero();
  }

  @Test
  void bulkDeleteNoOwnedReturnsZero() {
    UserEntity me = userRepository.save(new UserEntity("m@x.com", "google", "g-lmbe"));
    UserEntity other = userRepository.save(new UserEntity("o@x.com", "google", "g-lmboe"));
    linkRepository.save(new LinkEntity("https://x.com", "bulk0010", other.getId(), null));

    int removed = service.bulkDelete(me.getId(), List.of("bulk0010"));

    assertThat(removed).isZero();
    assertThat(linkRepository.findByShortCode("bulk0010")).isPresent();
  }
}
