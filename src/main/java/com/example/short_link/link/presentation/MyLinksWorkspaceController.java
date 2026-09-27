package com.example.short_link.link.presentation;

import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.application.read.MyLinksWorkspaceQueryService;
import com.example.short_link.link.application.write.LinkFavoritesService;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.presentation.request.FavoriteOrderRequest;
import com.example.short_link.link.presentation.response.MyLinksOverviewResponse;
import com.example.short_link.link.presentation.response.MyLinksPage;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links/me")
@RequiredArgsConstructor
public class MyLinksWorkspaceController {
  private final MyLinksWorkspaceQueryService query;
  private final LinkFavoritesService favorites;
  private final ShortLinkUrlBuilder urls;

  @GetMapping("/favorites")
  public MyLinksPage favorites(@AuthenticationPrincipal Long userId) {
    return MyLinksPage.from(query.favorites(userId), urls);
  }

  @PutMapping("/favorites/{code}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void add(@AuthenticationPrincipal Long userId, @PathVariable ShortCode code) {
    favorites.add(userId, code);
  }

  @DeleteMapping("/favorites/{code}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(@AuthenticationPrincipal Long userId, @PathVariable ShortCode code) {
    favorites.remove(userId, code);
  }

  @PutMapping("/favorites/order")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reorder(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody FavoriteOrderRequest request) {
    favorites.reorder(userId, request.shortCodes());
  }

  @GetMapping("/by-codes")
  public MyLinksPage byCodes(
      @AuthenticationPrincipal Long userId, @RequestParam List<ShortCode> codes) {
    return MyLinksPage.from(query.byCodes(userId, codes), urls);
  }

  @GetMapping("/overview")
  public MyLinksOverviewResponse overview(@AuthenticationPrincipal Long userId) {
    return MyLinksOverviewResponse.from(query.overview(userId), urls);
  }
}
