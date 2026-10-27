package com.example.short_link.federation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.federation.domain.repository.ForYouSignalReader.Kind;
import org.junit.jupiter.api.Test;

class ForYouSignalKindsTest {

  @Test
  void theSignalQueryNumbersEachKindByItsDeclarationOrder() {
    assertThat(Kind.values())
        .containsExactly(Kind.LIKED, Kind.BOOKMARKED, Kind.ANSWERED, Kind.FOLLOWED, Kind.DWELT);
  }
}
