package com.example.short_link.post.collection.application.read;

import com.example.short_link.common.note.NoteBodyReader;
import com.example.short_link.post.collection.domain.CollectionConnectionEntity;
import com.example.short_link.post.collection.domain.CollectionEntity;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import com.example.short_link.post.collection.domain.repository.CollectionConnectionRepository;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CollectionContentReader {
  static final int PREVIEW_PER_COLLECTION = 2;
  private final CollectionConnectionRepository connectionRepository;
  private final PostRepository postRepository;
  private final PostHighlightRepository highlightRepository;
  private final NoteBodyReader noteBodies;
  private final UserRepository userRepository;

  public Map<Long, List<String>> previewByCollection(
      List<CollectionEntity> collections, boolean publishedOnly) {
    if (collections.isEmpty()) return Map.of();

    var selected = previewConnections(collections);
    var connections = selected.values().stream().flatMap(List::stream).toList();
    var content = loadPreviewContent(connections, publishedOnly);
    return content.previewLabels(selected);
  }

  public List<ConnectionView> connections(List<CollectionConnectionEntity> connections) {
    return loadPublishedContent(connections).connectionViews(connections);
  }

  // 순서 있는 컬렉션은 처음 두 단계를, 나머지는 최근 두 연결을 고른다.
  private Map<Long, List<CollectionConnectionEntity>> previewConnections(
      List<CollectionEntity> collections) {
    Set<Long> ordered =
        collections.stream()
            .filter(CollectionEntity::isOrdered)
            .map(CollectionEntity::getId)
            .collect(Collectors.toSet());
    Map<Long, List<CollectionConnectionEntity>> selected = new LinkedHashMap<>();
    for (var connection :
        connectionRepository.findAllByCollectionIdInOrderByPositionDesc(
            collections.stream().map(CollectionEntity::getId).toList())) {
      var picked = selected.computeIfAbsent(connection.getCollectionId(), id -> new ArrayList<>());
      if (ordered.contains(connection.getCollectionId())) {
        picked.addFirst(connection);
        if (picked.size() > PREVIEW_PER_COLLECTION) picked.removeLast();
      } else if (picked.size() < PREVIEW_PER_COLLECTION) {
        picked.add(connection);
      }
    }
    // 먼저 두 연결을 고른 뒤 공개 범위를 적용한다. 제외된 항목을 더 오래된 연결로 채우지 않는다.
    return selected;
  }

  private CollectionContentSnapshot loadPreviewContent(
      List<CollectionConnectionEntity> connections, boolean publishedOnly) {
    var candidates = postRepository.findAllByIdIn(refIds(connections, ConnectionBlockType.POST));
    if (publishedOnly) candidates = candidates.stream().filter(PostEntity::isPublished).toList();
    var posts = indexedBy(candidates, PostEntity::getId);
    var highlights =
        indexedBy(
            highlightRepository.findAllByIdIn(refIds(connections, ConnectionBlockType.HIGHLIGHT)),
            PostHighlightEntity::getId);
    if (publishedOnly && !highlights.isEmpty()) keepHighlightsOfPublishedPosts(highlights);
    var notes = noteBodies.blocksByIds(refIds(connections, ConnectionBlockType.NOTE));
    return new CollectionContentSnapshot(posts, highlights, notes, Map.of());
  }

  private void keepHighlightsOfPublishedPosts(Map<Long, PostHighlightEntity> highlights) {
    Set<Long> parentIds =
        highlights.values().stream()
            .map(PostHighlightEntity::getPostId)
            .collect(Collectors.toSet());
    Set<Long> publishedParents =
        postRepository.findAllByIdIn(parentIds).stream()
            .filter(PostEntity::isPublished)
            .map(PostEntity::getId)
            .collect(Collectors.toSet());
    highlights.values().removeIf(highlight -> !publishedParents.contains(highlight.getPostId()));
  }

  private CollectionContentSnapshot loadPublishedContent(
      List<CollectionConnectionEntity> connections) {
    var highlights =
        indexedBy(
            highlightRepository.findAllByIdIn(refIds(connections, ConnectionBlockType.HIGHLIGHT)),
            PostHighlightEntity::getId);
    var notes = noteBodies.blocksByIds(refIds(connections, ConnectionBlockType.NOTE));

    Set<Long> postIds = new HashSet<>(refIds(connections, ConnectionBlockType.POST));
    highlights.values().forEach(highlight -> postIds.add(highlight.getPostId()));
    var posts =
        indexedBy(
            postRepository.findAllByIdIn(postIds).stream().filter(PostEntity::isPublished).toList(),
            PostEntity::getId);
    Set<Long> authorIds =
        posts.values().stream().map(PostEntity::getUserId).collect(Collectors.toSet());
    var authors = indexedBy(userRepository.findAllByIdIn(authorIds), UserEntity::getId);
    return new CollectionContentSnapshot(posts, highlights, notes, authors);
  }

  private static List<Long> refIds(
      List<CollectionConnectionEntity> connections, ConnectionBlockType type) {
    return connections.stream()
        .filter(connection -> connection.getBlockType() == type)
        .map(CollectionConnectionEntity::getRefId)
        .distinct()
        .toList();
  }

  private static <T> Map<Long, T> indexedBy(List<T> items, Function<T, Long> key) {
    return items.stream().collect(Collectors.toMap(key, Function.identity()));
  }
}
