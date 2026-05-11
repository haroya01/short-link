package com.example.short_link.profile.email;

import com.example.short_link.profile.application.InvalidUsernameException;
import com.example.short_link.profile.application.ProfileNotFoundException;
import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.ProfileBlockRepository;
import com.example.short_link.profile.domain.ProfileBlockType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/email-leads")
@RequiredArgsConstructor
public class EmailLeadController {

  private final EmailLeadService service;
  private final ProfileBlockRepository blockRepository;

  @PostMapping
  public ResponseEntity<Response> submit(
      @RequestBody SubmitRequest request, HttpServletRequest httpRequest) {
    ProfileBlockEntity block =
        blockRepository
            .findById(request.blockId())
            .filter(b -> b.getType() == ProfileBlockType.EMAIL_FORM)
            .orElseThrow(() -> new ProfileNotFoundException("block " + request.blockId()));
    service.submit(block.getUserId(), block.getId(), request.email(), clientIp(httpRequest));
    return ResponseEntity.ok(new Response(true));
  }

  @ExceptionHandler(ProfileNotFoundException.class)
  public ResponseEntity<Response> notFound() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Response(false));
  }

  @ExceptionHandler(InvalidUsernameException.class)
  public ResponseEntity<Response> badRequest() {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Response(false));
  }

  @ExceptionHandler(EmailLeadRateLimitedException.class)
  public ResponseEntity<Response> tooMany() {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new Response(false));
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
