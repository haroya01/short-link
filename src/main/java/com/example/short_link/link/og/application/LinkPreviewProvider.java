package com.example.short_link.link.og.application;

import com.example.short_link.common.link.LinkPreviewReader;
import com.example.short_link.link.og.application.dto.LinkPreview;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class LinkPreviewProvider implements LinkPreviewReader {

  private final LinkPreviewService previews;

  @Override
  public Optional<Preview> read(String url) {
    LinkPreview preview = previews.fetch(url);
    if (preview.title() == null && preview.image() == null) {
      return Optional.empty();
    }
    return Optional.of(
        new Preview(preview.url(), preview.title(), preview.description(), preview.image()));
  }
}
