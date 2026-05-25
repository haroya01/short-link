package com.example.short_link.tag.presentation;

import com.example.short_link.link.presentation.request.TagRequest;
import com.example.short_link.tag.application.TagService;
import com.example.short_link.tag.application.TagService.TagSummary;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class TagController {

  private final TagService service;

  @GetMapping
  public List<TagSummary> list(@AuthenticationPrincipal Long userId) {
    return service.list(userId);
  }

  @PostMapping
  public ResponseEntity<TagSummary> create(
      @AuthenticationPrincipal Long userId, @RequestBody TagRequest request) {
    TagSummary created = service.create(userId, request.name(), request.color());
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PatchMapping("/{id}")
  public TagSummary update(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @RequestBody TagRequest request) {
    return service.update(userId, id, request.name(), request.color());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    service.delete(userId, id);
    return ResponseEntity.noContent().build();
  }
}
