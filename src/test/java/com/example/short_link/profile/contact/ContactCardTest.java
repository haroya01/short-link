package com.example.short_link.profile.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.profile.application.InvalidUsernameException;
import org.junit.jupiter.api.Test;

class ContactCardTest {

  @Test
  void normalizesValidCard() {
    String out =
        ContactCard.normalize(
            "{\"name\":\"  김동현  \",\"title\":\"Engineer\",\"company\":\"kurl\","
                + "\"email\":\"a@b.com\",\"phone\":\"+82-10-0000-0000\","
                + "\"address\":\"Seoul\",\"website\":\"https://kurl.me\"}");
    assertThat(out).contains("\"name\":\"김동현\"");
    assertThat(out).contains("\"email\":\"a@b.com\"");
    assertThat(out).contains("\"website\":\"https://kurl.me\"");
  }

  @Test
  void nameRequired() {
    assertThatThrownBy(() -> ContactCard.normalize("{}"))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(() -> ContactCard.normalize("{\"name\":\"   \"}"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void emailValidationRunsWhenPresent() {
    assertThatThrownBy(() -> ContactCard.normalize("{\"name\":\"x\",\"email\":\"not-an-email\"}"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void websiteMustBeHttp() {
    assertThatThrownBy(
            () -> ContactCard.normalize("{\"name\":\"x\",\"website\":\"javascript:alert(1)\"}"))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(
            () -> ContactCard.normalize("{\"name\":\"x\",\"website\":\"ftp://example.com\"}"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void blankOptionalsBecomeNull() {
    String out = ContactCard.normalize("{\"name\":\"x\",\"title\":\"   \",\"phone\":\"\"}");
    assertThat(out).contains("\"title\":null");
    assertThat(out).contains("\"phone\":null");
  }

  @Test
  void rejectsMalformedJson() {
    assertThatThrownBy(() -> ContactCard.normalize("not json"))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(() -> ContactCard.normalize(""))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void acceptsValidLogoUrl() {
    String out =
        ContactCard.normalize(
            "{\"name\":\"x\",\"logoUrl\":\"https://cdn.test/profile-images/1/logo.png\"}");
    assertThat(out).contains("\"logoUrl\":\"https://cdn.test/profile-images/1/logo.png\"");
  }

  @Test
  void logoUrlMustBeHttp() {
    assertThatThrownBy(
            () -> ContactCard.normalize("{\"name\":\"x\",\"logoUrl\":\"javascript:alert(1)\"}"))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(
            () -> ContactCard.normalize("{\"name\":\"x\",\"logoUrl\":\"ftp://example.com/logo\"}"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void backwardCompatibleWhenLogoUrlMissing() {
    // Existing CONTACT_CARD JSON in DB doesn't have logoUrl. Re-normalize should succeed and
    // round-trip with logoUrl as null — no migration required.
    String out = ContactCard.normalize("{\"name\":\"x\",\"email\":\"a@b.com\"}");
    assertThat(out).contains("\"logoUrl\":null");
  }

  @Test
  void acceptsKnownPalettes() {
    for (String p :
        new String[] {
          "amethyst",
          "rose-gold",
          "emerald",
          "sapphire",
          "sunset",
          "midnight",
          "champagne",
          "aurora"
        }) {
      String out = ContactCard.normalize("{\"name\":\"x\",\"palette\":\"" + p + "\"}");
      assertThat(out).contains("\"palette\":\"" + p + "\"");
    }
  }

  @Test
  void rejectsUnknownPalette() {
    assertThatThrownBy(
            () -> ContactCard.normalize("{\"name\":\"x\",\"palette\":\"rainbow-vomit\"}"))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(
            () -> ContactCard.normalize("{\"name\":\"x\",\"palette\":\"javascript:alert(1)\"}"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void paletteNullBackwardCompatible() {
    // Existing CONTACT_CARD JSON without palette → round-trip with palette null. Frontend
    // interprets null as "use default."
    String out = ContactCard.normalize("{\"name\":\"x\"}");
    assertThat(out).contains("\"palette\":null");
  }
}
