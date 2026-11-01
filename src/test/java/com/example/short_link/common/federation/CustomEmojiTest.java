package com.example.short_link.common.federation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CustomEmojiTest {

  private static final List<CustomEmoji> TWO =
      List.of(
          new CustomEmoji("neoghost", "https://f.example/neoghost.png"),
          new CustomEmoji("verified", "https://f.example/verified.png"));

  @Test
  void aListIsKeptAsJsonAndNoneAsNull() {
    assertThat(CustomEmoji.column(TWO))
        .isEqualTo(
            "[{\"shortcode\":\"neoghost\",\"url\":\"https://f.example/neoghost.png\"},"
                + "{\"shortcode\":\"verified\",\"url\":\"https://f.example/verified.png\"}]");
    assertThat(CustomEmoji.column(List.of())).isNull();
    assertThat(CustomEmoji.column(null)).isNull();
  }

  @Test
  void theColumnReadsBackInOrderAsMysqlHandsItBack() {
    assertThat(CustomEmoji.fromColumn(CustomEmoji.column(TWO))).isEqualTo(TWO);
    assertThat(
            CustomEmoji.fromColumn(
                "[{\"url\": \"https://f.example/neoghost.png\", \"shortcode\": \"neoghost\"},"
                    + " {\"url\": \"https://f.example/verified.png\", \"shortcode\": \"verified\"}]"))
        .isEqualTo(TWO);
  }

  @Test
  void nothingOrSomethingUnreadableIsNoEmojis() {
    assertThat(CustomEmoji.fromColumn(null)).isEmpty();
    assertThat(CustomEmoji.fromColumn(" ")).isEmpty();
    assertThat(CustomEmoji.fromColumn("[{")).isEmpty();
    assertThat(CustomEmoji.fromColumn("[{\"shortcode\":\"x\"},{\"url\":\"https://f.example/y\"}]"))
        .isEmpty();
  }
}
