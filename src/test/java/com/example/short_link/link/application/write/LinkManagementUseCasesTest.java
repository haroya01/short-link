package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.link.application.dto.MyLink;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
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
            new UpdateLinkCommand(
                user.getId(), new ShortCode("lmu0001"), "https://new.com", null, null, null));

    assertThat(result.shortCode().value()).isEqualTo("lmu0001");
    assertThat(result.originalUrl()).isEqualTo("https://new.com");
  }

  @Test
  void updateExpiresAtAndNoteAndExpiredMessage() {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-lmu2"));
    linkRepository.save(new LinkEntity("https://x.com", "lmu0002", user.getId(), null));

    Instant future = Instant.now().plusSeconds(3600);
    updateLink.execute(
        new UpdateLinkCommand(
            user.getId(), new ShortCode("lmu0002"), null, future, "personal note", "Sale ended"));

    LinkEntity reloaded = linkRepository.findByShortCode(new ShortCode("lmu0002")).orElseThrow();
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
                        user.getId(),
                        new ShortCode("nope9999"),
                        "https://x.com",
                        null,
                        null,
                        null)))
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
                        attacker.getId(),
                        new ShortCode("lmu0003"),
                        "https://hax.com",
                        null,
                        null,
                        null)))
        .isInstanceOf(LinkException.class);
  }

  @Test
  void updateToOwnShortLinkHostThrowsAndKeepsUrl() {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-lmu4"));
    linkRepository.save(new LinkEntity("https://old.com", "lmu0004", user.getId(), null));

    // 테스트 프로파일 base-url(http://localhost:8080)의 호스트로 갱신 시도 — 생성 경로와 같은
    // 자기참조 거부를 타야 갱신으로 리다이렉트 루프를 만드는 우회가 막힌다.
    assertThatThrownBy(
            () ->
                updateLink.execute(
                    new UpdateLinkCommand(
                        user.getId(),
                        new ShortCode("lmu0004"),
                        "http://localhost:8080/abc123",
                        null,
                        null,
                        null)))
        .isInstanceOfSatisfying(
            LinkException.class,
            e -> assertThat(e.errorCode()).isEqualTo(LinkErrorCode.SELF_REFERENCING_URL));

    LinkEntity reloaded = linkRepository.findByShortCode(new ShortCode("lmu0004")).orElseThrow();
    assertThat(reloaded.getOriginalUrl()).isEqualTo("https://old.com");
  }

  @Test
  void deleteRemovesLink() {
    UserEntity user = userRepository.save(new UserEntity("d@x.com", "google", "g-lmd"));
    linkRepository.save(new LinkEntity("https://x.com", "lmd0001", user.getId(), null));

    deleteLink.execute(new DeleteLinkCommand(user.getId(), new ShortCode("lmd0001")));

    assertThat(linkRepository.findByShortCode(new ShortCode("lmd0001"))).isEmpty();
  }

  @Test
  void deleteByNonOwnerThrows() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-lmdo"));
    UserEntity attacker = userRepository.save(new UserEntity("a@x.com", "google", "g-lmda"));
    linkRepository.save(new LinkEntity("https://x.com", "lmd0002", owner.getId(), null));

    assertThatThrownBy(
            () ->
                deleteLink.execute(
                    new DeleteLinkCommand(attacker.getId(), new ShortCode("lmd0002"))))
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
    assertThat(linkRepository.findByShortCode(new ShortCode("bulk0001"))).isEmpty();
    assertThat(linkRepository.findByShortCode(new ShortCode("bulk0002"))).isEmpty();
    assertThat(linkRepository.findByShortCode(new ShortCode("bulk0003"))).isPresent();
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
    assertThat(linkRepository.findByShortCode(new ShortCode("bulk0010"))).isPresent();
  }
}
