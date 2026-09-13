package com.example.short_link.post.infrastructure.image;

import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.common.net.HttpFetcher.Request;
import com.example.short_link.common.net.HttpFetcher.Response;
import com.example.short_link.common.net.PublicHttpUrlGuard;
import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
import com.example.short_link.post.application.image.ExternalPostImageReader;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class HttpPostImageReader implements ExternalPostImageReader {
  private final HttpFetcher httpFetcher;
  private static final Duration IMPORT_CONNECT_TIMEOUT = Duration.ofMillis(3_000);
  private static final Duration IMPORT_READ_TIMEOUT = Duration.ofMillis(5_000);
  private static final String IMPORT_USER_AGENT = "kurl-image-import/1.0 (+https://kurl.me/bot)";
  private static final int MAX_REDIRECT_HOPS = 5;

  @Override
  public Image read(String url, long maxBytes, Long postId) {
    Resolved resolved =
        PublicHttpUrlGuard.resolve(url == null ? null : url.trim())
            .orElseThrow(
                () ->
                    new PostException(
                        PostErrorCode.PERMISSION_DENIED, "image url not allowed: " + url));

    // 캡보다 1바이트 더 받아오게 해서, 잘려서 정확히 maxBytes 인 경우와 진짜 초과를 구분한다.
    int fetchCap = (int) Math.min(maxBytes + 1, Integer.MAX_VALUE);
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("User-Agent", IMPORT_USER_AGENT);
    headers.put("Accept", "image/*");

    Response response = fetchFollowingRedirects(resolved, headers, fetchCap, url, postId);
    if (response.status() >= 400) {
      throw new PostException(
          PostErrorCode.PERMISSION_DENIED, "image fetch returned " + response.status());
    }
    byte[] body = response.body();
    if (body.length == 0) {
      throw new PostException(PostErrorCode.PERMISSION_DENIED, "empty image body");
    }
    String contentType = sniffImageType(body);
    if (contentType == null) {
      throw new PostException(
          PostErrorCode.PERMISSION_DENIED,
          "fetched bytes are not a supported image (jpeg/png/webp/gif)");
    }
    if (body.length > maxBytes) {
      throw new PostException(
          PostErrorCode.PERMISSION_DENIED,
          "image exceeds maxBytes (" + body.length + " > " + maxBytes + ")");
    }

    return new Image(body, contentType);
  }

  /**
   * 각 리다이렉트에서 {@link PublicHttpUrlGuard}로 주소를 다시 검증한다. 호스트가 바뀌는 이미지 프록시를 지원하면서 DNS 재바인딩과 사설 IP 접근을
   * 차단한다.
   */
  private Response fetchFollowingRedirects(
      Resolved first, Map<String, String> headers, int fetchCap, String originalUrl, Long postId) {
    Resolved current = first;
    for (int hop = 0; hop <= MAX_REDIRECT_HOPS; hop++) {
      Response response;
      try {
        response =
            httpFetcher.fetch(
                Request.getNoRedirects(
                    current, headers, IMPORT_CONNECT_TIMEOUT, IMPORT_READ_TIMEOUT, fetchCap));
      } catch (RuntimeException e) {
        log.warn("post image import fetch failed url={} post={}", originalUrl, postId, e);
        throw new PostException(PostErrorCode.PERMISSION_DENIED, "image fetch failed");
      }
      if (!isRedirect(response.status())) {
        return response;
      }
      String location = response.header("Location");
      if (location == null || location.isBlank()) {
        throw new PostException(PostErrorCode.PERMISSION_DENIED, "redirect missing location");
      }
      URI next;
      try {
        next = current.uri().resolve(location.trim());
      } catch (IllegalArgumentException e) {
        throw new PostException(
            PostErrorCode.PERMISSION_DENIED, "image url not allowed: " + location);
      }
      current =
          PublicHttpUrlGuard.resolve(next.toString())
              .orElseThrow(
                  () ->
                      new PostException(
                          PostErrorCode.PERMISSION_DENIED, "image url not allowed: " + next));
    }
    throw new PostException(PostErrorCode.PERMISSION_DENIED, "too many redirects");
  }

  private static boolean isRedirect(int status) {
    return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
  }

  /** 원격 Content-Type은 신뢰할 수 없고 정상 이미지도 octet-stream으로 올 수 있어 저장 타입은 바이트 시그니처로 판별한다. */
  private static String sniffImageType(byte[] b) {
    if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
      return "image/jpeg";
    }
    if (b.length >= 8
        && (b[0] & 0xFF) == 0x89
        && b[1] == 'P'
        && b[2] == 'N'
        && b[3] == 'G'
        && (b[4] & 0xFF) == 0x0D
        && (b[5] & 0xFF) == 0x0A
        && (b[6] & 0xFF) == 0x1A
        && (b[7] & 0xFF) == 0x0A) {
      return "image/png";
    }
    if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
      return "image/gif";
    }
    if (b.length >= 12
        && b[0] == 'R'
        && b[1] == 'I'
        && b[2] == 'F'
        && b[3] == 'F'
        && b[8] == 'W'
        && b[9] == 'E'
        && b[10] == 'B'
        && b[11] == 'P') {
      return "image/webp";
    }
    return null;
  }
}
