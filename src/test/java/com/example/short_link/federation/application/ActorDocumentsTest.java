package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ActorDocumentsTest {

  @Test
  void summaryEscapesHtmlAndKeepsLineBreaks() {
    assertThat(ActorDocuments.summary(" a\n<b> ")).isEqualTo("<p>a<br>&lt;b&gt;</p>");
    assertThat(ActorDocuments.summary(" ")).isNull();
    assertThat(ActorDocuments.summary(null)).isNull();
  }
}
