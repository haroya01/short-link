package com.example.short_link.post.collection.application;

import com.example.short_link.post.collection.domain.CollectionEntity;
import com.example.short_link.post.collection.domain.CollectionVisibility;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CollectionUnlistedUrls {

  private final String blogBaseUrl;

  public CollectionUnlistedUrls(@Value("${short-link.blog-base-url}") String blogBaseUrl) {
    this.blogBaseUrl =
        blogBaseUrl.endsWith("/")
            ? blogBaseUrl.substring(0, blogBaseUrl.length() - 1)
            : blogBaseUrl;
  }

  public String of(CollectionEntity collection) {
    if (collection.getVisibility() != CollectionVisibility.UNLISTED
        || collection.getUnlistedKey() == null) {
      return null;
    }
    return blogBaseUrl + "/collections/" + collection.getId() + "?k=" + collection.getUnlistedKey();
  }
}
