package com.example.short_link.post.application.image;

public interface ExternalPostImageReader {
  Image read(String url, long maxBytes, Long postId);

  record Image(byte[] body, String contentType) {}
}
