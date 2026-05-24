package com.example.short_link.profile.domain.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.profile.exception.InvalidUsernameException;
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

  @Test
  void ignoresUnknownFields() {
    // Forward compat — a frontend rolled out ahead of a backend that doesn't yet know about a
    // field shouldn't 400 every write. This guard was missing when the frontend started sending
    // logoFocalX/logoFocalY (PR #133) and the backend Jackson default FAIL_ON_UNKNOWN_PROPERTIES
    // bounced every contact-card PATCH with a 400. Adding this test catches the same class of
    // bug for future fields without remembering to add a forward-compat case each time.
    String out =
        ContactCard.normalize("{\"name\":\"x\",\"futureField\":\"hello\",\"anotherFuture\":42}");
    assertThat(out).contains("\"name\":\"x\"");
  }

  @Test
  void logoFocalPointRoundTripsAndClamps() {
    String out =
        ContactCard.normalize(
            "{\"name\":\"x\",\"logoUrl\":\"https://cdn.test/l.png\","
                + "\"logoFocalX\":40,\"logoFocalY\":30}");
    assertThat(out).contains("\"logoFocalX\":40");
    assertThat(out).contains("\"logoFocalY\":30");

    String clamped =
        ContactCard.normalize("{\"name\":\"x\",\"logoFocalX\":-50,\"logoFocalY\":150}");
    assertThat(clamped).contains("\"logoFocalX\":0");
    assertThat(clamped).contains("\"logoFocalY\":100");
  }

  @Test
  void logoFocalPointDefaultsTo50WhenMissing() {
    // Records that predate focal-point support normalize to 50/50 (visual center) — same crop
    // behavior the public card had before the field existed.
    String out = ContactCard.normalize("{\"name\":\"x\"}");
    assertThat(out).contains("\"logoFocalX\":50");
    assertThat(out).contains("\"logoFocalY\":50");
  }
}
