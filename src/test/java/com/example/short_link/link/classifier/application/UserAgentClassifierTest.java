package com.example.short_link.link.classifier.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.dto.UserAgentInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class UserAgentClassifierTest {

  @Autowired private UserAgentClassifier classifier;

  @Test
  void classifiesIPhoneAsMobile() {
    UserAgentInfo info =
        classifier.classify(
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1");

    assertThat(info.deviceClass()).isEqualTo("mobile");
    assertThat(info.bot()).isFalse();
  }

  @Test
  void classifiesIPadAsTablet() {
    UserAgentInfo info =
        classifier.classify(
            "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Mobile/15E148 Safari/604.1");

    assertThat(info.deviceClass()).isEqualTo("tablet");
  }

  @Test
  void classifiesWindowsAsDesktop() {
    UserAgentInfo info =
        classifier.classify(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");

    assertThat(info.deviceClass()).isEqualTo("desktop");
    assertThat(info.bot()).isFalse();
  }

  @Test
  void classifiesGooglebotAsBot() {
    UserAgentInfo info =
        classifier.classify(
            "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)");

    assertThat(info.bot()).isTrue();
    assertThat(info.deviceClass()).isEqualTo("bot");
  }

  @Test
  void classifiesNullAsUnknown() {
    UserAgentInfo info = classifier.classify(null);

    assertThat(info.deviceClass()).isEqualTo("unknown");
    assertThat(info.bot()).isFalse();
  }

  @Test
  void classifiesBlankAsUnknown() {
    UserAgentInfo info = classifier.classify("");

    assertThat(info.deviceClass()).isEqualTo("unknown");
  }

  @Test
  void mapsUnknownYauaaValuesToNull() {
    UserAgentInfo info = classifier.classify("FooBarApp/1.0");

    assertThat(info.osName()).isNull();
  }
}
