package com.example.short_link.note.presentation;

import com.example.short_link.note.application.read.NoteFeedView;
import com.example.short_link.note.application.read.NoteHistoryView;
import com.example.short_link.note.application.read.NoteQueryService;
import com.example.short_link.note.application.read.NoteThreadView;
import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.note.application.read.PostQuotesView;
import com.example.short_link.note.application.read.ProfileMediaView;
import com.example.short_link.note.application.read.ProfileRepliesView;
import com.example.short_link.note.application.read.TrendingLinkView;
import com.example.short_link.note.application.read.TrendingTagView;
import com.example.short_link.note.application.write.NoteCommandService;
import com.example.short_link.note.application.write.NoteFeedSettingsService;
import com.example.short_link.note.application.write.NoteImages;
import com.example.short_link.note.presentation.request.CreateNoteRequest;
import com.example.short_link.note.presentation.request.CreateNoteThreadRequest;
import com.example.short_link.note.presentation.request.EditNoteRequest;
import com.example.short_link.note.presentation.request.NoteFeedPreferencesRequest;
import com.example.short_link.note.presentation.request.NoteImagePresignRequest;
import com.example.short_link.note.presentation.request.ReplyPolicyRequest;
import com.example.short_link.note.presentation.response.LikedIdsResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NoteController {

  private final NoteQueryService query;
  private final NoteCommandService command;
  private final NoteImages images;
  private final NoteFeedSettingsService feedSettings;

  @GetMapping("/api/v1/public/notes")
  public NoteFeedView everyone(
      @AuthenticationPrincipal Long viewerId,
      @RequestParam(defaultValue = "recent") String sort,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return "trending".equalsIgnoreCase(sort)
        ? query.trending(page, size, viewerId)
        : query.everyone(page, size, viewerId);
  }

  @GetMapping("/api/v1/public/notes/trending-tags")
  public List<TrendingTagView> trendingTags() {
    return query.trendingTags();
  }

  @GetMapping("/api/v1/public/notes/trending-links")
  public List<TrendingLinkView> trendingLinks() {
    return query.trendingLinks();
  }

  @GetMapping("/api/v1/public/notes/links")
  public NoteFeedView linked(
      @AuthenticationPrincipal Long viewerId,
      @RequestParam(defaultValue = "") String url,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return query.linked(url, page, size, viewerId);
  }

  @GetMapping("/api/v1/public/notes/tags/{tag}")
  public NoteFeedView tagged(
      @AuthenticationPrincipal Long viewerId,
      @PathVariable String tag,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return query.tagged(tag, page, size, viewerId);
  }

  @GetMapping("/api/v1/public/notes/search")
  public NoteFeedView search(
      @AuthenticationPrincipal Long viewerId,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return query.search(q, page, size, viewerId);
  }

  @GetMapping("/api/v1/federation/accounts/{id}/notes")
  public NoteFeedView remoteAccountNotes(
      @AuthenticationPrincipal Long viewerId,
      @PathVariable Long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return query.byRemoteActor(id, page, size, viewerId);
  }

  @GetMapping("/api/v1/public/notes/{id}/history")
  public NoteHistoryView history(@AuthenticationPrincipal Long viewerId, @PathVariable Long id) {
    return query.history(id, viewerId);
  }

  @GetMapping("/api/v1/notes/direct")
  public NoteFeedView direct(
      @AuthenticationPrincipal Long viewerId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return query.direct(viewerId, page, size);
  }

  @GetMapping("/api/v1/public/notes/{id}")
  public NoteThreadView thread(@AuthenticationPrincipal Long viewerId, @PathVariable Long id) {
    return query.thread(id, viewerId);
  }

  @GetMapping("/api/v1/public/notes/{id}/hidden-replies")
  public List<NoteView> hiddenReplies(
      @AuthenticationPrincipal Long viewerId, @PathVariable Long id) {
    return query.hiddenReplies(id, viewerId);
  }

  @GetMapping("/api/v1/public/profiles/{username}/notes")
  public NoteFeedView byAuthor(
      @AuthenticationPrincipal Long viewerId,
      @PathVariable String username,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return query.byAuthor(username, page, size, viewerId);
  }

  @GetMapping("/api/v1/public/profiles/{username}/replies")
  public ProfileRepliesView replies(
      @AuthenticationPrincipal Long viewerId,
      @PathVariable String username,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return query.repliesByAuthor(username, page, size, viewerId);
  }

  @GetMapping("/api/v1/public/profiles/{username}/media")
  public ProfileMediaView media(
      @AuthenticationPrincipal Long viewerId,
      @PathVariable String username,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return query.mediaByAuthor(username, page, size, viewerId);
  }

  @GetMapping("/api/v1/public/profiles/{username}/reposts")
  public NoteFeedView reposts(
      @AuthenticationPrincipal Long viewerId,
      @PathVariable String username,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return query.reposts(username, page, size, viewerId);
  }

  @GetMapping("/api/v1/notes/following")
  public NoteFeedView following(
      @AuthenticationPrincipal Long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return query.following(userId, page, size);
  }

  @GetMapping("/api/v1/notes/federated")
  public NoteFeedView federated(
      @AuthenticationPrincipal Long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return query.federated(userId, page, size);
  }

  @PostMapping("/api/v1/notes")
  @ResponseStatus(HttpStatus.CREATED)
  public NoteView create(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CreateNoteRequest request) {
    return command.create(userId, request.toDraft());
  }

  @PostMapping("/api/v1/notes/threads")
  @ResponseStatus(HttpStatus.CREATED)
  public List<NoteView> createThread(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CreateNoteThreadRequest request) {
    return command.createThread(userId, request.toDrafts());
  }

  @PatchMapping("/api/v1/notes/{id}")
  public NoteView edit(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @RequestBody EditNoteRequest request) {
    return command.edit(userId, id, request.body(), request.contentWarning(), request.sensitive());
  }

  @DeleteMapping("/api/v1/notes/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    command.delete(userId, id);
  }

  @PutMapping("/api/v1/notes/{id}/like")
  public NoteCommandService.LikeStatus like(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return command.setLike(userId, id, true);
  }

  @DeleteMapping("/api/v1/notes/{id}/like")
  public NoteCommandService.LikeStatus unlike(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return command.setLike(userId, id, false);
  }

  @PutMapping("/api/v1/notes/{id}/conversation-mute")
  public NoteCommandService.ConversationMuteStatus muteConversation(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return command.setConversationMuted(userId, id, true);
  }

  @DeleteMapping("/api/v1/notes/{id}/conversation-mute")
  public NoteCommandService.ConversationMuteStatus unmuteConversation(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return command.setConversationMuted(userId, id, false);
  }

  @PutMapping("/api/v1/notes/{id}/repost")
  public NoteCommandService.RepostStatus repost(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return command.setRepost(userId, id, true);
  }

  @DeleteMapping("/api/v1/notes/{id}/repost")
  public NoteCommandService.RepostStatus unrepost(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return command.setRepost(userId, id, false);
  }

  @PutMapping("/api/v1/notes/{id}/bookmark")
  public NoteCommandService.BookmarkStatus bookmark(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return command.setBookmark(userId, id, true);
  }

  @DeleteMapping("/api/v1/notes/{id}/bookmark")
  public NoteCommandService.BookmarkStatus unbookmark(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return command.setBookmark(userId, id, false);
  }

  @GetMapping("/api/v1/notes/bookmarks")
  public NoteFeedView bookmarks(
      @AuthenticationPrincipal Long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return query.bookmarks(userId, page, size);
  }

  @GetMapping("/api/v1/public/posts/{id}/quotes")
  public PostQuotesView postQuotes(
      @AuthenticationPrincipal Long viewerId,
      @PathVariable Long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return query.postQuotes(id, page, size, viewerId);
  }

  @GetMapping("/api/v1/public/notes/{id}/quotes")
  public NoteFeedView quotes(
      @AuthenticationPrincipal Long viewerId,
      @PathVariable Long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return query.quotes(id, page, size, viewerId);
  }

  @PutMapping("/api/v1/notes/{id}/pin")
  public NoteCommandService.PinStatus pin(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return command.setPin(userId, id, true);
  }

  @DeleteMapping("/api/v1/notes/{id}/pin")
  public NoteCommandService.PinStatus unpin(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return command.setPin(userId, id, false);
  }

  @PutMapping("/api/v1/notes/{id}/hidden")
  public NoteCommandService.ReplyHiddenStatus hideReply(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return command.setReplyHidden(userId, id, true);
  }

  @DeleteMapping("/api/v1/notes/{id}/hidden")
  public NoteCommandService.ReplyHiddenStatus showReply(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return command.setReplyHidden(userId, id, false);
  }

  @PutMapping("/api/v1/notes/{id}/reply-policy")
  public NoteCommandService.ReplyPolicyStatus setReplyPolicy(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @RequestBody ReplyPolicyRequest request) {
    return command.setReplyPolicy(userId, id, request.replyPolicy());
  }

  @GetMapping("/api/v1/notes/feed-preferences")
  public NoteFeedSettingsService.FeedPreferences feedPreferences(
      @AuthenticationPrincipal Long userId) {
    return feedSettings.preferences(userId);
  }

  @PutMapping("/api/v1/notes/feed-preferences")
  public NoteFeedSettingsService.FeedPreferences updateFeedPreferences(
      @AuthenticationPrincipal Long userId, @RequestBody NoteFeedPreferencesRequest request) {
    return feedSettings.update(userId, request.showReposts(), request.languages());
  }

  @GetMapping("/api/v1/notes/repost-visibility/{username}")
  public NoteFeedSettingsService.RepostVisibility repostVisibility(
      @AuthenticationPrincipal Long userId, @PathVariable String username) {
    return feedSettings.repostsOf(userId, username);
  }

  @PutMapping("/api/v1/notes/repost-visibility/{username}")
  public NoteFeedSettingsService.RepostVisibility hideReposts(
      @AuthenticationPrincipal Long userId, @PathVariable String username) {
    return feedSettings.setRepostsHidden(userId, username, true);
  }

  @DeleteMapping("/api/v1/notes/repost-visibility/{username}")
  public NoteFeedSettingsService.RepostVisibility showReposts(
      @AuthenticationPrincipal Long userId, @PathVariable String username) {
    return feedSettings.setRepostsHidden(userId, username, false);
  }

  @GetMapping("/api/v1/notes/like-status")
  public LikedIdsResponse likeStatus(
      @AuthenticationPrincipal Long userId, @RequestParam List<Long> ids) {
    return new LikedIdsResponse(query.likedNoteIds(userId, ids));
  }

  @PostMapping("/api/v1/notes/images/presign")
  public NoteImages.PresignedImage presignImage(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody NoteImagePresignRequest request) {
    return images.presign(userId, request.contentType());
  }
}
