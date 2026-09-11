package com.example.short_link.link.safety.infrastructure;

import static com.example.short_link.common.config.SafeBrowsingConfig.SAFE_BROWSING_CB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.short_link.common.config.SafeBrowsingProperties;
import com.example.short_link.link.safety.application.UrlThreatLookupException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GoogleSafeBrowsingLookupTest {

  private final SafeBrowsingProperties properties =
      new SafeBrowsingProperties(true, "test-key", Duration.ofHours(1), Duration.ofSeconds(2));

  /** Fresh registry per test so breaker state from one test doesn't leak into another. */
  private CircuitBreakerRegistry registry() {
    return CircuitBreakerRegistry.ofDefaults();
  }

  @Test
  void returnsTrueWhenNoMatchesField() {
    RestClient.Builder builder =
        RestClient.builder().baseUrl("https://safebrowsing.googleapis.com");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(method(HttpMethod.POST))
        .andExpect(requestTo(CoreMatchers.containsString("/v4/threatMatches:find?key=test-key")))
        .andExpect(
            content()
                .json(
                    """
                    {
                      "client": {"clientId":"short-link","clientVersion":"1.0"},
                      "threatInfo": {
                        "threatTypes": ["MALWARE","SOCIAL_ENGINEERING","UNWANTED_SOFTWARE","POTENTIALLY_HARMFUL_APPLICATION"],
                        "platformTypes": ["ANY_PLATFORM"],
                        "threatEntryTypes": ["URL"],
                        "threatEntries": [{"url":"https://safe.example/page"}]
                      }
                    }
                    """))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
    var lookup = new GoogleSafeBrowsingLookup(builder.build(), properties, registry());

    assertThat(lookup.isSafe("https://safe.example/page")).isTrue();
    server.verify();
  }

  @Test
  void returnsFalseWhenMatchesPresent() {
    RestClient.Builder builder =
        RestClient.builder().baseUrl("https://safebrowsing.googleapis.com");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                "{\"matches\":[{\"threatType\":\"MALWARE\"}]}", MediaType.APPLICATION_JSON));
    var lookup = new GoogleSafeBrowsingLookup(builder.build(), properties, registry());

    assertThat(lookup.isSafe("https://malware.example/exploit")).isFalse();
    server.verify();
  }

  @Test
  void reportsProviderUnavailableAfterTheBreakerRecordsTheFailure() {
    RestClient.Builder builder =
        RestClient.builder().baseUrl("https://safebrowsing.googleapis.com");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(method(HttpMethod.POST)).andRespond(withServerError());
    CircuitBreakerRegistry registry = registry();
    var lookup = new GoogleSafeBrowsingLookup(builder.build(), properties, registry);

    assertThatThrownBy(() -> lookup.isSafe("https://timeout.example/anywhere"))
        .isInstanceOfSatisfying(
            UrlThreatLookupException.class,
            failure ->
                assertThat(failure.kind()).isEqualTo(UrlThreatLookupException.Kind.UNAVAILABLE));
    assertThat(registry.circuitBreaker(SAFE_BROWSING_CB).getMetrics().getNumberOfFailedCalls())
        .isEqualTo(1);
    server.verify();
  }

  @Test
  void failsOpenWhenCircuitBreakerIsOpen() {
    // No mock server interactions expected — breaker should short-circuit before any HTTP.
    RestClient.Builder builder =
        RestClient.builder().baseUrl("https://safebrowsing.googleapis.com");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    CircuitBreakerRegistry reg = registry();
    CircuitBreaker breaker = reg.circuitBreaker(SAFE_BROWSING_CB);
    breaker.transitionToOpenState();
    var lookup = new GoogleSafeBrowsingLookup(builder.build(), properties, reg);

    assertThat(lookup.isSafe("https://any.example/path")).isTrue();
    assertThat(breaker.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(1);
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 403})
  void authenticationErrorsAreDistinctAndStillCountAsBreakerFailures(int status) {
    RestClient.Builder builder =
        RestClient.builder().baseUrl("https://safebrowsing.googleapis.com");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.valueOf(status)));
    CircuitBreakerRegistry registry = registry();
    var lookup = new GoogleSafeBrowsingLookup(builder.build(), properties, registry);

    assertThatThrownBy(() -> lookup.isSafe("https://example.com"))
        .isInstanceOfSatisfying(
            UrlThreatLookupException.class,
            failure -> {
              assertThat(failure.kind()).isEqualTo(UrlThreatLookupException.Kind.AUTHENTICATION);
              assertThat(failure.getCause())
                  .isInstanceOf(org.springframework.web.client.HttpClientErrorException.class);
            });
    assertThat(registry.circuitBreaker(SAFE_BROWSING_CB).getMetrics().getNumberOfFailedCalls())
        .isEqualTo(1);
    server.verify();
  }
}
