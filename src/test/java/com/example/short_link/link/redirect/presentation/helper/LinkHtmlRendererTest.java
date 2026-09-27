package com.example.short_link.link.redirect.presentation.helper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.exception.LinkErrorCode;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class LinkHtmlRendererTest {

  private static final ShortCode CODE = ShortCode.of("abc123");
  private static final Locale KO = Locale.KOREAN;

  private static MessageSource messageSource() {
    var ms = new ResourceBundleMessageSource();
    ms.setBasename("messages");
    ms.setDefaultEncoding("UTF-8");
    ms.setFallbackToSystemLocale(false);
    return ms;
  }

  private final LinkHtmlRenderer renderer = new LinkHtmlRenderer(messageSource());

  @Test
  void passwordPrompt_withSiteKey_rendersTurnstileWidgetAndScript() {
    String html = renderer.passwordPrompt(KO, CODE, false, "0xSITEKEY");

    assertThat(html).contains("challenges.cloudflare.com/turnstile/v0/api.js");
    assertThat(html).contains("class=\"cf-turnstile\" data-sitekey=\"0xSITEKEY\"");
    assertThat(html).doesNotContain("비밀번호가 올바르지 않아요");
  }

  @Test
  void passwordPrompt_withoutSiteKey_omitsWidget() {
    String html = renderer.passwordPrompt(KO, CODE, false, null);

    assertThat(html).doesNotContain("cf-turnstile");
    assertThat(html).doesNotContain("turnstile/v0/api.js");
  }

  @Test
  void passwordPrompt_blankSiteKey_treatedAsAbsent() {
    String html = renderer.passwordPrompt(KO, CODE, false, "   ");

    assertThat(html).doesNotContain("cf-turnstile");
  }

  @Test
  void passwordPrompt_failed_showsError() {
    String html = renderer.passwordPrompt(KO, CODE, true, null);

    assertThat(html).contains("비밀번호가 올바르지 않아요");
    assertThat(html).contains("action=\"/abc123\"");
  }

  @Test
  void passwordPrompt_escapesSiteKey() {
    String html = renderer.passwordPrompt(KO, CODE, false, "a\"<b");

    assertThat(html).contains("a&quot;&lt;b");
    assertThat(html).doesNotContain("data-sitekey=\"a\"<b\"");
  }

  @Test
  void passwordPromptResponse_buildsHtmlResponseWithStatus() {
    ResponseEntity<byte[]> response =
        renderer.passwordPromptResponse(KO, HttpStatus.UNAUTHORIZED, CODE, true, "0xKEY");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getHeaders().getFirst("X-Robots-Tag")).isEqualTo("noindex, nofollow");
    String body = new String(response.getBody());
    assertThat(body).contains("cf-turnstile");
    assertThat(body).contains("비밀번호가 올바르지 않아요");
  }

  @Test
  void blockedAndExpiredPages_render() {
    assertThat(renderer.blockedPageResponse(KO).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    ResponseEntity<byte[]> expired = renderer.expiredPageResponse(KO, "만료된 캠페인 <link>");
    assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.GONE);
    assertThat(new String(expired.getBody())).contains("&lt;link&gt;");
  }

  @Test
  void expiredPage_blankMessage_usesDefaultCopy() {
    ResponseEntity<byte[]> expired = renderer.expiredPageResponse(KO, null);
    assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.GONE);
    assertThat(new String(expired.getBody())).contains("만료");
  }

  @Test
  void viewLimitPage_isGone_withReason() {
    ResponseEntity<byte[]> r = renderer.viewLimitPageResponse(KO);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.GONE);
    assertThat(new String(r.getBody())).contains("조회 한도");
  }

  @Test
  void notFoundPage_is404() {
    assertThat(renderer.notFoundPageResponse(KO).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void passwordPrompt_failed_addsShakeClass() {
    assertThat(renderer.passwordPrompt(KO, CODE, true, null)).contains("card shake");
    assertThat(renderer.passwordPrompt(KO, CODE, false, null)).doesNotContain("card shake");
  }

  @Test
  void unlockedPage_animatesAndForwardsToEscapedDestination() {
    ResponseEntity<byte[]> r = renderer.unlockedPageResponse(KO, "https://example.com/a?b=1&c=2");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    String body = new String(r.getBody());
    assertThat(body).contains("http-equiv=\"refresh\"");
    assertThat(body).contains("data-u=\"https://example.com/a?b=1&amp;c=2\"");
    assertThat(body).contains("bigmark");
  }

  @Test
  void unlockedPage_escapesScriptInjectionInDestination() {
    String evil = "https://x/\"></script><script>alert(1)</script>";
    String body = new String(renderer.unlockedPageResponse(KO, evil).getBody());
    assertThat(body).doesNotContain("<script>alert(1)</script>");
    assertThat(body).contains("&lt;script&gt;");
  }

  @Test
  void visitorErrorPage_mapsVisitorCodes_elseNull() {
    assertThat(
            renderer.visitorErrorPage(KO, LinkErrorCode.LINK_VIEW_LIMIT_EXCEEDED).getStatusCode())
        .isEqualTo(HttpStatus.GONE);
    assertThat(renderer.visitorErrorPage(KO, LinkErrorCode.LINK_NOT_FOUND).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(renderer.visitorErrorPage(KO, LinkErrorCode.LINK_EXPIRED).getStatusCode())
        .isEqualTo(HttpStatus.GONE);
    assertThat(renderer.visitorErrorPage(KO, LinkErrorCode.LINK_NOT_OWNED)).isNull();
  }

  @Test
  void passwordPrompt_speaksTheVisitorsLanguage() {
    String html = renderer.passwordPrompt(Locale.JAPANESE, CODE, true, null);

    assertThat(html).contains("<html lang=\"ja\">");
    assertThat(html).contains("パスワードが必要なリンク");
    assertThat(html).contains("パスワードが正しくありません");
    assertThat(html).contains(">開く</button>");
    assertThat(html).doesNotContain("비밀번호");
  }

  @Test
  void pagesUseEnglishWhenTheLanguageIsNotSupported() {
    String body = new String(renderer.notFoundPageResponse(Locale.ENGLISH).getBody());

    assertThat(body).contains("<html lang=\"en\">");
    assertThat(body).contains("Link not found");
  }

  @Test
  void unlockedPage_keepsTheBrandBoldInEveryLanguage() {
    for (Locale locale :
        List.of(KO, Locale.ENGLISH, Locale.JAPANESE, Locale.of("vi"), Locale.of("hi"))) {
      String body =
          new String(renderer.unlockedPageResponse(locale, "https://example.com").getBody());
      assertThat(body).contains("<b>kurl</b>");
      assertThat(body).doesNotContain("\u0000");
    }
  }

  @Test
  void responsesVaryByLanguage() {
    assertThat(renderer.notFoundPageResponse(KO).getHeaders().getFirst("Vary"))
        .isEqualTo("Accept-Language");
  }

  @Test
  void everyLanguageTranslatesEveryVisitorMessage() throws IOException {
    Set<String> english = visitorKeys("messages.properties");
    assertThat(english).isNotEmpty();
    for (String file :
        List.of(
            "messages_ko.properties",
            "messages_ja.properties",
            "messages_vi.properties",
            "messages_hi.properties")) {
      assertThat(visitorKeys(file)).as(file).isEqualTo(english);
    }
  }

  private static Set<String> visitorKeys(String file) throws IOException {
    Properties props = new Properties();
    try (var in = LinkHtmlRendererTest.class.getClassLoader().getResourceAsStream(file)) {
      props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
    return props.stringPropertyNames().stream()
        .filter(k -> k.startsWith("visitor."))
        .collect(Collectors.toSet());
  }
}
