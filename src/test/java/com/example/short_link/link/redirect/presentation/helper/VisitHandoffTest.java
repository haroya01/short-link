package com.example.short_link.link.redirect.presentation.helper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.classifier.application.ClientAppClassifier;
import com.example.short_link.link.redirect.application.RedirectOutcome;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;

class VisitHandoffTest {

  private static final String LINE = "Mozilla/5.0 (iPhone) Safari Line/13.16.0";
  private static final String KAKAOTALK = "Mozilla/5.0 (iPhone) KAKAOTALK 10.4.0";
  private static final String INSTAGRAM = "Mozilla/5.0 (iPhone) Instagram 300.0";

  private final VisitHandoff handoff = new VisitHandoff(new ClientAppClassifier(), renderer());

  private static LinkHtmlRenderer renderer() {
    var ms = new ResourceBundleMessageSource();
    ms.setBasename("messages");
    ms.setDefaultEncoding("UTF-8");
    ms.setFallbackToSystemLocale(false);
    return new LinkHtmlRenderer(ms);
  }

  private static RedirectOutcome.Redirect to(String url, boolean openInBrowser) {
    return new RedirectOutcome.Redirect(
        new CachedLink.Picked(url, null), new CachedLink.VisitOptions(openInBrowser));
  }

  @Test
  void addsTheLineParameterWithoutBreakingQueryOrFragment() {
    assertThat(VisitHandoff.withParam("https://a.com", "x=1")).isEqualTo("https://a.com?x=1");
    assertThat(VisitHandoff.withParam("https://a.com/p?q=1", "x=1"))
        .isEqualTo("https://a.com/p?q=1&x=1");
    assertThat(VisitHandoff.withParam("https://a.com/p?", "x=1")).isEqualTo("https://a.com/p?x=1");
    assertThat(VisitHandoff.withParam("https://a.com/p?q=1#h", "x=1"))
        .isEqualTo("https://a.com/p?q=1&x=1#h");
  }

  @Test
  void onlyKakaoTalkAndLineAreHandedOff() {
    var redirect = to("https://dest.example.com/", true);

    assertThat(handoff.redirect(redirect, LINE, Locale.KOREAN).getHeaders().getLocation())
        .hasToString("https://dest.example.com/?openExternalBrowser=1");
    assertThat(handoff.redirect(redirect, KAKAOTALK, Locale.KOREAN).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(handoff.redirect(redirect, INSTAGRAM, Locale.KOREAN).getHeaders().getLocation())
        .hasToString("https://dest.example.com/");
    assertThat(handoff.redirect(redirect, null, Locale.KOREAN).getHeaders().getLocation())
        .hasToString("https://dest.example.com/");
  }

  @Test
  void nothingChangesWhileTheOptionIsOff() {
    var redirect = to("https://dest.example.com/", false);

    assertThat(handoff.redirect(redirect, LINE, Locale.KOREAN).getHeaders().getLocation())
        .hasToString("https://dest.example.com/");
    assertThat(handoff.redirect(redirect, KAKAOTALK, Locale.KOREAN).getStatusCode())
        .isEqualTo(HttpStatus.FOUND);
  }

  @Test
  void unlockedLinksContinueThroughTheSameHandoff() {
    var redirect = to("https://dest.example.com/a?b=1", true);

    String line = new String(handoff.unlocked(redirect, LINE, Locale.KOREAN).getBody());
    String kakao = new String(handoff.unlocked(redirect, KAKAOTALK, Locale.KOREAN).getBody());

    assertThat(line)
        .contains("data-u=\"https://dest.example.com/a?b=1&amp;openExternalBrowser=1\"");
    assertThat(kakao)
        .contains(
            "data-u=\"kakaotalk://web/openExternal?url=https%3A%2F%2Fdest.example.com%2Fa%3Fb%3D1\"");
  }
}
