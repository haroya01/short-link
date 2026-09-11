package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.application.read.PublicFeedQuery.Browse;
import com.example.short_link.post.application.read.PublicFeedQuery.BrowseOrder;
import com.example.short_link.post.application.read.PublicFeedQuery.Search;
import com.example.short_link.post.application.read.PublicFeedQuery.SearchOrder;
import com.example.short_link.post.application.read.PublicFeedQuery.Tagged;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class PublicFeedQueryTest {

  @Test
  void searchTakesPriorityOverTagAndTrimsTextWhileKeepingTheLanguageFilter() {
    var query = PublicFeedQuery.from(" spring ", "java", "recent", " ko ", 0, 20);

    assertThat(query.selection()).isEqualTo(new Search("spring", SearchOrder.RECENT, " ko "));
  }

  @Test
  void tagAfterBlankSearchHasNeitherLanguageFilteringNorAnAlternateSort() {
    var query = PublicFeedQuery.from("  ", " java ", "trending", "ja", 0, 20);

    assertThat(query.selection()).isEqualTo(new Tagged("java"));
  }

  @Test
  void blankSearchAndTagBrowseAndLeaveLanguageNormalizationToTheRepository() {
    var query = PublicFeedQuery.from("  ", "  ", null, "  ", 0, 20);

    assertThat(query.selection()).isEqualTo(new Browse(BrowseOrder.RECENT, "  "));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\u3000", "\u0000", " ko "})
  void languageIsNotNormalizedTwiceBeforeTheRepository(String language) {
    var browse = PublicFeedQuery.from(null, null, null, language, 0, 20);
    var search = PublicFeedQuery.from("spring", null, null, language, 0, 20);

    assertThat(browse.selection()).isEqualTo(new Browse(BrowseOrder.RECENT, language));
    assertThat(search.selection()).isEqualTo(new Search("spring", SearchOrder.RELEVANCE, language));
  }

  @ParameterizedTest
  @MethodSource("browseOrders")
  void browsingRetainsTheExistingSortFallback(String input, BrowseOrder expected) {
    var query = PublicFeedQuery.from(null, null, input, "en", 0, 20);

    assertThat(query.selection()).isEqualTo(new Browse(expected, "en"));
  }

  static Stream<Arguments> browseOrders() {
    return Stream.of(
        Arguments.of(null, BrowseOrder.RECENT),
        Arguments.of("", BrowseOrder.RECENT),
        Arguments.of("recent", BrowseOrder.RECENT),
        Arguments.of("relevance", BrowseOrder.RECENT),
        Arguments.of("unknown", BrowseOrder.RECENT),
        Arguments.of("trending", BrowseOrder.TRENDING),
        Arguments.of("TrEnDiNg", BrowseOrder.TRENDING),
        Arguments.of(" trending ", BrowseOrder.RECENT));
  }

  @ParameterizedTest
  @MethodSource("searchOrders")
  void searchingRetainsTheExistingSortFallback(String input, SearchOrder expected) {
    var query = PublicFeedQuery.from("spring", null, input, null, 0, 20);

    assertThat(query.selection()).isEqualTo(new Search("spring", expected, null));
  }

  static Stream<Arguments> searchOrders() {
    return Stream.of(
        Arguments.of(null, SearchOrder.RELEVANCE),
        Arguments.of("", SearchOrder.RELEVANCE),
        Arguments.of("relevance", SearchOrder.RELEVANCE),
        Arguments.of("unknown", SearchOrder.RELEVANCE),
        Arguments.of("recent", SearchOrder.RECENT),
        Arguments.of("ReCeNt", SearchOrder.RECENT),
        Arguments.of("trending", SearchOrder.TRENDING),
        Arguments.of("TrEnDiNg", SearchOrder.TRENDING),
        Arguments.of(" recent ", SearchOrder.RELEVANCE),
        Arguments.of(" trending ", SearchOrder.RELEVANCE));
  }

  @ParameterizedTest
  @CsvSource({"-3, 999, 0, 50", "0, 0, 0, 1", "2, -1, 2, 1", "2, 8, 2, 8", "1, 50, 1, 50"})
  void pageBoundsApplyToEverySelection(int page, int size, int expectedPage, int expectedSize) {
    for (PublicFeedQuery query :
        new PublicFeedQuery[] {
          PublicFeedQuery.from(null, null, null, null, page, size),
          PublicFeedQuery.from("spring", null, null, null, page, size),
          PublicFeedQuery.from(null, "java", null, null, page, size)
        }) {
      assertThat(query.page()).isEqualTo(expectedPage);
      assertThat(query.size()).isEqualTo(expectedSize);
    }
  }
}
