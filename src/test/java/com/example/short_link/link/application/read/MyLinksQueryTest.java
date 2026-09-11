package com.example.short_link.link.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.dto.MyLinksQuery;
import com.example.short_link.link.application.dto.MyLinksQuery.SortDir;
import com.example.short_link.link.application.dto.MyLinksQuery.SortKey;
import org.junit.jupiter.api.Test;

class MyLinksQueryTest {

  @Test
  void defaultsToDefaultSizeAndNoCursor() {
    MyLinksQuery query = MyLinksQuery.builder().build();
    assertThat(query.size()).isEqualTo(MyLinksQuery.DEFAULT_SIZE);
    assertThat(query.after()).isNull();
    assertThat(query.q()).isNull();
    assertThat(query.sort()).isEqualTo(SortKey.CREATED_AT);
    assertThat(query.dir()).isEqualTo(SortDir.DESC);
  }

  @Test
  void capsSizeToMax() {
    MyLinksQuery query = MyLinksQuery.builder().size(5000).build();
    assertThat(query.size()).isEqualTo(MyLinksQuery.MAX_SIZE);
  }

  @Test
  void normalizesNonPositiveSize() {
    MyLinksQuery query = MyLinksQuery.builder().size(-1).q("  ").build();
    assertThat(query.size()).isEqualTo(MyLinksQuery.DEFAULT_SIZE);
    assertThat(query.q()).isNull();
  }

  @Test
  void trimsTextFiltersAndNullsBlankText() {
    MyLinksQuery query =
        MyLinksQuery.builder().q("  hello ").tag("  work ").domain(" example.com ").build();
    assertThat(query.q()).isEqualTo("hello");
    assertThat(query.tag()).isEqualTo("work");
    assertThat(query.domain()).isEqualTo("example.com");

    MyLinksQuery blank = MyLinksQuery.builder().q("").tag("  ").domain("\t").build();
    assertThat(blank.q()).isNull();
    assertThat(blank.tag()).isNull();
    assertThat(blank.domain()).isNull();
  }
}
