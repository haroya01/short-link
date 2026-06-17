package com.example.short_link.user.presentation;

import com.example.short_link.user.application.read.BlockQueryService;
import com.example.short_link.user.application.read.BlockedUserView;
import com.example.short_link.user.application.write.BlockUseCase;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Block / unblock a user (App Store 1.2 UGC). All endpoints require auth. PUT/DELETE return 204;
 * the GET list backs the "blocked users" management screen + the client-side content filter.
 */
@RestController
@RequiredArgsConstructor
public class BlockController {

  private final BlockUseCase blockUseCase;
  private final BlockQueryService blockQueryService;

  @PutMapping("/api/v1/users/{username}/block")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void block(@AuthenticationPrincipal Long userId, @PathVariable String username) {
    blockUseCase.block(userId, username);
  }

  @DeleteMapping("/api/v1/users/{username}/block")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unblock(@AuthenticationPrincipal Long userId, @PathVariable String username) {
    blockUseCase.unblock(userId, username);
  }

  @GetMapping("/api/v1/users/me/blocks")
  public List<BlockedUserView> myBlocks(@AuthenticationPrincipal Long userId) {
    return blockQueryService.myBlocks(userId);
  }
}
