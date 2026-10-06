package com.example.short_link.common.note;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MentionsTest {

  @Test
  void membersAreNamedByTheirHandleInAnyCase() {
    assertThat(Mentions.of("@yuki 고마워요, @Mina_01 도요. (@yuki)")).containsExactly("yuki", "mina_01");
  }

  @Test
  void emailsAccountsOnOtherServersAndOddLengthsAreNotMembers() {
    assertThat(Mentions.of("me@kurl.me @yo @abcdefghijklmnopq @alice@mastodon.social")).isEmpty();
  }

  @Test
  void aNoteNamesAtMostTenMembers() {
    String many =
        IntStream.range(0, 12).mapToObj(i -> "@user" + i).collect(Collectors.joining(" "));
    assertThat(Mentions.of(many)).hasSize(Mentions.MAX_MENTIONS);
    assertThat(Mentions.of(null)).isEmpty();
  }
}
