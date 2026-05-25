package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.link.application.dto.MyLink;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
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
class LinkManagementUseCasesTest {

  @Autowired private UpdateLinkUseCase updateLink;
  @Autowired private DeleteLinkUseCase deleteLink;
  @Autowired private BulkDeleteLinksUseCase bulkDeleteLinks;
  @Autowired private LinkRepository linkRepository;
  @Autowired private UserRepository userRepository;

  @Test
  void updateChangesUrlAndPublishesOgFetchEvent() {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-lmu"));
    linkRepository.save(new LinkEntity("https://old.com", "lmu0001", user.getId(), null));

    MyLink result =
        updateLink.execute(
            new UpdateLinkCommand(user.getId(), "lmu0001", "https://new.com", null, null, null));

    assertThat(result.shortCode()).isEqualTo("lmu0001");
    assertThat(result.originalUrl()).isEqualTo("https://new.com");
  }

  @Test
  void updateExpiresAtAndNoteAndExpiredMessage() {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-lmu2"));
    linkRepository.save(new LinkEntity("https://x.com", "lmu0002", user.getId(), null));

    Instant future = Instant.now().plusSeconds(3600);
    updateLink.execute(
        new UpdateLinkCommand(
            user.getId(), "lmu0002", null, future, "personal note", "Sale ended"));

    LinkEntity reloaded = linkRepository.findByShortCode("lmu0002").orElseThrow();
    assertThat(reloaded.getExpiresAt()).isEqualTo(future);
    assertThat(reloaded.getExpiredMessage()).isEqualTo("Sale ended");
  }

  @Test
  void updateOnUnknownShortCodeThrows() {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-lmu3"));
    assertThatThrownBy(
            () ->
                updateLink.execute(
                    new UpdateLinkCommand(
                        user.getId(), "nope9999", "https://x.com", null, null, null)))
        .isInstanceOf(LinkException.class);
  }

  @Test
  void updateByNonOwnerThrows() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-lmuo"));
    UserEntity attacker = userRepository.save(new UserEntity("a@x.com", "google", "g-lmua"));
    linkRepository.save(new LinkEntity("https://x.com", "lmu0003", owner.getId(), null));

    assertThatThrownBy(
            () ->
                updateLink.execute(
                    new UpdateLinkCommand(
                        attacker.getId(), "lmu0003", "https://hax.com", null, null, null)))
        .isInstanceOf(LinkException.class);
  }

  @Test
  void deleteRemovesLink() {
    UserEntity user = userRepository.save(new UserEntity("d@x.com", "google", "g-lmd"));
    linkRepository.save(new LinkEntity("https://x.com", "lmd0001", user.getId(), null));

    deleteLink.execute(new DeleteLinkCommand(user.getId(), "lmd0001"));

    assertThat(linkRepository.findByShortCode("lmd0001")).isEmpty();
  }

  @Test
  void deleteByNonOwnerThrows() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-lmdo"));
    UserEntity attacker = userRepository.save(new UserEntity("a@x.com", "google", "g-lmda"));
    linkRepository.save(new LinkEntity("https://x.com", "lmd0002", owner.getId(), null));

    assertThatThrownBy(() -> deleteLink.execute(new DeleteLinkCommand(attacker.getId(), "lmd0002")))
        .isInstanceOf(LinkException.class);
  }

  @Test
  void bulkDeleteRemovesOwnedSkipsOthersAndReturnsCount() {
    UserEntity me = userRepository.save(new UserEntity("m@x.com", "google", "g-lmbm"));
    UserEntity other = userRepository.save(new UserEntity("o@x.com", "google", "g-lmbo"));
    linkRepository.save(new LinkEntity("https://a.com", "bulk0001", me.getId(), null));
    linkRepository.save(new LinkEntity("https://b.com", "bulk0002", me.getId(), null));
    linkRepository.save(new LinkEntity("https://c.com", "bulk0003", other.getId(), null));

    int removed =
        bulkDeleteLinks.execute(
            BulkDeleteLinksCommand.of(
                me.getId(), List.of("bulk0001", "bulk0002", "bulk0003", "nope")));

    assertThat(removed).isEqualTo(2);
    assertThat(linkRepository.findByShortCode("bulk0001")).isEmpty();
    assertThat(linkRepository.findByShortCode("bulk0002")).isEmpty();
    assertThat(linkRepository.findByShortCode("bulk0003")).isPresent();
  }

  @Test
  void bulkDeleteEmptyOrNullReturnsZero() {
    assertThat(bulkDeleteLinks.execute(BulkDeleteLinksCommand.of(1L, null))).isZero();
    assertThat(bulkDeleteLinks.execute(BulkDeleteLinksCommand.of(1L, List.of()))).isZero();
  }

  @Test
  void bulkDeleteNoOwnedReturnsZero() {
    UserEntity me = userRepository.save(new UserEntity("m@x.com", "google", "g-lmbe"));
    UserEntity other = userRepository.save(new UserEntity("o@x.com", "google", "g-lmboe"));
    linkRepository.save(new LinkEntity("https://x.com", "bulk0010", other.getId(), null));

    int removed =
        bulkDeleteLinks.execute(BulkDeleteLinksCommand.of(me.getId(), List.of("bulk0010")));

    assertThat(removed).isZero();
    assertThat(linkRepository.findByShortCode("bulk0010")).isPresent();
  }
}
