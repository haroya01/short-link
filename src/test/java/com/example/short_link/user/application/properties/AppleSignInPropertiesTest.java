package com.example.short_link.user.application.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AppleSignInPropertiesTest {

  private static final String[] EVERY_CLIENT = {
    "focustime.kurl", "focustime.kurl.links", "me.kurl.signin"
  };

  @Test
  void defaultsAcceptBothNativeAppsAndTheWebServicesId() {
    AppleSignInProperties props = new AppleSignInProperties(null, null, null);

    // 블로그 앱과 링크 앱은 번들 id 가 다르고 identity token 의 aud 는 각자 자기 번들 id 다.
    // 링크 앱이 목록에서 빠지면 그 앱의 Apple 로그인은 전부 401 — App Review 리젝 재발.
    // 웹 토큰의 aud 는 Services ID 라서, 그게 빠지면 웹 Apple 로그인이 전부 401.
    assertThat(props.clientIds()).containsExactlyInAnyOrder(EVERY_CLIENT);
    assertThat(props.issuer()).isEqualTo("https://appleid.apple.com");
    assertThat(props.jwkSetUri()).isEqualTo("https://appleid.apple.com/auth/keys");
  }

  @Test
  void explicitClientIdsAreKeptAsGiven() {
    AppleSignInProperties props =
        new AppleSignInProperties(null, null, List.of("focustime.kurl", "me.kurl.signin"));

    assertThat(props.clientIds()).containsExactly("focustime.kurl", "me.kurl.signin");
  }

  @Test
  void emptyEnvironmentValueStillAcceptsEveryClient() {
    new ApplicationContextRunner()
        .withUserConfiguration(AppleSettings.class)
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withPropertyValues(
            "spring.config.location=classpath:/application.yml", "APPLE_SIGNIN_CLIENT_IDS=")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(AppleSignInProperties.class).clientIds())
                  .containsExactlyInAnyOrder(EVERY_CLIENT);
            });
  }

  @Test
  void applicationYamlAndComposeDefaultsListTheSameClientsAsTheFallback() throws IOException {
    List<String> fallback = new AppleSignInProperties(null, null, null).clientIds();

    assertThat(writtenDefault("src/main/resources/application.yml", "${APPLE_SIGNIN_CLIENT_IDS:"))
        .as("application.yml default")
        .containsExactlyInAnyOrderElementsOf(fallback);
    assertThat(writtenDefault("deploy/docker-compose.yml", "${APPLE_SIGNIN_CLIENT_IDS:-"))
        .as("docker-compose.yml default")
        .containsExactlyInAnyOrderElementsOf(fallback);
  }

  private static List<String> writtenDefault(String file, String placeholder) throws IOException {
    Matcher matcher =
        Pattern.compile(Pattern.quote(placeholder) + "([^}]*)}")
            .matcher(Files.readString(Path.of(file)));
    assertThat(matcher.find()).as("%s in %s", placeholder, file).isTrue();
    return Arrays.stream(matcher.group(1).split(",")).map(String::trim).toList();
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(AppleSignInProperties.class)
  static class AppleSettings {}
}
