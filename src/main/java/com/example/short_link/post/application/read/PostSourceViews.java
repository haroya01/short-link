package com.example.short_link.post.application.read;

import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.post.domain.PostEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostSourceViews {

  private final ShortLinkUrlBuilder shortLinks;

  public PostSourceView of(PostEntity post) {
    if (!post.isImported()) {
      return null;
    }
    String shortUrl =
        post.getSourceLinkId() == null || post.getSourceShortCode() == null
            ? null
            : shortLinks.build(ShortCode.of(post.getSourceShortCode()));
    return new PostSourceView(
        post.getSourcePlatform(), post.getSourceUrl(), shortUrl, post.isSourceExcerptOnly());
  }
}
