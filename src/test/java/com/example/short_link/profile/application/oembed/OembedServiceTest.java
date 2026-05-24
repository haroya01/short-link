package com.example.short_link.profile.application.oembed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.short_link.profile.exception.OembedNotApplicableException;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OembedServiceTest {

  @Test
  void returnsParsedResponseFromProvider() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(method(HttpMethod.GET))
        .andExpect(requestTo(CoreMatchers.startsWith("https://www.youtube.com/oembed")))
        .andRespond(
            withSuccess(
                "{\"type\":\"video\",\"title\":\"hello\",\"author_name\":\"alice\","
                    + "\"thumbnail_url\":\"https://img.example/t.jpg\","
                    + "\"html\":\"<iframe src=\\\"x\\\"></iframe>\",\"width\":480,\"height\":270}",
                MediaType.APPLICATION_JSON));
    OembedService service = new OembedService(builder.build());

    OembedResponse out = service.fetch("https://youtu.be/abc123");

    assertThat(out).isNotNull();
    assertThat(out.provider()).isEqualTo("youtube");
    assertThat(out.type()).isEqualTo("video");
    assertThat(out.title()).isEqualTo("hello");
    assertThat(out.authorName()).isEqualTo("alice");
    assertThat(out.thumbnailUrl()).isEqualTo("https://img.example/t.jpg");
    assertThat(out.html()).contains("iframe");
    assertThat(out.width()).isEqualTo(480);
    assertThat(out.height()).isEqualTo(270);
    server.verify();
  }

  @Test
  void throwsForUnsupportedProvider() {
    // No HTTP call should happen — unsupported host is rejected up-front with the domain
    // exception the controller layer maps to 422.
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    OembedService service = new OembedService(builder.build());

    assertThatThrownBy(() -> service.fetch("https://example.com/x"))
        .isInstanceOf(OembedNotApplicableException.class);
    server.verify();
  }

  @Test
  void returnsEmptyResponseOnProviderError() {
    // 5xx from provider shouldn't surface to the user — frontend renders the raw URL fallback.
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(method(HttpMethod.GET)).andRespond(withServerError());
    OembedService service = new OembedService(builder.build());

    OembedResponse out = service.fetch("https://vimeo.com/12345");

    assertThat(out).isNotNull();
    assertThat(out.provider()).isEqualTo("vimeo");
    assertThat(out.title()).isNull();
    assertThat(out.html()).isNull();
  }
}
