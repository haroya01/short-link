package com.example.short_link.cta.presentation;

import com.example.short_link.cta.application.read.CtaQueryService;
import com.example.short_link.cta.application.read.CtaView;
import com.example.short_link.cta.application.write.CreateCtaCommand;
import com.example.short_link.cta.application.write.CreateCtaUseCase;
import com.example.short_link.cta.application.write.DeleteCtaCommand;
import com.example.short_link.cta.application.write.DeleteCtaUseCase;
import com.example.short_link.cta.application.write.UpdateCtaCommand;
import com.example.short_link.cta.application.write.UpdateCtaUseCase;
import com.example.short_link.cta.domain.CtaPurpose;
import com.example.short_link.cta.domain.CtaStyle;
import com.example.short_link.cta.presentation.request.CreateCtaRequest;
import com.example.short_link.cta.presentation.request.UpdateCtaRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ctas")
@RequiredArgsConstructor
public class CtaController {

  private final CreateCtaUseCase createCta;
  private final UpdateCtaUseCase updateCta;
  private final DeleteCtaUseCase deleteCta;
  private final CtaQueryService ctaQueryService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CtaView create(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CreateCtaRequest request) {
    return CtaView.from(
        createCta.execute(
            new CreateCtaCommand(
                userId,
                request.label(),
                request.url(),
                parseStyle(request.style()),
                parsePurpose(request.purpose()))));
  }

  @GetMapping
  public List<CtaView> listMine(@AuthenticationPrincipal Long userId) {
    return ctaQueryService.listMyCtas(userId);
  }

  @GetMapping("/{id}")
  public CtaView find(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return ctaQueryService.findOwnCta(userId, id);
  }

  @PatchMapping("/{id}")
  public CtaView update(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @Valid @RequestBody UpdateCtaRequest request) {
    return CtaView.from(
        updateCta.execute(
            new UpdateCtaCommand(
                userId,
                id,
                request.label(),
                request.url(),
                parseStyle(request.style()),
                parsePurpose(request.purpose()))));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    deleteCta.execute(new DeleteCtaCommand(userId, id));
  }

  private static CtaStyle parseStyle(String s) {
    return s == null || s.isBlank() ? null : CtaStyle.valueOf(s.toUpperCase());
  }

  private static CtaPurpose parsePurpose(String s) {
    return s == null || s.isBlank() ? null : CtaPurpose.valueOf(s.toUpperCase());
  }
}
