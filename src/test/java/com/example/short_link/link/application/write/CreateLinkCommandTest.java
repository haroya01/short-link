package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CreateLinkCommandTest {

  @Test
  void aShorteningReusesTheOwnersLinkToTheSameUrl() {
    CreateLinkCommand command = CreateLinkCommand.of("https://example.com", 7L, null, null);

    assertThat(command.deduplicate()).isTrue();
    assertThat(command.allowSelfHost()).isFalse();
  }

  @Test
  void aLinkToOurOwnPageIsAlwaysNewAndMayPointAtThisHost() {
    CreateLinkCommand command = CreateLinkCommand.forOwnPage("https://kurl.me/e/party", 7L);

    assertThat(command.deduplicate()).isFalse();
    assertThat(command.allowSelfHost()).isTrue();
    assertThat(command.userId()).isEqualTo(7L);
  }

  @Test
  void eachBatchGetsItsOwnLinkToAnOutsideUrl() {
    CreateLinkCommand command = CreateLinkCommand.perBatch("https://example.com", 7L);

    assertThat(command.deduplicate()).isFalse();
    assertThat(command.allowSelfHost()).isFalse();
    assertThat(command.userId()).isEqualTo(7L);
  }
}
