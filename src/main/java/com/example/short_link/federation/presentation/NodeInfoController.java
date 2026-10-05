package com.example.short_link.federation.presentation;

import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.presentation.response.NodeInfoLinksResponse;
import com.example.short_link.federation.presentation.response.NodeInfoResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Server-level metadata only; per-user counts stay out like everywhere else in kurl.
@RestController
@RequiredArgsConstructor
public class NodeInfoController {

  static final String SCHEMA = "http://nodeinfo.diaspora.software/ns/schema/2.1";
  private static final MediaType NODEINFO =
      MediaType.parseMediaType("application/json; profile=\"" + SCHEMA + "#\"");

  private final FederationUrls urls;

  @GetMapping("/.well-known/nodeinfo")
  public ResponseEntity<NodeInfoLinksResponse> discovery() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
        .body(
            new NodeInfoLinksResponse(
                List.of(new NodeInfoLinksResponse.Link(SCHEMA, urls.nodeInfo()))));
  }

  @GetMapping("/ap/nodeinfo/2.1")
  public ResponseEntity<NodeInfoResponse> nodeInfo() {
    return ResponseEntity.ok()
        .contentType(NODEINFO)
        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
        .body(
            new NodeInfoResponse(
                "2.1",
                new NodeInfoResponse.Software(
                    "kurl", "1.0.0", "https://github.com/haroya01/short-link", "https://kurl.me"),
                List.of("activitypub"),
                new NodeInfoResponse.Services(List.of(), List.of()),
                true,
                new NodeInfoResponse.Usage(Map.of(), 0),
                Map.of()));
  }
}
