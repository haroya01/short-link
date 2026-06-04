package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.stats.domain.repository.projection.ClickProjections;
import com.example.short_link.post.application.write.SeriesOwnership;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostReadStatsReader;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostReadStatsServiceTest {

  private static final long USER = 7L;

  @Mock private PostReadStatsReader reader;
  @Mock private PostRepository postRepository;
  @Mock private SeriesOwnership seriesOwnership;
  @Mock private UserRepository userRepository;

  private PostReadStatsService service;

  @BeforeEach
  void setUp() {
    service = new PostReadStatsService(reader, postRepository, seriesOwnership, userRepository);
    UserEntity owner = new UserEntity("o@x.com", "google", "g-1");
    when(userRepository.findById(USER)).thenReturn(Optional.of(owner)); // tz default Asia/Seoul
  }

  private PostEntity ownedPost() {
    return new PostEntity(USER, "p", "P", "ko");
  }

  @Test
  void forPostAggregatesWhenOwned() {
    when(postRepository.findById(1L)).thenReturn(Optional.of(ownedPost()));
    when(reader.countViews(any())).thenReturn(120L);
    when(reader.countHuman(any())).thenReturn(100L);
    when(reader.countBot(any())).thenReturn(20L);
    when(reader.countUnique(any())).thenReturn(80L);

    PostReadStats stats = service.forPost(USER, 1L);

    assertThat(stats.totalVisits()).isEqualTo(120L);
    assertThat(stats.humanVisits()).isEqualTo(100L);
    assertThat(stats.botVisits()).isEqualTo(20L);
    assertThat(stats.uniqueVisits()).isEqualTo(80L);
    assertThat(stats.timezone()).isEqualTo("Asia/Seoul");
    // Unstubbed list queries default to empty (Mockito) → stable empty breakdowns, not null.
    assertThat(stats.countryVisits()).isEmpty();
    assertThat(stats.dailyVisits()).isEmpty();
  }

  private static ClickProjections.HeatmapRow heat(int dow) {
    return new ClickProjections.HeatmapRow() {
      @Override
      public Integer getDow() {
        return dow;
      }

      @Override
      public Integer getHour() {
        return 9;
      }

      @Override
      public Long getCount() {
        return 1L;
      }
    };
  }

  @Test
  void forPostMapsDimensions() {
    when(postRepository.findById(1L)).thenReturn(Optional.of(ownedPost()));
    ClickProjections.HourClickRow h = mock(ClickProjections.HourClickRow.class);
    when(h.getHour()).thenReturn(21);
    when(h.getCount()).thenReturn(50L);
    when(reader.hourly(any(), any())).thenReturn(List.of(h));
    // dow 0 (default→UNKNOWN) + 1..7 covers every dayOfWeekName branch.
    when(reader.heatmap(any(), any()))
        .thenReturn(
            List.of(heat(0), heat(1), heat(2), heat(3), heat(4), heat(5), heat(6), heat(7)));
    ClickProjections.CountryClickRow c = mock(ClickProjections.CountryClickRow.class);
    when(c.getCountry()).thenReturn(null); // null → "unknown"
    when(c.getCount()).thenReturn(7L);
    when(reader.topCountries(any(), anyInt())).thenReturn(List.of(c));

    PostReadStats stats = service.forPost(USER, 1L);

    assertThat(stats.peakHour()).isEqualTo(21);
    assertThat(stats.heatmap())
        .extracting(PostReadStats.HeatmapCell::dayOfWeek)
        .containsExactly(
            "UNKNOWN",
            "SUNDAY",
            "MONDAY",
            "TUESDAY",
            "WEDNESDAY",
            "THURSDAY",
            "FRIDAY",
            "SATURDAY");
    assertThat(stats.countryVisits().get(0).country()).isEqualTo("unknown");
  }

  @Test
  void forPostFallsBackToDefaultTimezoneWhenUserMissing() {
    when(postRepository.findById(1L)).thenReturn(Optional.of(ownedPost()));
    when(userRepository.findById(USER)).thenReturn(Optional.empty()); // tz null → DEFAULT_ZONE

    PostReadStats stats = service.forPost(USER, 1L);

    assertThat(stats.timezone()).isEqualTo("Asia/Seoul");
  }

  @Test
  void forPostDeniesWhenNotOwned() {
    PostEntity other = new PostEntity(999L, "p", "P", "ko");
    when(postRepository.findById(1L)).thenReturn(Optional.of(other));

    assertThatThrownBy(() -> service.forPost(USER, 1L)).isInstanceOf(PostException.class);
    verify(reader, never()).countViews(any());
  }

  @Test
  void forPostThrowsWhenPostMissing() {
    when(postRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.forPost(USER, 1L)).isInstanceOf(PostException.class);
  }

  @Test
  void forSeriesAggregatesAcrossMembers() {
    PostEntity m1 = mock(PostEntity.class);
    PostEntity m2 = mock(PostEntity.class);
    when(m1.getId()).thenReturn(11L);
    when(m2.getId()).thenReturn(12L);
    when(postRepository.findAllBySeriesIdOrderBySeriesOrderAsc(5L)).thenReturn(List.of(m1, m2));
    when(reader.countViews(any())).thenReturn(300L);

    PostReadStats stats = service.forSeries(USER, 5L);

    verify(seriesOwnership).requireOwned(USER, 5L);
    assertThat(stats.totalVisits()).isEqualTo(300L);
  }

  @Test
  void forSeriesEmptyWhenNoMembers() {
    when(postRepository.findAllBySeriesIdOrderBySeriesOrderAsc(5L)).thenReturn(List.of());

    PostReadStats stats = service.forSeries(USER, 5L);

    verify(seriesOwnership).requireOwned(USER, 5L);
    assertThat(stats.totalVisits()).isZero();
    assertThat(stats.countryVisits()).isEmpty();
    // Empty member set must not hit the reader (SQL IN () would be invalid).
    verify(reader, never()).countViews(any());
    verify(reader, never()).daily(any(), any(), any());
  }
}
