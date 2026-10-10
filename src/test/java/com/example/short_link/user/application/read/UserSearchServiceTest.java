package com.example.short_link.user.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.user.domain.repository.UserSearchReader;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserSearchServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");

  @Mock private UserSearchReader reader;

  private UserSearchService service() {
    return new UserSearchService(reader, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static UserSearchReader.Match match(String username) {
    return new UserSearchReader.Match(1L, username, null, null, null, 3, false, false, false);
  }

  @Test
  void fewerThanTwoLettersAfterTheAtSignFindNobodyWithoutAsking() {
    for (String q : new String[] {null, "", " ", "@", " @k ", "가"}) {
      UserSearchView view = service().search(7L, q, 0, 20);
      assertThat(view.items()).isEmpty();
      assertThat(view.hasNext()).isFalse();
    }
    verify(reader, never()).search(any(), anyString(), any(), anyInt(), anyInt());
  }

  @Test
  void asksForOneMoreThanThePageToTellWhetherMoreFollow() {
    List<UserSearchReader.Match> rows =
        IntStream.range(0, 4).mapToObj(i -> match("kim" + i)).toList();
    when(reader.search(7L, "Kim", NOW, 6, 4)).thenReturn(rows);

    UserSearchView view = service().search(7L, " @Kim ", 2, 3);

    assertThat(view.items())
        .extracting(UserSearchView.Item::username)
        .containsExactly("kim0", "kim1", "kim2");
    assertThat(view.hasNext()).isTrue();
    assertThat(view.page()).isEqualTo(2);
    assertThat(view.size()).isEqualTo(3);
  }

  @Test
  void sizeStaysWithinOneToTwentyAndPageNeverGoesBelowZero() {
    service().search(null, "kim", -3, 500);
    verify(reader).search(null, "kim", NOW, 0, UserSearchService.MAX_SIZE + 1);

    UserSearchView small = service().search(null, "lee", 0, 0);
    verify(reader).search(null, "lee", NOW, 0, 2);
    assertThat(small.size()).isEqualTo(1);
  }

  @Test
  void aLongQueryIsCutAtThirtyCharactersWithoutSplittingOne() {
    String emoji = "😀".repeat(40);
    assertThat(UserSearchService.normalize(emoji))
        .isEqualTo("😀".repeat(UserSearchService.MAX_QUERY));
    assertThat(UserSearchService.normalize("@ " + "a".repeat(40)))
        .isEqualTo("a".repeat(UserSearchService.MAX_QUERY));
  }

  @Test
  void aHiddenCountComesBackAsNothingAndALongBioIsShortened() {
    String bio = "가".repeat(UserSearchService.BIO_LENGTH + 5);
    when(reader.search(null, "kim", NOW, 0, 21))
        .thenReturn(
            List.of(
                new UserSearchReader.Match(1L, "kim", "Kim", "a.png", bio, 9, true, true, true),
                new UserSearchReader.Match(2L, "kimb", null, null, "  ", 0, false, false, false)));

    List<UserSearchView.Item> items = service().search(null, "kim", 0, 20).items();

    assertThat(items.get(0))
        .isEqualTo(
            new UserSearchView.Item(
                1L,
                "kim",
                "Kim",
                "a.png",
                "가".repeat(UserSearchService.BIO_LENGTH) + "…",
                null,
                true,
                true));
    assertThat(items.get(1).userId()).isEqualTo(2L);
    assertThat(items.get(1).bio()).isNull();
    assertThat(items.get(1).followerCount()).isZero();
  }
}
