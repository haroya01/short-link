package com.example.short_link.profile.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.profile.exception.InvalidUsernameException;
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

  @Test
  void subtitleRoundTrips() {
    String out =
        EmailFormConfig.normalize("{\"title\":\"신상품 알림\",\"subtitle\":\"신상품 출시 시 가장 먼저 알려드려요.\"}");
    assertThat(out).contains("\"subtitle\":\"신상품 출시 시 가장 먼저 알려드려요.\"");
  }

  @Test
  void subtitleDefaultsToNullWhenMissing() {
    // Legacy records without subtitle re-normalize with subtitle=null — frontend renderer skips
    // the slot entirely so visual is identical to pre-migration.
    String out = EmailFormConfig.normalize("{\"title\":\"Subscribe\"}");
    assertThat(out).contains("\"subtitle\":null");
  }

  @Test
  void subtitleTrimmedAndBlankBecomesNull() {
    String out = EmailFormConfig.normalize("{\"title\":\"ok\",\"subtitle\":\"   \"}");
    assertThat(out).contains("\"subtitle\":null");
  }

  @Test
  void capsSubtitleTo200() {
    String longStr = "x".repeat(500);
    String out = EmailFormConfig.normalize("{\"title\":\"ok\",\"subtitle\":\"" + longStr + "\"}");
    assertThat(out).contains("\"subtitle\":\"" + "x".repeat(200) + "\"");
  }

  @Test
  void ignoresUnknownFields() {
    // Forward compat — same hotfix pattern as ContactCard logoFocalX/Y (PR #256). A frontend
    // shipping a new field (consentCheckbox, marketingOptInLabel) before the backend deploy
    // shouldn't bounce every EMAIL_FORM PATCH with a 400 from Jackson's default
    // FAIL_ON_UNKNOWN_PROPERTIES.
    String out =
        EmailFormConfig.normalize(
            "{\"title\":\"Subscribe\",\"consentCheckbox\":true,\"marketingOptInLabel\":\"yes\"}");
    assertThat(out).contains("\"title\":\"Subscribe\"");
  }
}
