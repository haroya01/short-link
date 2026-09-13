package com.example.short_link.post.collection.application.read;

import com.example.short_link.post.collection.domain.CollectionConnectionEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.note.domain.NoteEntity;
import com.example.short_link.user.domain.UserEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record CollectionContentSnapshot(
    Map<Long, PostEntity> posts,
    Map<Long, PostHighlightEntity> highlights,
    Map<Long, NoteEntity> notes,
    Map<Long, UserEntity> authors) {

  private static final int PREVIEW_LABEL_MAX = 40;

  Map<Long, List<String>> previewLabels(
      Map<Long, List<CollectionConnectionEntity>> selectedByCollection) {
    Map<Long, List<String>> result = new LinkedHashMap<>();
    selectedByCollection.forEach(
        (collectionId, connections) -> {
          List<String> labels = new ArrayList<>();
          for (var connection : connections) {
            String label = previewLabel(connection);
            if (label != null) labels.add(label);
          }
          result.put(collectionId, labels);
        });
    return result;
  }

  List<ConnectionView> connectionViews(List<CollectionConnectionEntity> connections) {
    List<ConnectionView> views = new ArrayList<>();
    for (var connection : connections) {
      ConnectionView view =
          switch (connection.getBlockType()) {
            case POST -> postCard(connection);
            case HIGHLIGHT -> highlightCard(connection);
            case NOTE -> noteCard(connection);
          };
      if (view != null) views.add(view);
    }
    return views;
  }

  private ConnectionView postCard(CollectionConnectionEntity connection) {
    PostEntity post = posts.get(connection.getRefId());
    if (post == null) return null;
    return ConnectionView.post(
        connection.getId(),
        connection.getWhy(),
        connection.getCreatedAt(),
        post.getTitle(),
        post.getExcerpt(),
        post.getSlug(),
        authorUsername(post));
  }

  private ConnectionView highlightCard(CollectionConnectionEntity connection) {
    PostHighlightEntity highlight = highlights.get(connection.getRefId());
    if (highlight == null) return null;
    PostEntity parent = posts.get(highlight.getPostId());
    if (parent == null) return null;
    return ConnectionView.highlight(
        connection.getId(),
        connection.getWhy(),
        connection.getCreatedAt(),
        highlight.getQuote(),
        parent.getTitle(),
        parent.getSlug(),
        authorUsername(parent));
  }

  private ConnectionView noteCard(CollectionConnectionEntity connection) {
    NoteEntity note = notes.get(connection.getRefId());
    if (note == null) return null;
    return ConnectionView.note(
        connection.getId(), connection.getWhy(), connection.getCreatedAt(), note.getBody());
  }

  private String authorUsername(PostEntity post) {
    UserEntity author = authors.get(post.getUserId());
    if (author == null) return null;
    return author.getUsername();
  }

  private String previewLabel(CollectionConnectionEntity connection) {
    return switch (connection.getBlockType()) {
      case POST -> {
        PostEntity post = posts.get(connection.getRefId());
        if (post == null) yield null;
        yield shortenLabel(post.getTitle());
      }
      case HIGHLIGHT -> {
        PostHighlightEntity highlight = highlights.get(connection.getRefId());
        if (highlight == null) yield null;
        yield shortenLabel(highlight.getQuote());
      }
      case NOTE -> {
        NoteEntity note = notes.get(connection.getRefId());
        if (note == null) yield null;
        yield shortenLabel(note.getBody());
      }
    };
  }

  private static String shortenLabel(String text) {
    String trimmed = text.strip();
    if (trimmed.length() > PREVIEW_LABEL_MAX) {
      return trimmed.substring(0, PREVIEW_LABEL_MAX) + "…";
    }
    return trimmed;
  }
}
