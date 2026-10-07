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

  @Test
  void accountsElsewhereAreReadAsUserAtServer() {
    assertThat(
            Mentions.remote(
                "hi @Bob@Mastodon.Social and @yuki, also @ann.lee@social.example. bye @bob@mastodon.social"))
        .containsExactly("bob@mastodon.social", "ann.lee@social.example");
    assertThat(Mentions.remote("mail me at me@example.com")).isEmpty();
    assertThat(Mentions.remote("@x.@server.example")).isEmpty();
    assertThat(Mentions.remote("no handles")).isEmpty();
    assertThat(Mentions.remote(null)).isEmpty();
    assertThat(Mentions.of("@bob@mastodon.social")).isEmpty();
  }
}
