package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.profile.exception.ProfileException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
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
    UserEntity author = author("john");
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    SeriesEntity series = new SeriesEntity(author.getId(), "my-series", "My Series");
    when(seriesRepository.findByUserIdAndSlug(author.getId(), "my-series"))
        .thenReturn(Optional.of(series));
    when(postRepository.findAllBySeriesIdAndStatusOrderBySeriesOrderAsc(
            series.getId(), PostStatus.PUBLISHED))
        .thenReturn(List.of(new PostEntity(author.getId(), "a", "A", "ko")));

    PublicSeriesDetail detail = service.findPublicSeries("john", "my-series");

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
