package com.example.short_link.profile.presentation.email;

import com.example.short_link.profile.application.email.EmailLeadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
  public EmailLeadSubmitResponse submit(
      @Valid @RequestBody EmailLeadSubmitRequest request, HttpServletRequest httpRequest) {
    service.submitPublic(request.blockId(), request.email(), clientIp(httpRequest));
    return new EmailLeadSubmitResponse(true);
  }

  private static String clientIp(HttpServletRequest req) {
    String forwarded = req.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return req.getRemoteAddr();
  }
}
