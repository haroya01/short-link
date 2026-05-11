package com.example.short_link.profile.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.profile.application.InvalidUsernameException;
import org.junit.jupiter.api.Test;

class EmailFormConfigTest {

  @Test
  void normalizesValidConfig() {
    String out =
        EmailFormConfig.normalize(
            "{\"title\":\"  Subscribe  \",\"placeholder\":\"you@example.com\","
                + "\"successMessage\":\"Thanks!\"}");
    assertThat(out).contains("\"title\":\"Subscribe\"");
    assertThat(out).contains("\"placeholder\":\"you@example.com\"");
    assertThat(out).contains("\"successMessage\":\"Thanks!\"");
  }

  @Test
  void titleRequired() {
    assertThatThrownBy(() -> EmailFormConfig.normalize("{\"title\":\"   \"}"))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(() -> EmailFormConfig.normalize("{}"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void rejectsBlank() {
    assertThatThrownBy(() -> EmailFormConfig.normalize(null))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(() -> EmailFormConfig.normalize(""))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void rejectsMalformedJson() {
    assertThatThrownBy(() -> EmailFormConfig.normalize("not json"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void capsTitlePlaceholderSuccess() {
    String longStr = "x".repeat(500);
    String out =
        EmailFormConfig.normalize(
            "{\"title\":\""
                + longStr
                + "\",\"placeholder\":\""
                + longStr
                + "\",\"successMessage\":\""
                + longStr
                + "\"}");
    // titles cap at 60, placeholder 60, success 120 — verify by counting x's between quotes
    assertThat(out).contains("\"title\":\"" + "x".repeat(60) + "\"");
    assertThat(out).contains("\"placeholder\":\"" + "x".repeat(60) + "\"");
    assertThat(out).contains("\"successMessage\":\"" + "x".repeat(120) + "\"");
  }

  @Test
  void blankOptionalsBecomeNull() {
    String out = EmailFormConfig.normalize("{\"title\":\"ok\",\"placeholder\":\"   \"}");
    assertThat(out).contains("\"placeholder\":null");
  }
}
