package com.example.short_link.link.safety.application;

import static com.example.short_link.common.config.SafeBrowsingConfig.SAFE_BROWSING_CB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.short_link.common.config.SafeBrowsingProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SafeBrowsingClientTest {

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
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
    SafeBrowsingClient client = new SafeBrowsingClient(builder.build(), properties, registry());

    assertThat(client.isSafeForKey("https://safe.example", "https://safe.example/page")).isTrue();
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
    SafeBrowsingClient client = new SafeBrowsingClient(builder.build(), properties, registry());

    assertThat(client.isSafeForKey("https://malware.example", "https://malware.example/exploit"))
        .isFalse();
  }

  @Test
  void propagatesUpstreamErrors() {
    RestClient.Builder builder =
        RestClient.builder().baseUrl("https://safebrowsing.googleapis.com");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(method(HttpMethod.POST)).andRespond(withServerError());
    SafeBrowsingClient client = new SafeBrowsingClient(builder.build(), properties, registry());

    assertThatThrownBy(
            () ->
                client.isSafeForKey("https://timeout.example", "https://timeout.example/anywhere"))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void failsOpenWhenCircuitBreakerIsOpen() {
    // No mock server interactions expected — breaker should short-circuit before any HTTP.
    RestClient.Builder builder =
        RestClient.builder().baseUrl("https://safebrowsing.googleapis.com");
    CircuitBreakerRegistry reg = registry();
    CircuitBreaker breaker = reg.circuitBreaker(SAFE_BROWSING_CB);
    breaker.transitionToOpenState();
    SafeBrowsingClient client = new SafeBrowsingClient(builder.build(), properties, reg);

    assertThat(client.isSafeForKey("https://any.example", "https://any.example/path")).isTrue();
  }
}
