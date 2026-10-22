package com.example.short_link.federation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SeenItemTest {

  @Test
  void aKurlNoteOrPostKeyNamesItsKindAndId() {
    assertThat(SeenItem.sent("kurl:note:1")).contains(new SeenItem(SeenItem.NOTE, 1));
    assertThat(SeenItem.sent("kurl:post:9223372036854775807"))
        .contains(new SeenItem(SeenItem.POST, Long.MAX_VALUE));
  }

  @Test
  void anythingElseIsNotAKurlKey() {
    for (String key :
        new String[] {
          null,
          "",
          "kurl:note:0",
          "kurl:note:-1",
          "kurl:note:01",
          "kurl:note:9223372036854775808",
          "kurl:note:12345678901234567890",
          "KURL:note:1",
          "kurl:Note:1",
          "kurl:link:1",
          "kurl:note:1 ",
          "kurl:note:",
          "https://a.example/users/a/statuses/1"
        }) {
      assertThat(SeenItem.sent(key)).as(String.valueOf(key)).isEqualTo(Optional.empty());
    }
  }
}
