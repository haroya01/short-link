package com.example.short_link.link.presentation;

import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.application.dto.MyLink;
import com.example.short_link.link.application.write.BulkDeleteLinksCommand;
import com.example.short_link.link.application.write.BulkDeleteLinksUseCase;
import com.example.short_link.link.application.write.DeleteLinkCommand;
import com.example.short_link.link.application.write.DeleteLinkUseCase;
import com.example.short_link.link.application.write.UpdateLinkCommand;
import com.example.short_link.link.application.write.UpdateLinkUseCase;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.presentation.request.BulkDeleteRequest;
import com.example.short_link.link.presentation.request.UpdateLinkRequest;
import com.example.short_link.link.presentation.response.BulkDeleteResponse;
import com.example.short_link.link.presentation.response.MyLinkResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
@RequiredArgsConstructor
public class LinkManagementController {

  private final UpdateLinkUseCase updateLink;
  private final DeleteLinkUseCase deleteLink;
  private final BulkDeleteLinksUseCase bulkDeleteLinks;
  private final ShortLinkUrlBuilder urlBuilder;

  @PatchMapping("/{shortCode}")
  public MyLinkResponse update(
      @AuthenticationPrincipal Long userId,
      @PathVariable ShortCode shortCode,
      @Valid @RequestBody UpdateLinkRequest request) {
    MyLink updated =
        updateLink.execute(
            new UpdateLinkCommand(
                userId,
                shortCode,
                request.originalUrl(),
                request.expiresAt(),
                request.note(),
                request.expiredMessage()));
    return MyLinkResponse.from(updated, urlBuilder);
  }

  @DeleteMapping("/{shortCode}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Long userId, @PathVariable ShortCode shortCode) {
    deleteLink.execute(new DeleteLinkCommand(userId, shortCode));
  }

  @DeleteMapping
  public BulkDeleteResponse bulkDelete(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody BulkDeleteRequest request) {
    int removed = bulkDeleteLinks.execute(BulkDeleteLinksCommand.of(userId, request.shortCodes()));
    return new BulkDeleteResponse(removed, request.shortCodes().size() - removed);
  }
}
