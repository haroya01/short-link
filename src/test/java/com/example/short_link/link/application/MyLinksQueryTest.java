package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.link.application.LinkFilters.ExpiryFilter;
import org.junit.jupiter.api.Test;

class MyLinksQueryTest {

  @Test
  void defaultsToFirstPageAndDefaultSize() {
    MyLinksQuery q = MyLinksQuery.of(null, null, null, null, null, null, null, null);
    assertThat(q.page()).isEqualTo(1);
    assertThat(q.size()).isEqualTo(MyLinksQuery.DEFAULT_SIZE);
    assertThat(q.q()).isNull();
    assertThat(q.zeroBasedPage()).isZero();
  }

  @Test
  void capsSizeToMax() {
    MyLinksQuery q = MyLinksQuery.of(null, 5000, null, null, null, null, null, null);
    assertThat(q.size()).isEqualTo(MyLinksQuery.MAX_SIZE);
  }

  @Test
  void normalizesNonPositivePageOrSize() {
    MyLinksQuery q = MyLinksQuery.of(0, -1, "  ", null, null, null, null, null);
    assertThat(q.page()).isEqualTo(1);
    assertThat(q.size()).isEqualTo(MyLinksQuery.DEFAULT_SIZE);
    assertThat(q.q()).isNull();
  }

  @Test
  void trimsAndNullsBlankQuery() {
    assertThat(MyLinksQuery.of(1, 20, "  hello ", null, null, null, null, null).q())
        .isEqualTo("hello");
    assertThat(MyLinksQuery.of(1, 20, "", null, null, null, null, null).q()).isNull();
  }

  @Test
  void parsesExpiryEnumCaseInsensitively() {
    MyLinksQuery q = MyLinksQuery.of(null, null, null, null, null, "expired", null, null);
    assertThat(q.expiry()).isEqualTo(ExpiryFilter.EXPIRED);
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
}
