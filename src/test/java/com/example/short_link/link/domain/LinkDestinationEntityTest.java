package com.example.short_link.link.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LinkDestinationEntityTest {

  @Test
  void shortConstructorClampsWeightAndDefaultsEnabled() {
    LinkDestinationEntity d = new LinkDestinationEntity(7L, "https://x", 0, "var-a", "kr");
    assertThat(d.getLinkId()).isEqualTo(7L);
    assertThat(d.getUrl()).isEqualTo("https://x");
    assertThat(d.getWeight()).isEqualTo(1);
    assertThat(d.getLabel()).isEqualTo("var-a");
    assertThat(d.getCountryCode()).isEqualTo("KR");
    assertThat(d.isEnabled()).isTrue();
    assertThat(d.getDeviceClass()).isNull();
    assertThat(d.getOs()).isNull();
  }

  @Test
  void longConstructorNormalizesDeviceAndOs() {
    LinkDestinationEntity d =
        new LinkDestinationEntity(7L, "https://x", 5, "B", "us", " Mobile ", " IOS ");
    assertThat(d.getDeviceClass()).isEqualTo("mobile");
    assertThat(d.getOs()).isEqualTo("ios");
    assertThat(d.getCountryCode()).isEqualTo("US");
    assertThat(d.getWeight()).isEqualTo(5);
  }

  @Test
  void countryCodeBlankIsNull() {
    LinkDestinationEntity d = new LinkDestinationEntity(7L, "https://x", 1, null, "  ");
    assertThat(d.getCountryCode()).isNull();
  }

  @Test
  void countryCodeWrongLengthThrows() {
    assertThatThrownBy(() -> new LinkDestinationEntity(7L, "https://x", 1, null, "KOR"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deviceClassValidatesValue() {
    assertThatThrownBy(
            () -> new LinkDestinationEntity(7L, "https://x", 1, null, "KR", "watch", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void osValidatesValue() {
    assertThatThrownBy(
            () -> new LinkDestinationEntity(7L, "https://x", 1, null, "KR", null, "symbian"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void updateLeavesUnsetFieldsAlone() {
    LinkDestinationEntity d =
        new LinkDestinationEntity(7L, "https://x", 2, "L", "KR", "mobile", "ios");
    d.update(null, null, null, null, null, null, null);
    assertThat(d.getUrl()).isEqualTo("https://x");
    assertThat(d.getWeight()).isEqualTo(2);
    assertThat(d.getLabel()).isEqualTo("L");
    assertThat(d.isEnabled()).isTrue();
    assertThat(d.getCountryCode()).isEqualTo("KR");
    assertThat(d.getDeviceClass()).isEqualTo("mobile");
    assertThat(d.getOs()).isEqualTo("ios");
  }

  @Test
  void shortUpdateOverridesOnlyBaseFields() {
    LinkDestinationEntity d =
        new LinkDestinationEntity(7L, "https://x", 1, null, "KR", "mobile", "ios");
    d.update("https://y", 9, "label2", false, "us");
    assertThat(d.getUrl()).isEqualTo("https://y");
    assertThat(d.getWeight()).isEqualTo(9);
    assertThat(d.getLabel()).isEqualTo("label2");
    assertThat(d.isEnabled()).isFalse();
    assertThat(d.getCountryCode()).isEqualTo("US");
    assertThat(d.getDeviceClass()).isEqualTo("mobile");
    assertThat(d.getOs()).isEqualTo("ios");
  }

  @Test
  void longUpdateNormalizesNewValues() {
    LinkDestinationEntity d = new LinkDestinationEntity(7L, "https://x", 1, "L", "KR");
    d.update("https://y", 0, "L2", true, "JP", "Desktop", "Windows");
    assertThat(d.getWeight()).isEqualTo(1);
    assertThat(d.getCountryCode()).isEqualTo("JP");
    assertThat(d.getDeviceClass()).isEqualTo("desktop");
    assertThat(d.getOs()).isEqualTo("windows");
  }
}
