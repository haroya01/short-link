package com.example.short_link.link.api;

import com.example.short_link.link.application.LinkDestinationService;
import com.example.short_link.link.application.LinkDestinationService.DestinationSummary;
import com.example.short_link.link.application.read.LinkDestinationQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
@RequiredArgsConstructor
public class LinkDestinationController {

  private static final String DEVICE_PATTERN = "^(mobile|tablet|desktop)?$";
  private static final String OS_PATTERN = "^(ios|android|windows|macos|linux)?$";

  private final LinkDestinationService service;
  private final LinkDestinationQueryService query;

  @GetMapping("/{shortCode}/destinations")
  public List<DestinationSummary> list(
      @AuthenticationPrincipal Long userId, @PathVariable String shortCode) {
    return query.list(userId, shortCode);
  }

  @PostMapping("/{shortCode}/destinations")
  public ResponseEntity<DestinationSummary> add(
      @AuthenticationPrincipal Long userId,
      @PathVariable String shortCode,
      @RequestBody AddRequest request) {
    DestinationSummary added =
        service.add(
            userId,
            shortCode,
            request.url(),
            request.weight(),
            request.label(),
            request.countryCode(),
            request.deviceClass(),
            request.os());
    return ResponseEntity.status(HttpStatus.CREATED).body(added);
  }

  @PatchMapping("/{shortCode}/destinations/{id}")
  public DestinationSummary update(
      @AuthenticationPrincipal Long userId,
      @PathVariable String shortCode,
      @PathVariable Long id,
      @RequestBody UpdateRequest request) {
    return service.update(
        userId,
        shortCode,
        id,
        request.url(),
        request.weight(),
        request.label(),
        request.enabled(),
        request.countryCode(),
        request.deviceClass(),
        request.os());
  }

  @DeleteMapping("/{shortCode}/destinations/{id}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal Long userId, @PathVariable String shortCode, @PathVariable Long id) {
    service.delete(userId, shortCode, id);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{shortCode}/blocked-countries")
  public BlockedCountriesResponse setBlocked(
      @AuthenticationPrincipal Long userId,
      @PathVariable String shortCode,
      @RequestBody BlockedCountriesRequest request) {
    var link = service.setBlockedCountries(userId, shortCode, request.codes());
    return new BlockedCountriesResponse(link.getBlockedCountries());
  }

  public record AddRequest(
      @NotBlank @Size(max = 2048) String url,
      @Min(1) @Max(100) Integer weight,
      @Size(max = 40) String label,
      @Pattern(regexp = "^[A-Za-z]{2}$", message = "countryCode must be ISO-3166 alpha-2")
          String countryCode,
      @Pattern(regexp = DEVICE_PATTERN, message = "deviceClass must be mobile/tablet/desktop")
          String deviceClass,
      @Pattern(regexp = OS_PATTERN, message = "os must be ios/android/windows/macos/linux")
          String os) {}

  public record UpdateRequest(
      @Size(max = 2048) String url,
      @Min(1) @Max(100) Integer weight,
      @Size(max = 40) String label,
      Boolean enabled,
      @Pattern(
              regexp = "^([A-Za-z]{2})?$",
              message = "countryCode must be ISO-3166 alpha-2 or empty")
          String countryCode,
      @Pattern(regexp = DEVICE_PATTERN) String deviceClass,
      @Pattern(regexp = OS_PATTERN) String os) {}

  public record BlockedCountriesRequest(@Size(max = 255) String codes) {}

  public record BlockedCountriesResponse(String codes) {}
}
