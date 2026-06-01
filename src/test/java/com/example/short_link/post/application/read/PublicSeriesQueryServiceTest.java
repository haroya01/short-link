package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.SeriesActivity;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.profile.exception.ProfileException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PublicSeriesQueryServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private SeriesRepository seriesRepository;
  @Mock private PostRepository postRepository;

  private PublicSeriesQueryService service;

  @BeforeEach
  void setUp() {
    service = new PublicSeriesQueryService(userRepository, seriesRepository, postRepository);
  }

  private UserEntity author(String username) {
    UserEntity user = new UserEntity("u@x.com", "google", "g-1");
    user.claimUsername(username);
    return user;
  }

  private UserEntity author(long id, String username) {
    UserEntity user = new UserEntity(username + "@x.com", "google", "g-" + id);
    user.claimUsername(username);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  private SeriesEntity series(long id, long userId, String slug, String title) {
    SeriesEntity s = new SeriesEntity(userId, slug, title);
    ReflectionTestUtils.setField(s, "id", id);
    return s;
  }

  @Test
  void discoverSeriesRanksHydratesAndDropsDeletedAuthors() {
    UserEntity alice = author(1L, "alice");
    UserEntity bob = author(2L, "bob");
    bob.softDelete(); // bob's series must be dropped
    Instant recent = Instant.parse("2026-05-30T09:00:00Z");
    Instant mid = Instant.parse("2026-05-20T09:00:00Z");
    Instant old = Instant.parse("2026-05-10T09:00:00Z");

    // Ranked by recency: alice s10 (newest), bob s20 (dropped), alice s30 (oldest).
    when(postRepository.findActiveSeries(2, 12))
        .thenReturn(
            List.of(
                new SeriesActivity(10L, 4, recent),
                new SeriesActivity(20L, 3, mid),
                new SeriesActivity(30L, 2, old)));
    when(seriesRepository.findAllByIdIn(any()))
        .thenReturn(
            List.of(
                series(10L, 1L, "deep-dive", "Deep Dive"),
                series(20L, 2L, "ghost", "Ghost"),
                series(30L, 1L, "side-log", "Side Log")));
    when(userRepository.findAllByIdIn(any())).thenReturn(List.of(alice, bob));
    // Member previews for the surviving series (deep-dive); fetched only for survivors.
    when(postRepository.findAllBySeriesIdAndStatusOrderBySeriesOrderAsc(10L, PostStatus.PUBLISHED))
        .thenReturn(
            List.of(
                new PostEntity(1L, "dd-1", "DD One", "ko"),
                new PostEntity(1L, "dd-2", "DD Two", "ko")));

    List<PublicSeriesCard> cards = service.discoverSeries(6);

    // bob's series dropped; activity order preserved among survivors.
    assertThat(cards).extracting(PublicSeriesCard::slug).containsExactly("deep-dive", "side-log");
    PublicSeriesCard first = cards.get(0);
    assertThat(first.title()).isEqualTo("Deep Dive");
    assertThat(first.postCount()).isEqualTo(4);
    assertThat(first.lastPublishedAt()).isEqualTo(recent);
    assertThat(first.author().username()).isEqualTo("alice");
    assertThat(first.posts()).extracting(SeriesPostRef::slug).containsExactly("dd-1", "dd-2");
    assertThat(first.posts().get(0).title()).isEqualTo("DD One");
  }

  @Test
  void discoverSeriesEmptyWhenNoneActive() {
    when(postRepository.findActiveSeries(2, 12)).thenReturn(List.of());
    assertThat(service.discoverSeries(6)).isEmpty();
  }

  @Test
  void listHidesEmptySeries() {
    UserEntity author = author("john");
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    SeriesEntity withPosts = new SeriesEntity(author.getId(), "filled", "Filled");
    ReflectionTestUtils.setField(withPosts, "id", 1L);
    SeriesEntity empty = new SeriesEntity(author.getId(), "empty", "Empty");
    ReflectionTestUtils.setField(empty, "id", 2L);
    when(seriesRepository.findAllByUserIdOrderByCreatedAtDesc(author.getId()))
        .thenReturn(List.of(withPosts, empty));
    when(postRepository.findAllBySeriesIdAndStatusOrderBySeriesOrderAsc(
            withPosts.getId(), PostStatus.PUBLISHED))
        .thenReturn(List.of(new PostEntity(author.getId(), "p", "P", "ko")));
    when(postRepository.findAllBySeriesIdAndStatusOrderBySeriesOrderAsc(
            empty.getId(), PostStatus.PUBLISHED))
        .thenReturn(List.of());

    PublicSeriesListView view = service.listPublicSeries("john");

    assertThat(view.series()).hasSize(1);
    assertThat(view.series().get(0).slug()).isEqualTo("filled");
    assertThat(view.series().get(0).postCount()).isEqualTo(1);
  }

  @Test
  void detailReturnsPublishedMembers() {
    UserEntity author = author(1L, "john");
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    SeriesEntity series = series(42L, author.getId(), "my-series", "My Series");
    when(seriesRepository.findByUserIdAndSlug(author.getId(), "my-series"))
        .thenReturn(Optional.of(series));
    when(postRepository.findAllBySeriesIdAndStatusOrderBySeriesOrderAsc(
            series.getId(), PostStatus.PUBLISHED))
        .thenReturn(List.of(new PostEntity(author.getId(), "a", "A", "ko")));

    PublicSeriesDetail detail = service.findPublicSeries("john", "my-series");

    assertThat(detail.series().id()).isEqualTo(42L);
    assertThat(detail.series().title()).isEqualTo("My Series");
    assertThat(detail.posts()).hasSize(1);
    assertThat(detail.posts().get(0).slug()).isEqualTo("a");
  }

  @Test
  void unknownAuthorThrows() {
    when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.listPublicSeries("ghost"))
        .isInstanceOf(ProfileException.class);
  }
}
