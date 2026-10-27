package com.example.short_link.post.collection.application.read;

import com.example.short_link.common.note.NoteBodyReader;
import com.example.short_link.post.application.access.PostAccessPolicy;
import com.example.short_link.post.collection.application.CollectionTargets;
import com.example.short_link.post.collection.domain.CollectionConnectionEntity;
import com.example.short_link.post.collection.domain.CollectionEntity;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import com.example.short_link.post.collection.domain.repository.CollectionConnectionRepository;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.application.read.UserSummary;
import com.example.short_link.user.application.read.UserSummaryReader;
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
  private final UserSummaryReader users;

  public Map<Long, List<String>> previewByCollection(
      List<CollectionEntity> collections, Long viewerId) {
    if (collections.isEmpty()) return Map.of();

    var selected = previewConnections(collections);
    var connections = selected.values().stream().flatMap(List::stream).toList();
    var content = loadPreviewContent(connections, viewerId);
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
      List<CollectionConnectionEntity> connections, Long viewerId) {
    var openHighlights = openHighlights(connections);
    Set<Long> postIds = new HashSet<>(refIds(connections, ConnectionBlockType.POST));
    openHighlights.forEach(highlight -> postIds.add(highlight.getPostId()));
    var posts =
        indexedBy(
            postRepository.findAllByIdIn(postIds).stream()
                .filter(post -> CollectionTargets.shows(post, viewerId))
                .toList(),
            PostEntity::getId);
    var highlights =
        indexedBy(
            openHighlights.stream()
                .filter(highlight -> posts.containsKey(highlight.getPostId()))
                .toList(),
            PostHighlightEntity::getId);
    var notes = noteBodies.blocksByIds(refIds(connections, ConnectionBlockType.NOTE));
    return new CollectionContentSnapshot(posts, highlights, notes, Map.of());
  }

  private CollectionContentSnapshot loadPublishedContent(
      List<CollectionConnectionEntity> connections) {
    var highlights = indexedBy(openHighlights(connections), PostHighlightEntity::getId);
    var notes = noteBodies.blocksByIds(refIds(connections, ConnectionBlockType.NOTE));

    Set<Long> postIds = new HashSet<>(refIds(connections, ConnectionBlockType.POST));
    highlights.values().forEach(highlight -> postIds.add(highlight.getPostId()));
    var posts =
        indexedBy(
            postRepository.findAllByIdIn(postIds).stream()
                .filter(PostAccessPolicy::isOpen)
                .toList(),
            PostEntity::getId);
    Set<Long> authorIds =
        posts.values().stream().map(PostEntity::getUserId).collect(Collectors.toSet());
    var authors = indexedBy(users.findAll(authorIds), UserSummary::id);
    return new CollectionContentSnapshot(posts, highlights, notes, authors);
  }

  private List<PostHighlightEntity> openHighlights(List<CollectionConnectionEntity> connections) {
    return highlightRepository
        .findAllByIdIn(refIds(connections, ConnectionBlockType.HIGHLIGHT))
        .stream()
        .filter(PostAccessPolicy::saidOpenly)
        .toList();
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
