package com.example.short_link.link.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.link.application.dto.MyLinksCursor;
import com.example.short_link.link.application.dto.MyLinksQuery;
import com.example.short_link.link.application.dto.MyLinksQuery.SortDir;
import com.example.short_link.link.application.dto.MyLinksQuery.SortKey;
import com.example.short_link.link.domain.LinkExpiryFilter;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MyLinksQueryTest {

  @Test
  void defaultsToDefaultSizeAndNoCursor() {
    MyLinksQuery q = MyLinksQuery.of(null, null, null, null, null, null, null, null);
    assertThat(q.size()).isEqualTo(MyLinksQuery.DEFAULT_SIZE);
    assertThat(q.after()).isNull();
    assertThat(q.q()).isNull();
    assertThat(q.sort()).isEqualTo(SortKey.CREATED_AT);
    assertThat(q.dir()).isEqualTo(SortDir.DESC);
  }

  @Test
  void capsSizeToMax() {
    MyLinksQuery q = MyLinksQuery.of(5000, null, null, null, null, null, null, null);
    assertThat(q.size()).isEqualTo(MyLinksQuery.MAX_SIZE);
  }

  @Test
  void normalizesNonPositiveSize() {
    MyLinksQuery q = MyLinksQuery.of(-1, null, "  ", null, null, null, null, null);
    assertThat(q.size()).isEqualTo(MyLinksQuery.DEFAULT_SIZE);
    assertThat(q.q()).isNull();
  }

  @Test
  void trimsAndNullsBlankQuery() {
    assertThat(MyLinksQuery.of(20, null, "  hello ", null, null, null, null, null).q())
        .isEqualTo("hello");
    assertThat(MyLinksQuery.of(20, null, "", null, null, null, null, null).q()).isNull();
  }

  @Test
  void parsesExpiryEnumCaseInsensitively() {
    MyLinksQuery q = MyLinksQuery.of(null, null, null, null, null, "expired", null, null);
    assertThat(q.expiry()).isEqualTo(LinkExpiryFilter.EXPIRED);
  }

  @Test
  void rejectsUnknownExpiryFilter() {
    assertThatThrownBy(() -> MyLinksQuery.of(null, null, null, null, null, "tomorrow", null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBadDate() {
    assertThatThrownBy(
            () -> MyLinksQuery.of(null, null, null, null, null, null, "not-a-date", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parsesIso8601Dates() {
    MyLinksQuery q =
        MyLinksQuery.of(null, null, null, null, null, null, "2026-01-01T00:00:00Z", null);
    assertThat(q.createdAfter()).isNotNull();
  }

  @Test
  void parsesSortAndDirection() {
    MyLinksQuery q =
        MyLinksQuery.of(null, null, null, null, null, null, null, null, "clickCount", "asc");
    assertThat(q.sort()).isEqualTo(SortKey.CLICK_COUNT);
    assertThat(q.dir()).isEqualTo(SortDir.ASC);
  }

  @Test
  void rejectsUnknownSort() {
    assertThatThrownBy(
            () -> MyLinksQuery.of(null, null, null, null, null, null, null, null, "url", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void decodesValidCursor() {
    Instant original = Instant.ofEpochSecond(1700000000L, 123_456_000L); // microsecond-aligned
    String encoded = new MyLinksCursor(original, 42L).encode();
    MyLinksQuery q = MyLinksQuery.of(null, encoded, null, null, null, null, null, null);
    assertThat(q.after()).isNotNull();
    assertThat(q.after().id()).isEqualTo(42L);
    assertThat(q.after().createdAt()).isEqualTo(original);
  }

  @Test
  void rejectsMalformedCursor() {
    assertThatThrownBy(
            () -> MyLinksQuery.of(null, "not-base64-!!!", null, null, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
