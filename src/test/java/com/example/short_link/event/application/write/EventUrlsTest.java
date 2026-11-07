package com.example.short_link.event.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.event.exception.EventErrorCode;
import com.example.short_link.event.exception.EventException;
import org.junit.jupiter.api.Test;

class EventUrlsTest {

  @Test
  void anEmptyLinkIsLeftAloneAndEveryOtherOneMustBeAllowed() {
    assertThatCode(() -> EventUrls.requireAllowed(url -> false, null, "", "  "))
        .doesNotThrowAnyException();
    assertThatCode(() -> EventUrls.requireAllowed(url -> true, " https://example.com/room "))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () ->
                EventUrls.requireAllowed(
                    url -> !url.contains("phish.test"),
                    "https://example.com/place",
                    "https://phish.test/room"))
        .isInstanceOfSatisfying(
            EventException.class,
            e -> assertThat(e.errorCode()).isEqualTo(EventErrorCode.MALICIOUS_URL));
  }
}
