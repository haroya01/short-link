package com.example.short_link.profile.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.short_link.common.post.PublishedPostCountReader;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.domain.repository.LinkRepository.ProfileLinkRow;
import com.example.short_link.link.stats.domain.repository.ClickTotalsReadRepository;
import com.example.short_link.profile.application.PublicProfile;
import com.example.short_link.profile.application.PublicProfileSnapshot;
import com.example.short_link.profile.application.PublicProfileSnapshot.LinkWindow;
import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.ProfileBlockType;
import com.example.short_link.profile.domain.UsernameHistoryEntity;
import com.example.short_link.profile.domain.repository.ProfileBlockRepository;
import com.example.short_link.profile.domain.repository.UsernameHistoryRepository;
import com.example.short_link.profile.exception.ProfileException;
import com.example.short_link.support.TestEntities;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublicProfileLoaderTest {

  private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");

  @Mock private UserRepository userRepository;
  @Mock private LinkRepository linkRepository;
  @Mock private ClickTotalsReadRepository clickRepository;
  @Mock private UsernameHistoryRepository usernameHistoryRepository;
  @Mock private ProfileBlockRepository profileBlockRepository;
  @Mock private PublishedPostCountReader postCountReader;
  @Mock private ShortLinkUrlBuilder urlBuilder;

  private PublicProfileLoader loader;

  @BeforeEach
  void setUp() {
    loader =
        new PublicProfileLoader(
            userRepository,
            linkRepository,
            clickRepository,
            usernameHistoryRepository,
            profileBlockRepository,
            postCountReader,
            urlBuilder,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private record Row(LinkEntity getLink, Instant getOpensAt, Boolean getPasswordRequired)
      implements ProfileLinkRow {}

  private UserEntity alice() {
    UserEntity u = new UserEntity("u@x.com", "google", "g-7");
    TestEntities.withId(u, 7L);
    u.claimUsername("alice");
    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));
    return u;
  }

  private static LinkEntity profileLink(long id, String code, int order) {
    LinkEntity link = new LinkEntity("https://secret.example.com/" + code, code, 7L, null);
    TestEntities.withId(link, id);
    link.setProfileOrder(order);
    return link;
  }

  @Test
  void anUnknownUsernameIsNotFound() {
    when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
    when(usernameHistoryRepository.findFirstByOldUsernameAndExpiresAtAfter("ghost", NOW))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> loader.load("ghost")).isInstanceOf(ProfileException.class);
  }

  @Test
  void aDeletedAccountIsNotFound() {
    UserEntity u = alice();
    u.softDelete();
    when(usernameHistoryRepository.findFirstByOldUsernameAndExpiresAtAfter("alice", NOW))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> loader.load("alice")).isInstanceOf(ProfileException.class);
  }

  @Test
  void aNullUsernameIsNotFound() {
    when(userRepository.findByUsername("")).thenReturn(Optional.empty());
    when(usernameHistoryRepository.findFirstByOldUsernameAndExpiresAtAfter("", NOW))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> loader.load(null)).isInstanceOf(ProfileException.class);
  }

  @Test
  void anOldUsernameResolvesThroughItsHistory() {
    UserEntity u = new UserEntity("u@x.com", "google", "g-7");
    TestEntities.withId(u, 7L);
    u.claimUsername("now");
    when(userRepository.findByUsername("old")).thenReturn(Optional.empty());
    when(usernameHistoryRepository.findFirstByOldUsernameAndExpiresAtAfter("old", NOW))
        .thenReturn(Optional.of(new UsernameHistoryEntity(7L, "old", NOW.plusSeconds(60))));
    when(userRepository.findById(7L)).thenReturn(Optional.of(u));
    when(linkRepository.findPublicProfileLinks(7L)).thenReturn(List.of());
    when(profileBlockRepository.findAllByUserIdOrderByProfileOrderAsc(7L)).thenReturn(List.of());

    assertThat(loader.load("old").profile().username()).isEqualTo("now");
  }

  @Test
  void linksAndBlocksKeepTheirProfileOrder() {
    alice();
    ProfileBlockEntity divider = new ProfileBlockEntity(7L, ProfileBlockType.DIVIDER, null, 2);
    TestEntities.withId(divider, 11L);
    ProfileBlockEntity textBlock = new ProfileBlockEntity(7L, ProfileBlockType.TEXT, "hello", 3);
    TestEntities.withId(textBlock, 12L);
    when(linkRepository.findPublicProfileLinks(7L))
        .thenReturn(List.of(new Row(profileLink(1L, "abc", 1), null, false)));
    when(profileBlockRepository.findAllByUserIdOrderByProfileOrderAsc(7L))
        .thenReturn(List.of(divider, textBlock));
    when(clickRepository.countsByLinkIds(any())).thenReturn(List.of());
    when(urlBuilder.build(new ShortCode("abc"))).thenReturn("https://kurl/abc");

    PublicProfileSnapshot snapshot = loader.load("alice");

    assertThat(snapshot.profile().entries())
        .extracting(PublicProfile.ProfileEntry::kind)
        .containsExactly("LINK", "DIVIDER", "TEXT");
    assertThat(snapshot.profile().entries().get(0).passwordProtected()).isFalse();
    assertThat(snapshot.profile().entries().get(1).passwordProtected()).isNull();
    assertThat(snapshot.linkWindows()).isEmpty();
  }

  @Test
  void aPasswordLinkKeepsOnlyItsShortUrlAndTheOwnersTitle() {
    alice();
    LinkEntity scraped = profileLink(1L, "pwd0001", 1);
    scraped.applyOgMetadata("Scraped title", "desc", "https://secret.example.com/og.jpg", NOW);
    LinkEntity titled = profileLink(2L, "pwd0002", 2);
    titled.applyOgMetadata("Scraped title", "desc", "https://secret.example.com/og.jpg", NOW);
    titled.changeOgOverride("Members only", null, "https://cdn.example.com/own.png");
    when(linkRepository.findPublicProfileLinks(7L))
        .thenReturn(List.of(new Row(scraped, null, true), new Row(titled, null, true)));
    when(profileBlockRepository.findAllByUserIdOrderByProfileOrderAsc(7L)).thenReturn(List.of());
    when(clickRepository.countsByLinkIds(any())).thenReturn(List.of());
    when(urlBuilder.build(any())).thenAnswer(i -> "https://kurl.me/" + i.getArgument(0));

    List<PublicProfile.ProfileEntry> entries = loader.load("alice").profile().entries();

    assertThat(entries)
        .allSatisfy(
            entry -> {
              assertThat(entry.passwordProtected()).isTrue();
              assertThat(entry.originalUrl()).isNull();
              assertThat(entry.ogImage()).isNull();
            });
    assertThat(entries)
        .extracting(PublicProfile.ProfileEntry::shortUrl, PublicProfile.ProfileEntry::ogTitle)
        .containsExactly(
            tuple("https://kurl.me/pwd0001", null),
            tuple("https://kurl.me/pwd0002", "Members only"));
  }

  @Test
  void linksThatOpenOrExpireCarryTheirWindowForServeTime() {
    alice();
    LinkEntity expiring = new LinkEntity("https://a", "exp0001", 7L, NOW.plusSeconds(30));
    TestEntities.withId(expiring, 1L);
    expiring.setProfileOrder(1);
    LinkEntity opening = profileLink(2L, "opn0001", 2);
    when(linkRepository.findPublicProfileLinks(7L))
        .thenReturn(
            List.of(new Row(expiring, null, false), new Row(opening, NOW.plusSeconds(60), false)));
    when(profileBlockRepository.findAllByUserIdOrderByProfileOrderAsc(7L)).thenReturn(List.of());
    when(clickRepository.countsByLinkIds(any())).thenReturn(List.of());
    when(urlBuilder.build(any())).thenAnswer(i -> "https://kurl.me/" + i.getArgument(0));

    PublicProfileSnapshot snapshot = loader.load("alice");

    assertThat(snapshot.linkWindows())
        .containsExactly(
            new LinkWindow(new ShortCode("exp0001"), null, NOW.plusSeconds(30)),
            new LinkWindow(new ShortCode("opn0001"), NOW.plusSeconds(60), null));
    assertThat(snapshot.visibleAt(NOW).entries())
        .extracting(e -> e.shortCode().value())
        .containsExactly("exp0001");
    assertThat(snapshot.visibleAt(NOW.plusSeconds(60)).entries())
        .extracting(e -> e.shortCode().value())
        .containsExactly("opn0001");
  }
}
