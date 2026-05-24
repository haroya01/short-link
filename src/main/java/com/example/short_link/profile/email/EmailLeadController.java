package com.example.short_link.profile.email;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/email-leads")
@RequiredArgsConstructor
public class EmailLeadController {

  private final EmailLeadService service;

  @PostMapping
  public Response submit(
      @Valid @RequestBody SubmitRequest request, HttpServletRequest httpRequest) {
    service.submitPublic(request.blockId(), request.email(), clientIp(httpRequest));
    return new Response(true);
  }

  private static String clientIp(HttpServletRequest req) {
    String forwarded = req.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return req.getRemoteAddr();
  }

  public record SubmitRequest(@NotNull Long blockId, @NotNull @Size(max = 254) String email) {}

  public record Response(boolean ok) {}
}
