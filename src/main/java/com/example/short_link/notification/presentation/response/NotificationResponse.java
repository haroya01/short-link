package com.example.short_link.notification.presentation.response;

import com.example.short_link.common.federation.CustomEmoji;
import com.example.short_link.notification.application.dto.NotificationCollectionRef;
import com.example.short_link.notification.application.dto.NotificationNoteRef;
import com.example.short_link.notification.application.dto.NotificationPostRef;
import com.example.short_link.notification.application.dto.NotificationSeriesRef;
import com.example.short_link.notification.application.dto.NotificationView;
import com.example.short_link.notification.domain.NotificationActor;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

public record NotificationResponse(
    Long id,
    String type,
    Long actorId,
    String actorUsername,
    String actorAvatarUrl,
    Long postId,
    String postSlug,
    String postTitle,
    String postAuthorUsername,
    Long commentId,
    Long highlightId,
    Long seriesId,
    String seriesSlug,
    String seriesTitle,
    Long collectionId,
    String collectionName,
    boolean read,
    Instant createdAt,
    String actorProfileUrl,
    Long noteId,
    String noteExcerpt,
    Long sourceNoteId,
    String sourceExcerpt,
    long count,
    List<Actor> actors,
    Long actorRemoteId,
    Integer milestone,
    String actorDisplayName,
    List<CustomEmoji> actorEmojis) {

  public record Actor(
      Long id,
      String username,
      String avatarUrl,
      String profileUrl,
      String displayName,
      List<CustomEmoji> emojis) {

    static Actor of(NotificationActor actor) {
      return new Actor(
          actor.userId(),
          actor.username(),
          actor.avatarUrl(),
          actor.profileUrl(),
          actor.displayName(),
          actor.emojis());
    }
  }

  public static NotificationResponse from(NotificationView view) {
    NotificationActor actor = view.actor();
    NotificationPostRef post = view.post();
    NotificationSeriesRef series = view.series();
    NotificationCollectionRef collection = view.collection();
    NotificationNoteRef note = view.note();
    return new NotificationResponse(
        view.id(),
        view.type().name(),
        part(actor, NotificationActor::userId),
        part(actor, NotificationActor::username),
        part(actor, NotificationActor::avatarUrl),
        postId(view),
        part(post, NotificationPostRef::slug),
        part(post, NotificationPostRef::title),
        part(post, NotificationPostRef::authorUsername),
        part(post, NotificationPostRef::commentId),
        part(post, NotificationPostRef::highlightId),
        part(series, NotificationSeriesRef::seriesId),
        part(series, NotificationSeriesRef::slug),
        part(series, NotificationSeriesRef::title),
        part(collection, NotificationCollectionRef::collectionId),
        part(collection, NotificationCollectionRef::collectionName),
        view.read(),
        view.createdAt(),
        part(actor, NotificationActor::profileUrl),
        part(note, NotificationNoteRef::noteId),
        part(note, NotificationNoteRef::excerpt),
        part(note, NotificationNoteRef::sourceNoteId),
        part(note, NotificationNoteRef::sourceExcerpt),
        view.count(),
        view.actors().stream().map(Actor::of).toList(),
        part(actor, NotificationActor::remoteId),
        part(post, NotificationPostRef::milestone),
        part(actor, NotificationActor::displayName),
        actor == null ? List.of() : actor.emojis());
  }

  private static <T, R> R part(T owner, Function<T, R> read) {
    return owner == null ? null : read.apply(owner);
  }

  private static Long postId(NotificationView view) {
    if (view.post() != null) {
      return view.post().postId();
    }
    return view.collection() == null ? null : view.collection().postId();
  }
}
