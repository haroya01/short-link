package com.example.short_link.link.redirect.application.helper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.ShortCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class LinkHtmlRendererTest {

  private static final ShortCode CODE = ShortCode.of("abc123");

  @Test
  void passwordPrompt_withSiteKey_rendersTurnstileWidgetAndScript() {
    String html = LinkHtmlRenderer.passwordPrompt(CODE, false, "0xSITEKEY");

    assertThat(html).contains("challenges.cloudflare.com/turnstile/v0/api.js");
    assertThat(html).contains("class=\"cf-turnstile\" data-sitekey=\"0xSITEKEY\"");
    // 실패 표시는 없다.
    assertThat(html).doesNotContain("비밀번호가 올바르지 않아요");
  }

  @Test
  void passwordPrompt_withoutSiteKey_omitsWidget() {
    String html = LinkHtmlRenderer.passwordPrompt(CODE, false, null);

    assertThat(html).doesNotContain("cf-turnstile");
    assertThat(html).doesNotContain("turnstile/v0/api.js");
  }

  @Test
  void passwordPrompt_blankSiteKey_treatedAsAbsent() {
    String html = LinkHtmlRenderer.passwordPrompt(CODE, false, "   ");

    assertThat(html).doesNotContain("cf-turnstile");
  }

  @Test
  void passwordPrompt_failed_showsError() {
    String html = LinkHtmlRenderer.passwordPrompt(CODE, true, null);

    assertThat(html).contains("비밀번호가 올바르지 않아요");
    assertThat(html).contains("action=\"/abc123\"");
  }

  @Test
  void passwordPrompt_escapesSiteKey() {
    String html = LinkHtmlRenderer.passwordPrompt(CODE, false, "a\"<b");

    assertThat(html).contains("a&quot;&lt;b");
    assertThat(html).doesNotContain("data-sitekey=\"a\"<b\"");
  }

  @Test
  void passwordPromptResponse_buildsHtmlResponseWithStatus() {
    ResponseEntity<byte[]> response =
        LinkHtmlRenderer.passwordPromptResponse(HttpStatus.UNAUTHORIZED, CODE, true, "0xKEY");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getHeaders().getFirst("X-Robots-Tag")).isEqualTo("noindex, nofollow");
    String body = new String(response.getBody());
    assertThat(body).contains("cf-turnstile");
    assertThat(body).contains("비밀번호가 올바르지 않아요");
  }

  @Test
  void blockedAndExpiredPages_render() {
    assertThat(LinkHtmlRenderer.blockedPageResponse().getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    ResponseEntity<byte[]> expired = LinkHtmlRenderer.expiredPageResponse("만료된 캠페인 <link>");
    assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.GONE);
    // 메시지는 escape 되어 들어간다.
    assertThat(new String(expired.getBody())).contains("&lt;link&gt;");
  }
}
