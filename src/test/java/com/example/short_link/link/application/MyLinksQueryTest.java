package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MyLinksQueryTest {

  @Test
  void defaultsToFirstPageAndDefaultSize() {
    MyLinksQuery q = MyLinksQuery.of(null, null, null, null);
    assertThat(q.page()).isEqualTo(1);
    assertThat(q.size()).isEqualTo(MyLinksQuery.DEFAULT_SIZE);
    assertThat(q.q()).isNull();
    assertThat(q.zeroBasedPage()).isZero();
  }

  @Test
  void capsSizeToMax() {
    MyLinksQuery q = MyLinksQuery.of(null, 5000, null, null);
    assertThat(q.size()).isEqualTo(MyLinksQuery.MAX_SIZE);
  }

  @Test
  void normalizesNonPositivePageOrSize() {
    MyLinksQuery q = MyLinksQuery.of(0, -1, "  ", null);
    assertThat(q.page()).isEqualTo(1);
    assertThat(q.size()).isEqualTo(MyLinksQuery.DEFAULT_SIZE);
    assertThat(q.q()).isNull();
  }

  @Test
  void trimsAndNullsBlankQuery() {
    assertThat(MyLinksQuery.of(1, 20, "  hello ", null).q()).isEqualTo("hello");
    assertThat(MyLinksQuery.of(1, 20, "", null).q()).isNull();
  }
}
