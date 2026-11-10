package com.example.short_link.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AddressChangeTest {

  @Test
  void aMoveKeepsOnlyTheHostsOnEitherSide() {
    assertThat(
            AddressChange.between(
                "https://Old.Example/welcome?invite=a1b2c3",
                "https://user:secret@new.example:8443/pay?token=z9y8"))
        .containsExactly(
            Map.entry("urlChanged", true),
            Map.entry("fromHost", "old.example"),
            Map.entry("toHost", "new.example"));
  }

  @Test
  void anAddedAddressHasNoHostBeforeItAndARemovedOneHasNoneAfter() {
    assertThat(AddressChange.between(null, "https://branch.example/a"))
        .containsExactly(Map.entry("urlChanged", true), Map.entry("toHost", "branch.example"));
    assertThat(AddressChange.between("https://branch.example/a", null))
        .containsExactly(Map.entry("urlChanged", true), Map.entry("fromHost", "branch.example"));
  }

  @Test
  void theSameAddressIsNotAChangeAndNamesNoHost() {
    assertThat(AddressChange.between("https://same.example/a", "https://same.example/a"))
        .containsExactly(Map.entry("urlChanged", false));
  }

  @Test
  void anAddressWithoutAReadableHostIsLeftOutRatherThanStored() {
    assertThat(AddressChange.between("not a url?token=abc", "https://after.example/x"))
        .containsExactly(Map.entry("urlChanged", true), Map.entry("toHost", "after.example"));
  }

  @Test
  void callersCanAddTheirOwnKeys() {
    Map<String, Object> metadata = AddressChange.between("https://a.example", "https://b.example");
    metadata.put("destinationId", 5L);

    assertThat(metadata).containsEntry("destinationId", 5L).hasSize(4);
  }
}
