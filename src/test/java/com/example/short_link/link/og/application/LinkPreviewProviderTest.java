package com.example.short_link.link.og.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.common.link.LinkPreviewReader.Preview;
import com.example.short_link.link.og.application.dto.LinkPreview;
import org.junit.jupiter.api.Test;

class LinkPreviewProviderTest {

  private final LinkPreviewService service = mock(LinkPreviewService.class);
  private final LinkPreviewProvider provider = new LinkPreviewProvider(service);

  @Test
  void aPageWithATitleOrAnImageBecomesACard() {
    when(service.fetch("https://a.example"))
        .thenReturn(new LinkPreview("https://a.example", "T", "D", null));
    when(service.fetch("https://b.example"))
        .thenReturn(new LinkPreview("https://b.example", null, null, "https://b.example/i.png"));

    assertThat(provider.read("https://a.example"))
        .contains(new Preview("https://a.example", "T", "D", null));
    assertThat(provider.read("https://b.example")).isPresent();
  }

  @Test
  void aBarePageIsNoCard() {
    when(service.fetch("https://c.example")).thenReturn(LinkPreview.bare("https://c.example"));

    assertThat(provider.read("https://c.example")).isEmpty();
  }
}
