package com.example.short_link.link.redirect.application.helper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.exception.LinkErrorCode;
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

  @Test
  void expiredPage_blankMessage_usesDefaultCopy() {
    ResponseEntity<byte[]> expired = LinkHtmlRenderer.expiredPageResponse(null);
    assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.GONE);
    assertThat(new String(expired.getBody())).contains("만료");
  }

  @Test
  void viewLimitPage_isGone_withReason() {
    ResponseEntity<byte[]> r = LinkHtmlRenderer.viewLimitPageResponse();
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.GONE);
    assertThat(new String(r.getBody())).contains("조회 한도");
  }

  @Test
  void notFoundPage_is404() {
    assertThat(LinkHtmlRenderer.notFoundPageResponse().getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void passwordPrompt_failed_addsShakeClass() {
    assertThat(LinkHtmlRenderer.passwordPrompt(CODE, true, null)).contains("card shake");
    assertThat(LinkHtmlRenderer.passwordPrompt(CODE, false, null)).doesNotContain("card shake");
  }

  @Test
  void unlockedPage_animatesAndForwardsToEscapedDestination() {
    ResponseEntity<byte[]> r =
        LinkHtmlRenderer.unlockedPageResponse("https://example.com/a?b=1&c=2");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    String body = new String(r.getBody());
    assertThat(body).contains("http-equiv=\"refresh\"");
    assertThat(body).contains("data-u=\"https://example.com/a?b=1&amp;c=2\"");
    assertThat(body).contains("bigmark"); // kurl 마크 애니메이션
  }

  @Test
  void unlockedPage_escapesScriptInjectionInDestination() {
    String evil = "https://x/\"></script><script>alert(1)</script>";
    String body = new String(LinkHtmlRenderer.unlockedPageResponse(evil).getBody());
    assertThat(body).doesNotContain("<script>alert(1)</script>");
    assertThat(body).contains("&lt;script&gt;");
  }

  @Test
  void visitorErrorPage_mapsVisitorCodes_elseNull() {
    assertThat(
            LinkHtmlRenderer.visitorErrorPage(LinkErrorCode.LINK_VIEW_LIMIT_EXCEEDED)
                .getStatusCode())
        .isEqualTo(HttpStatus.GONE);
    assertThat(LinkHtmlRenderer.visitorErrorPage(LinkErrorCode.LINK_NOT_FOUND).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(LinkHtmlRenderer.visitorErrorPage(LinkErrorCode.LINK_EXPIRED).getStatusCode())
        .isEqualTo(HttpStatus.GONE);
    assertThat(LinkHtmlRenderer.visitorErrorPage(LinkErrorCode.LINK_NOT_OWNED)).isNull();
  }
}
