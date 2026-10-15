package com.example.short_link.federation.presentation;

import com.example.short_link.federation.application.RemoteAccountView;
import com.example.short_link.federation.application.RemoteBlocks;
import com.example.short_link.federation.application.RemoteMutes;
import com.example.short_link.federation.presentation.request.RemoteMuteRequest;
import com.example.short_link.federation.presentation.response.RemoteAccountListResponse;
import com.example.short_link.federation.presentation.response.RemoteMuteListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/federation")
@RequiredArgsConstructor
public class RemoteBlockController {

  private static final int MAX_PAGE_SIZE = 50;

  private final RemoteBlocks blocks;
  private final RemoteMutes mutes;

  @PutMapping("/accounts/{id}/block")
  public RemoteAccountView block(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return blocks.block(userId, id);
  }

  @DeleteMapping("/accounts/{id}/block")
  public RemoteAccountView unblock(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return blocks.unblock(userId, id);
  }

  @GetMapping("/blocks")
  public RemoteAccountListResponse blocks(
      @AuthenticationPrincipal Long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return new RemoteAccountListResponse(blocks.blocks(userId, page(page), size(size)));
  }

  @PutMapping("/accounts/{id}/mute")
  public RemoteAccountView mute(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @RequestBody(required = false) RemoteMuteRequest request) {
    return mutes.mute(
        userId,
        id,
        request == null ? null : request.notifications(),
        request == null ? null : request.duration());
  }

  @DeleteMapping("/accounts/{id}/mute")
  public RemoteAccountView unmute(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return mutes.unmute(userId, id);
  }

  @GetMapping("/mutes")
  public RemoteMuteListResponse mutes(
      @AuthenticationPrincipal Long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return new RemoteMuteListResponse(mutes.mutes(userId, page(page), size(size)));
  }

  private static int page(int page) {
    return Math.max(page, 0);
  }

  private static int size(int size) {
    return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
  }
}
