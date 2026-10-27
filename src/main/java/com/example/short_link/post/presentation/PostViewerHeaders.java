package com.example.short_link.post.presentation;

import com.example.short_link.common.web.ClientIp;
import com.example.short_link.post.application.access.PostReader;
import jakarta.servlet.http.HttpServletRequest;

public final class PostReaderHeaders {

  public static final String KEY_HEADER = "X-Post-Key";
  public static final String GRANT_HEADER = "X-Post-Grant";
  public static final String RENDER_HEADER = "X-Kurl-Render";

  private PostReaderHeaders() {}

  public static PostReader from(Long viewerId, HttpServletRequest req) {
    return new PostReader(
        viewerId,
        present(req.getHeader(KEY_HEADER)),
        present(req.getHeader(GRANT_HEADER)),
        ClientIp.of(req),
        "server".equalsIgnoreCase(req.getHeader(RENDER_HEADER)));
  }

  private static String present(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
