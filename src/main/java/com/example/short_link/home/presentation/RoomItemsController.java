package com.example.short_link.home.presentation;

import com.example.short_link.home.application.read.RoomItemsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RoomItemsController {

  private final RoomItemsQueryService items;

  @GetMapping("/api/v1/rooms/{id}/items")
  public RoomItemsQueryService.Page room(
      @AuthenticationPrincipal Long viewerId,
      @PathVariable Long id,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String cursor) {
    return items.room(id, viewerId, cursor, limit);
  }

  @GetMapping("/api/v1/rooms/items")
  public RoomItemsQueryService.Page rooms(
      @AuthenticationPrincipal Long viewerId,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String cursor) {
    return items.rooms(viewerId, cursor, limit);
  }
}
