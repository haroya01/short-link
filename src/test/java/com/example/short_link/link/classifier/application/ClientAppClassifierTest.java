package com.example.short_link.link.classifier.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ClientAppClassifierTest {

  private final ClientAppClassifier classifier = new ClientAppClassifier();

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like"
            + " Gecko) Mobile/15E148 KAKAOTALK 10.4.5|kakaotalk",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like"
            + " Gecko) Mobile/15E148 Instagram 320.0.0.0.0|instagram",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like"
            + " Gecko) Mobile/15E148 Line/13.16.0|line",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like"
            + " Gecko) Mobile/15E148 [FBAN/FBIOS;FBAV/450.0.0.35.109]|facebook",
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0"
            + " Mobile Safari/537.36 [FB_IAB/FB4A;FBAV/450.0.0.35.109]|facebook",
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0"
            + " Mobile Safari/537.36 NAVER(inapp; search; 1000; 12.9.2)|naver",
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0"
            + " Mobile Safari/537.36 DaumApps/5.14.0|daum",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like"
            + " Gecko) musical_ly_32.5.0 JsSdk/2.0|tiktok",
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0"
            + " Mobile Safari/537.36 trill_320402 TikTok 32.4.2|tiktok",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like"
            + " Gecko) Mobile/15E148 Twitter for iPhone|twitter",
      })
  void namesTheInAppBrowserFromTheUserAgent(String userAgent, String expected) {
    assertThat(classifier.classify(userAgent)).isEqualTo(expected);
  }

  /** 일반 브라우저는 "인앱이 아님"이라 null — 별도 'browser' 라벨을 만들지 않는다. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like"
            + " Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
            + " Chrome/122.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:124.0) Gecko/20100101 Firefox/124.0",
        "Mozilla/5.0 (Linux; Android 14; SM-S928N) AppleWebKit/537.36 (KHTML, like Gecko)"
            + " SamsungBrowser/24.0 Chrome/117.0.0.0 Mobile Safari/537.36",
      })
  void ordinaryBrowsersAreNotInApp(String userAgent) {
    assertThat(classifier.classify(userAgent)).isNull();
  }

  @Test
  void missingUserAgentIsNotInApp() {
    assertThat(classifier.classify(null)).isNull();
    assertThat(classifier.classify("")).isNull();
    assertThat(classifier.classify("   ")).isNull();
  }

  /**
   * 페이스북 인앱 UA 는 브라우저 스택을 공유해 Instagram 토큰을 함께 싣기도 한다. 두 규칙이 다 걸리면 인스타그램이 이긴다 — V117 백필의 UPDATE 순서와
   * 같은 결정이라, 백필한 과거 행과 쓰기 시점 분류가 같은 답을 낸다.
   */
  @Test
  void instagramWinsWhenTheUserAgentCarriesBothTokens() {
    String both =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like"
            + " Gecko) Instagram 320.0.0.0.0 [FBAN/FBIOS;FBAV/450.0.0.35.109]";

    assertThat(classifier.classify(both)).isEqualTo("instagram");
  }

  /** "Line/" 은 슬래시까지 봐야 한다 — 그러지 않으면 Streamline·Headless 같은 무관한 토큰이 라인으로 잡힌다. */
  @Test
  void bareLineSubstringIsNotLineApp() {
    String headless =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko)"
            + " HeadlessChrome/122.0.0.0 Safari/537.36";

    assertThat(classifier.classify(headless)).isNull();
  }

  /** 분류 결과는 client_app 컬럼(VARCHAR(32))에 그대로 들어간다 — 잘려서 저장되면 집계가 갈라진다. */
  @Test
  void everyAppNameFitsTheColumn() {
    String[] uas = {
      "KAKAOTALK",
      "Instagram",
      "Line/1",
      "FBAV",
      "FB_IAB",
      "NAVER(inapp",
      "DaumApps",
      "musical_ly",
      "TikTok",
      "Twitter"
    };
    for (String ua : uas) {
      assertThat(classifier.classify(ua)).hasSizeLessThanOrEqualTo(ClientAppClassifier.MAX_LENGTH);
    }
  }
}
