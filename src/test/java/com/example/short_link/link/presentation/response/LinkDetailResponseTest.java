package com.example.short_link.link.presentation.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.dto.LinkDetailView;
import com.example.short_link.link.domain.ShortCode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinkDetailResponseTest {

  @Test
  void mapsAllFieldsFromView() {
    Instant expires = Instant.parse("2027-01-01T00:00:00Z");
    LinkDetailView view =
        new LinkDetailView(
            new ShortCode("abc123"),
            "https://example.com/origin",
            expires,
            "og title",
            "og desc",
            "https://example.com/og.png",
            "title override",
            "desc override",
            "https://example.com/og-override.png",
            true,
            10,
            3,
            true,
            List.of("a", "b"),
            "note body",
            "expired msg");

    LinkDetailResponse response = LinkDetailResponse.from(view);

    assertThat(response.shortCode().value()).isEqualTo("abc123");
    assertThat(response.originalUrl()).isEqualTo("https://example.com/origin");
    assertThat(response.expiresAt()).isEqualTo(expires);
    assertThat(response.ogTitle()).isEqualTo("og title");
    assertThat(response.ogDescription()).isEqualTo("og desc");
    assertThat(response.ogImage()).isEqualTo("https://example.com/og.png");
    assertThat(response.ogTitleOverride()).isEqualTo("title override");
    assertThat(response.ogDescriptionOverride()).isEqualTo("desc override");
    assertThat(response.ogImageOverride()).isEqualTo("https://example.com/og-override.png");
    assertThat(response.passwordProtected()).isTrue();
    assertThat(response.maxViews()).isEqualTo(10);
    assertThat(response.viewCount()).isEqualTo(3);
    assertThat(response.statsPublic()).isTrue();
    assertThat(response.tags()).containsExactly("a", "b");
    assertThat(response.note()).isEqualTo("note body");
    assertThat(response.expiredMessage()).isEqualTo("expired msg");
  }

  @Test
  void preservesNullableFields() {
    LinkDetailView view =
        new LinkDetailView(
            new ShortCode("xyz"),
            "https://example.com",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            0,
            false,
            List.of(),
            null,
            null);

    LinkDetailResponse response = LinkDetailResponse.from(view);

    assertThat(response.expiresAt()).isNull();
    assertThat(response.maxViews()).isNull();
    assertThat(response.passwordProtected()).isFalse();
    assertThat(response.tags()).isEmpty();
  }
}
