package com.example.short_link.profile.presentation.email;

import com.example.short_link.common.web.ClientIp;
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
    service.submitPublic(request.blockId(), request.email(), ClientIp.of(httpRequest));
    return new EmailLeadSubmitResponse(true);
  }
}
