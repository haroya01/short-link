package com.example.short_link.common.post;

/** The post slice implements this port to avoid a dependency cycle with profiles. */
public interface PublishedPostCountReader {

  long countPublishedByUserId(Long userId);
}
