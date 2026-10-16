package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostEntity;
import java.util.List;

public record OwnerAudience(
    boolean hasPassword, String unlistedUrl, List<String> blockedCountries) {

  public static OwnerAudience of(PostEntity post, String unlistedUrl) {
    return new OwnerAudience(post.hasPassword(), unlistedUrl, post.blockedCountryCodes());
  }
}
