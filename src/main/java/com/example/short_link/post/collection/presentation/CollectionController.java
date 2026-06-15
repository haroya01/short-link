package com.example.short_link.post.collection.presentation;

import com.example.short_link.post.collection.application.read.CollectionDetailView;
import com.example.short_link.post.collection.application.read.CollectionQueryService;
import com.example.short_link.post.collection.application.read.CollectionSummaryView;
import com.example.short_link.post.collection.application.write.CollectionCommandService;
import com.example.short_link.post.collection.application.write.ConnectBlockCommand;
import com.example.short_link.post.collection.application.write.CreateCollectionCommand;
import com.example.short_link.post.collection.application.write.EditCollectionCommand;
import com.example.short_link.post.collection.domain.CollectionEntity;
import com.example.short_link.post.collection.presentation.request.ConnectBlockRequest;
import com.example.short_link.post.collection.presentation.request.CreateCollectionRequest;
import com.example.short_link.post.collection.presentation.request.EditCollectionRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 컬렉션 표면 — 만들기·내 목록·상세·연결·연결끊기·삭제. "연결"이 §0의 핵심 동사라 글 인게이지와 같은 인증 컨텍스트에서 그 자리 연결을 받는다. 상세는 PRIVATE
 * 가시성을 쿼리 서비스가 가른다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CollectionController {

  private final CollectionCommandService commandService;
  private final CollectionQueryService queryService;

  @PostMapping("/collections")
  @ResponseStatus(HttpStatus.CREATED)
  public CollectionSummaryView create(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CreateCollectionRequest request) {
    CollectionEntity saved =
        commandService.create(
            new CreateCollectionCommand(
                userId, request.title(), request.description(), request.visibility()));
    return new CollectionSummaryView(
        saved.getId(),
        saved.getTitle(),
        saved.getDescription(),
        saved.getVisibility().name(),
        0,
        saved.getUpdatedAt(),
        List.of());
  }

  @PutMapping("/collections/{id}")
  public CollectionSummaryView edit(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @Valid @RequestBody EditCollectionRequest request) {
    CollectionEntity saved =
        commandService.edit(
            new EditCollectionCommand(
                userId, id, request.title(), request.description(), request.visibility()));
    return new CollectionSummaryView(
        saved.getId(),
        saved.getTitle(),
        saved.getDescription(),
        saved.getVisibility().name(),
        (int) queryService.connectionCount(saved.getId()),
        saved.getUpdatedAt(),
        List.of());
  }

  @GetMapping("/users/me/collections")
  public List<CollectionSummaryView> myCollections(@AuthenticationPrincipal Long userId) {
    return queryService.listMine(userId);
  }

  @GetMapping("/collections/{id}")
  public CollectionDetailView detail(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return queryService.detail(userId, id);
  }

  @PostMapping("/collections/{id}/connections")
  @ResponseStatus(HttpStatus.CREATED)
  public void connect(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @Valid @RequestBody ConnectBlockRequest request) {
    commandService.connect(
        new ConnectBlockCommand(userId, id, request.blockType(), request.refId(), request.why()));
  }

  @DeleteMapping("/collections/{id}/connections/{connectionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void disconnect(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @PathVariable Long connectionId) {
    commandService.disconnect(userId, id, connectionId);
  }

  @DeleteMapping("/collections/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    commandService.deleteCollection(userId, id);
  }
}
