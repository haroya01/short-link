package com.example.short_link.post.application.image;

/** 외부 URL에서 크기와 이미지 형식을 검증한 본문을 가져온다. */
public interface ExternalPostImageReader {
  Image read(String url, long maxBytes, Long postId);

  record Image(byte[] body, String contentType) {}
}
