package com.example.short_link.federation.presentation;

import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.inbox.InboxMessage;
import com.example.short_link.federation.application.inbox.InboxOutcome;
import com.example.short_link.federation.application.inbox.InboxService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InboxController {

  private final InboxService inbox;

  @PostMapping("/ap/inbox")
  public ResponseEntity<Void> shared(
      @RequestBody(required = false) byte[] body, HttpServletRequest request) {
    return receive(body, request, null);
  }

  @PostMapping(FederationUrls.ACTOR_PATH + "{publicId}/inbox")
  public ResponseEntity<Void> personal(
      @PathVariable String publicId,
      @RequestBody(required = false) byte[] body,
      HttpServletRequest request) {
    return receive(body, request, publicId);
  }

  private ResponseEntity<Void> receive(byte[] body, HttpServletRequest request, String publicId) {
    InboxOutcome outcome =
        inbox.receive(
            new InboxMessage(
                request.getRequestURI(),
                request.getQueryString(),
                headers(request),
                body == null ? new byte[0] : body),
            publicId);
    int status =
        switch (outcome.kind()) {
          case ACCEPTED, IGNORED -> 202;
          case UNAUTHORIZED -> 401;
          case MALFORMED -> 400;
          case NOT_FOUND -> 404;
        };
    return ResponseEntity.status(status).build();
  }

  private static Map<String, String> headers(HttpServletRequest request) {
    Map<String, String> headers = new HashMap<>();
    for (String name : Collections.list(request.getHeaderNames())) {
      headers.put(
          name.toLowerCase(Locale.ROOT),
          String.join(", ", Collections.list(request.getHeaders(name))));
    }
    return headers;
  }
}
