package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ShortCodeGeneratorTest {

  private static final Pattern BASE62 = Pattern.compile("^[0-9A-Za-z]{7}$");

  private final ShortCodeGenerator generator = new ShortCodeGenerator();

  @Test
  void generatesSevenCharBase62() {
    String code = generator.generate();
    assertThat(code).matches(BASE62);
  }

  @Test
  void manySamplesAllMatchShape() {
    // Catches "off-by-one length" / "alphabet leak" regressions across a representative sample.
    for (int i = 0; i < 1000; i++) {
      assertThat(generator.generate()).matches(BASE62);
    }
  }

  @Test
  void collisionsAreRareInSmallBatches() {
    // 62^7 ≈ 3.5e12 — 5000 samples should give zero collisions in practice. If this ever flakes,
    // someone shrank the alphabet or LENGTH and the collision odds blew up.
    Set<String> seen = new HashSet<>();
    int n = 5000;
    for (int i = 0; i < n; i++) seen.add(generator.generate());
    assertThat(seen).hasSize(n);
  }
}
