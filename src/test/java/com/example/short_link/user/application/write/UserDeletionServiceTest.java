package com.example.short_link.user.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.user.domain.RefreshTokenStore;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserDeletionServiceTest {

  @Autowired private UserDeletionService deletionService;
  @Autowired private UserRepository userRepository;
  @Autowired private LinkRepository linkRepository;
  @Autowired private ClickEventRepository clickEventRepository;
  @Autowired private RefreshTokenStore refreshTokenStore;

  @Test
  void cascadesAcrossUserLinksAndClicks() {
    UserEntity user = userRepository.save(new UserEntity("del@example.com", "google", "g-del"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com/x", "del0001", user.getId(), null));
    ClickEventEntity click =
        clickEventRepository.save(
            ClickEventEntity.builder().linkId(link.linkId()).bot(false).build());
    refreshTokenStore.save(user.getId(), "jti-1", java.time.Duration.ofMinutes(5));

    // deleteAccount is a soft delete — it just flags the row and revokes refresh tokens.
    // hardDelete is what the scheduled cleanup eventually runs to actually purge data.
    deletionService.deleteAccount(user.getId());
    assertThat(userRepository.findById(user.getId())).isPresent();
    assertThat(refreshTokenStore.exists(user.getId(), "jti-1")).isFalse();

    deletionService.hardDelete(user.getId());
    assertThat(userRepository.findById(user.getId())).isEmpty();
    assertThat(linkRepository.findByShortCode(new ShortCode("del0001"))).isEmpty();
    assertThat(clickEventRepository.findById(click.getId())).isEmpty();
  }

  @Test
  void leavesAnonymousLinksUntouched() {
    UserEntity user = userRepository.save(new UserEntity("del2@example.com", "google", "g-del2"));
    LinkEntity owned =
        linkRepository.save(new LinkEntity("https://example.com/y", "del0002", user.getId(), null));
    LinkEntity anonymous = linkRepository.save(new LinkEntity("https://example.com/z", "anon0001"));

    deletionService.hardDelete(user.getId());

    assertThat(linkRepository.findByShortCode(new ShortCode("del0002"))).isEmpty();
    assertThat(linkRepository.findByShortCode(new ShortCode("anon0001"))).isPresent();
  }

  @Test
  void throwsForUnknownUser() {
    assertThatThrownBy(() -> deletionService.deleteAccount(9_999_999L))
        .isInstanceOf(UserException.class);
  }
}
