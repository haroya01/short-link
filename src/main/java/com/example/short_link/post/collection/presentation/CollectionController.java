package com.example.short_link.post.collection.presentation;

import com.example.short_link.post.collection.application.read.CollectionDetailView;
import com.example.short_link.post.collection.application.read.CollectionQueryService;
import com.example.short_link.post.collection.application.read.CollectionSummaryView;
import com.example.short_link.post.collection.application.write.CollectionCommandService;
import com.example.short_link.post.collection.application.write.ConnectBlockCommand;
import com.example.short_link.post.collection.application.write.CreateCollectionCommand;
import com.example.short_link.post.collection.application.write.EditCollectionCommand;
import com.example.short_link.post.collection.domain.CollectionEntity;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import com.example.short_link.post.collection.presentation.request.ConnectBlockRequest;
import com.example.short_link.post.collection.presentation.request.CreateCollectionRequest;
import com.example.short_link.post.collection.presentation.request.EditCollectionRequest;
import com.example.short_link.post.collection.presentation.request.ReorderConnectionsRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
                userId,
                request.title(),
                request.description(),
                request.visibility(),
                request.kind()));
    return CollectionSummaryView.afterCreation(saved);
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
    return queryService.editedSummary(saved);
  }

  @GetMapping("/users/me/collections")
  public List<CollectionSummaryView> myCollections(
      @AuthenticationPrincipal Long userId,
      @RequestParam(required = false) ConnectionBlockType blockType,
      @RequestParam(required = false) Long refId) {
    return queryService.listMine(userId, blockType, refId);
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

  @PutMapping("/collections/{id}/connections/order")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reorderConnections(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @Valid @RequestBody ReorderConnectionsRequest request) {
    commandService.reorder(userId, id, request.connectionIds());
  }

  @DeleteMapping("/collections/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    commandService.deleteCollection(userId, id);
  }
}
