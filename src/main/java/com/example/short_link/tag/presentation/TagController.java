package com.example.short_link.tag.presentation;

import com.example.short_link.tag.application.dto.TagSummary;
import com.example.short_link.tag.application.read.TagQueryService;
import com.example.short_link.tag.application.write.CreateTagUseCase;
import com.example.short_link.tag.application.write.DeleteTagUseCase;
import com.example.short_link.tag.application.write.UpdateTagUseCase;
import com.example.short_link.tag.presentation.request.TagRequest;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class TagController {

  private final TagQueryService tagQueryService;
  private final CreateTagUseCase createTag;
  private final UpdateTagUseCase updateTag;
  private final DeleteTagUseCase deleteTag;

  @GetMapping
  public List<TagSummary> list(@AuthenticationPrincipal Long userId) {
    return tagQueryService.list(userId);
  }

  @PostMapping
  public ResponseEntity<TagSummary> create(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody TagRequest request) {
    TagSummary created = createTag.execute(userId, request.name(), request.color());
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PatchMapping("/{id}")
  public TagSummary update(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @Valid @RequestBody TagRequest request) {
    return updateTag.execute(userId, id, request.name(), request.color());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    deleteTag.execute(userId, id);
    return ResponseEntity.noContent().build();
  }
}
