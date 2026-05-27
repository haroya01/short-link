package com.example.short_link.link.destination.presentation;

import com.example.short_link.link.destination.application.dto.DestinationSummary;
import com.example.short_link.link.destination.application.read.LinkDestinationQueryService;
import com.example.short_link.link.destination.application.write.AddDestinationUseCase;
import com.example.short_link.link.destination.application.write.DeleteDestinationUseCase;
import com.example.short_link.link.destination.application.write.SetBlockedCountriesUseCase;
import com.example.short_link.link.destination.application.write.UpdateDestinationUseCase;
import com.example.short_link.link.destination.presentation.request.LinkDestinationAddRequest;
import com.example.short_link.link.destination.presentation.request.LinkDestinationBlockedCountriesRequest;
import com.example.short_link.link.destination.presentation.request.LinkDestinationUpdateRequest;
import com.example.short_link.link.destination.presentation.response.LinkDestinationBlockedCountriesResponse;
import com.example.short_link.link.domain.ShortCode;
import jakarta.validation.Valid;
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

  private final LinkDestinationQueryService query;
  private final AddDestinationUseCase addUseCase;
  private final UpdateDestinationUseCase updateUseCase;
  private final DeleteDestinationUseCase deleteUseCase;
  private final SetBlockedCountriesUseCase setBlockedUseCase;

  @GetMapping("/{shortCode}/destinations")
  public List<DestinationSummary> list(
      @AuthenticationPrincipal Long userId, @PathVariable ShortCode shortCode) {
    return query.list(userId, shortCode);
  }

  @PostMapping("/{shortCode}/destinations")
  public ResponseEntity<DestinationSummary> add(
      @AuthenticationPrincipal Long userId,
      @PathVariable ShortCode shortCode,
      @Valid @RequestBody LinkDestinationAddRequest request) {
    DestinationSummary added =
        addUseCase.execute(
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
      @PathVariable ShortCode shortCode,
      @PathVariable Long id,
      @Valid @RequestBody LinkDestinationUpdateRequest request) {
    return updateUseCase.execute(
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
      @AuthenticationPrincipal Long userId,
      @PathVariable ShortCode shortCode,
      @PathVariable Long id) {
    deleteUseCase.execute(userId, shortCode, id);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{shortCode}/blocked-countries")
  public LinkDestinationBlockedCountriesResponse setBlocked(
      @AuthenticationPrincipal Long userId,
      @PathVariable ShortCode shortCode,
      @Valid @RequestBody LinkDestinationBlockedCountriesRequest request) {
    var link = setBlockedUseCase.execute(userId, shortCode, request.codes());
    return new LinkDestinationBlockedCountriesResponse(link.getBlockedCountries());
  }
}
