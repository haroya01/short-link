package com.example.short_link.post.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiscoveryQualityTest {

  @Test
  void countsLettersAndDigitsInAnyScript() {
    assertThat(DiscoveryQuality.meaningfulLength("측정 결과 3번")).isEqualTo(6);
    assertThat(DiscoveryQuality.meaningfulLength("RTT 6,980ms")).isEqualTo(9);
    assertThat(DiscoveryQuality.meaningfulLength("ドメイン駆動設計")).isEqualTo(8);
  }

  @Test
  void jamoWhitespaceSymbolsAndMarkdownDoNotCount() {
    assertThat(DiscoveryQuality.meaningfulLength("ㅎㅎㅎ ㅋㅋ ㅠㅠ")).isZero();
    assertThat(DiscoveryQuality.meaningfulLength("## - **!!** 🎉 ---")).isZero();
    assertThat(DiscoveryQuality.meaningfulLength(null)).isZero();
  }

  @Test
  void aLabelNeedsTwoRealCharacters() {
    assertThat(DiscoveryQuality.isMeaningfulLabel("ㅇㅇㅇ")).isFalse();
    assertThat(DiscoveryQuality.isMeaningfulLabel("ㅁㅇㄹㄹㅇㄴㅁㄹㅁ")).isFalse();
    assertThat(DiscoveryQuality.isMeaningfulLabel("책")).isFalse();
    assertThat(DiscoveryQuality.isMeaningfulLabel("읽을 글")).isTrue();
  }

  @Test
  void aPostIsDiscoverableOnlyAboveTheBodyFloor() {
    PostEntity junk = new PostEntity(1L, "junk", "거거거구ㅜㅅ", "ko");
    junk.measureBody("ㅎㅎㅎ");
    assertThat(junk.isDiscoverable()).isFalse();

    PostEntity real = new PostEntity(1L, "real", "측정 기록", "ko");
    real.measureBody("가".repeat(DiscoveryQuality.MIN_BODY_TEXT_LENGTH));
    assertThat(real.isDiscoverable()).isTrue();
  }
}
