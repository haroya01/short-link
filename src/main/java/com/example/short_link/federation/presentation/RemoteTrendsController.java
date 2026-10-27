package com.example.short_link.federation.presentation;

import static com.example.short_link.common.seen.SeenParameters.DEVICE_HEADER;
import static com.example.short_link.common.seen.SeenParameters.HIDE_SEEN;

import com.example.short_link.common.seen.SeenViewer;
import com.example.short_link.federation.application.trends.LocalSeen;
import com.example.short_link.federation.application.trends.RemoteForYou;
import com.example.short_link.federation.application.trends.RemoteSeen;
import com.example.short_link.federation.application.trends.RemoteTrendingFeed;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import com.example.short_link.federation.presentation.request.SeenRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/federation")
@RequiredArgsConstructor
public class RemoteTrendsController {

  private final RemoteTrendingFeed trending;
  private final RemoteSeen seen;
  private final RemoteForYou forYou;
  private final LocalSeen local;

  @GetMapping("/trending")
  public RemoteTrendingFeed.Page trending(
      @AuthenticationPrincipal Long viewerId,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String cursor) {
    return trending.page(viewerId, cursor, limit);
  }

  @GetMapping("/latest")
  public RemoteTrendingFeed.Page latest(
      @AuthenticationPrincipal Long viewerId,
      @RequestHeader(name = DEVICE_HEADER, required = false) String device,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) String since) {
    return trending.latest(viewerId, SeenViewer.of(viewerId, device), cursor, since, limit);
  }

  @GetMapping("/for-you")
  public RemoteTrendingFeed.Page forYou(
      @AuthenticationPrincipal Long viewerId,
      @RequestHeader(name = DEVICE_HEADER, required = false) String device,
      @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String languages,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) String seen) {
    return forYou.page(
        viewerId,
        SeenViewer.of(viewerId, device),
        languages,
        cursor,
        limit,
        HIDE_SEEN.equals(seen));
  }

  @PostMapping("/seen")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void seen(
      @AuthenticationPrincipal Long viewerId,
      @RequestHeader(name = DEVICE_HEADER, required = false) String device,
      @RequestBody(required = false) SeenRequest request) {
    SeenViewer viewer = SeenViewer.of(viewerId, device);
    if (viewer == null) {
      throw new FederationException(FederationErrorCode.SEEN_WITHOUT_VIEWER);
    }
    if (request != null) {
      local.record(viewer, request.uris());
      seen.record(
          viewer,
          request.uris(),
          request.dwell() == null
              ? null
              : request.dwell().stream()
                  .filter(dwell -> dwell != null && dwell.ms() != null)
                  .map(dwell -> new RemoteSeen.Dwell(dwell.uri(), dwell.ms()))
                  .toList(),
          request.feed());
    }
  }
}
