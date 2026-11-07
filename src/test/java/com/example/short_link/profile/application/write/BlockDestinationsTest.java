package com.example.short_link.profile.application.write;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.profile.domain.ProfileBlockType;
import com.example.short_link.profile.domain.contact.ProductCardCarousel;
import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import org.junit.jupiter.api.Test;

class BlockDestinationsTest {

  private final BlockDestinations destinations =
      new BlockDestinations(url -> !url.contains("phish.test"));

  private static String card(String... buyUrls) {
    StringBuilder items = new StringBuilder();
    for (String url : buyUrls) {
      if (items.length() > 0) items.append(',');
      items.append("{\"name\":\"Bread\",\"ctaUrl\":\"").append(url).append("\"}");
    }
    return ProductCardCarousel.normalize("{\"items\":[" + items + "]}");
  }

  @Test
  void everyBuyButtonOfAProductCardMustLeadSomewhereAllowed() {
    assertThatCode(
            () ->
                destinations.requireAllowed(
                    ProfileBlockType.PRODUCT_CARD, card("https://example.com/a")))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () ->
                destinations.requireAllowed(
                    ProfileBlockType.PRODUCT_CARD,
                    card("https://example.com/a", "https://phish.test/b")))
        .isInstanceOfSatisfying(
            ProfileException.class,
            e ->
                org.assertj.core.api.Assertions.assertThat(e.errorCode())
                    .isEqualTo(ProfileErrorCode.MALICIOUS_URL));
  }

  @Test
  void aProductCardWithoutButtonsAndOtherBlocksAskNothing() {
    BlockDestinations refusesAll = new BlockDestinations(url -> false);

    assertThatCode(
            () ->
                refusesAll.requireAllowed(
                    ProfileBlockType.PRODUCT_CARD,
                    ProductCardCarousel.normalize("{\"items\":[{\"name\":\"Bread\"}]}")))
        .doesNotThrowAnyException();
    assertThatCode(() -> refusesAll.requireAllowed(ProfileBlockType.TEXT, "hello"))
        .doesNotThrowAnyException();
  }
}
