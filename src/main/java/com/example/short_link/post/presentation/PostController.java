package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.PostBlockView;
import com.example.short_link.post.application.read.PostQueryService;
import com.example.short_link.post.application.read.PostRevisionView;
import com.example.short_link.post.application.read.PostView;
import com.example.short_link.post.application.write.BackToDraftPostCommand;
import com.example.short_link.post.application.write.BackToDraftPostUseCase;
import com.example.short_link.post.application.write.CreatePostCommand;
import com.example.short_link.post.application.write.CreatePostUseCase;
import com.example.short_link.post.application.write.DeletePostCommand;
import com.example.short_link.post.application.write.DeletePostUseCase;
import com.example.short_link.post.application.write.IssuePreviewTokenUseCase;
import com.example.short_link.post.application.write.PublishPostCommand;
import com.example.short_link.post.application.write.PublishPostUseCase;
import com.example.short_link.post.application.write.ReplacePostBlocksCommand;
import com.example.short_link.post.application.write.ReplacePostBlocksUseCase;
import com.example.short_link.post.application.write.RepublishPostCommand;
import com.example.short_link.post.application.write.RepublishPostUseCase;
import com.example.short_link.post.application.write.RestorePostRevisionCommand;
import com.example.short_link.post.application.write.RestorePostRevisionUseCase;
import com.example.short_link.post.application.write.SchedulePostCommand;
import com.example.short_link.post.application.write.SchedulePostUseCase;
import com.example.short_link.post.application.write.SetPinnedPostsUseCase;
import com.example.short_link.post.application.write.UnpublishPostCommand;
import com.example.short_link.post.application.write.UnpublishPostUseCase;
import com.example.short_link.post.application.write.UpdatePostMetadataCommand;
import com.example.short_link.post.application.write.UpdatePostMetadataUseCase;
import com.example.short_link.post.domain.PostBlockType;
import com.example.short_link.post.presentation.request.CreatePostRequest;
import com.example.short_link.post.presentation.request.ReplaceBlocksRequest;
import com.example.short_link.post.presentation.request.SchedulePostRequest;
import com.example.short_link.post.presentation.request.SetPinnedPostsRequest;
import com.example.short_link.post.presentation.request.UpdatePostRequest;
import com.example.short_link.post.presentation.response.PreviewTokenResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

  private final CreatePostUseCase createPost;
  private final UpdatePostMetadataUseCase updatePostMetadata;
  private final PublishPostUseCase publishPost;
  private final SchedulePostUseCase schedulePost;
  private final UnpublishPostUseCase unpublishPost;
  private final RepublishPostUseCase republishPost;
  private final BackToDraftPostUseCase backToDraftPost;
  private final ReplacePostBlocksUseCase replacePostBlocks;
  private final RestorePostRevisionUseCase restorePostRevision;
  private final DeletePostUseCase deletePost;
  private final SetPinnedPostsUseCase setPinnedPosts;
  private final IssuePreviewTokenUseCase issuePreviewToken;
  private final PostQueryService postQueryService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PostView create(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CreatePostRequest request) {
    return PostView.from(
        createPost.execute(
            new CreatePostCommand(userId, request.slug(), request.title(), request.languageTag())));
  }

  @PutMapping("/pins")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void setPins(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody SetPinnedPostsRequest request) {
    setPinnedPosts.execute(userId, request.postIds());
  }

  @GetMapping
  public List<PostView> listMine(@AuthenticationPrincipal Long userId) {
    return postQueryService.listMyPosts(userId);
  }

  @GetMapping("/{id}")
  public PostView find(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return postQueryService.findOwnPost(userId, id);
  }

  @PatchMapping("/{id}")
  public PostView updateMetadata(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @Valid @RequestBody UpdatePostRequest request) {
    return PostView.from(
        updatePostMetadata.execute(
            new UpdatePostMetadataCommand(
                userId,
                id,
                request.title(),
                request.slug(),
                request.excerpt(),
                request.ogImageUrl(),
                request.ogImageKey(),
                request.languageTag(),
                request.tags())));
  }

  @PostMapping("/{id}/publish")
  public PostView publish(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return PostView.from(publishPost.execute(new PublishPostCommand(userId, id)));
  }

  /** Get-or-create the share token so the owner can preview/share a not-yet-public post. */
  @PostMapping("/{id}/preview-token")
  public PreviewTokenResponse issuePreviewToken(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return new PreviewTokenResponse(issuePreviewToken.issue(userId, id));
  }

  @PostMapping("/{id}/schedule")
  public PostView schedule(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @Valid @RequestBody SchedulePostRequest request) {
    return PostView.from(
        schedulePost.execute(new SchedulePostCommand(userId, id, request.scheduledAt())));
  }

  @PostMapping("/{id}/unpublish")
  public PostView unpublish(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return PostView.from(unpublishPost.execute(new UnpublishPostCommand(userId, id)));
  }

  @PostMapping("/{id}/republish")
  public PostView republish(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return PostView.from(republishPost.execute(new RepublishPostCommand(userId, id)));
  }

  @PostMapping("/{id}/back-to-draft")
  public PostView backToDraft(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return PostView.from(backToDraftPost.execute(new BackToDraftPostCommand(userId, id)));
  }

  @GetMapping("/{id}/blocks")
  public List<PostBlockView> listBlocks(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return postQueryService.listBlocks(userId, id);
  }

  @PutMapping("/{id}/blocks")
  public List<PostBlockView> replaceBlocks(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @Valid @RequestBody ReplaceBlocksRequest request) {
    List<ReplacePostBlocksCommand.BlockInput> inputs =
        request.blocks().stream()
            .map(
                b ->
                    new ReplacePostBlocksCommand.BlockInput(
                        PostBlockType.valueOf(b.type().toUpperCase()), b.content()))
            .toList();
    return replacePostBlocks.execute(new ReplacePostBlocksCommand(userId, id, inputs)).stream()
        .map(PostBlockView::from)
        .toList();
  }

  @GetMapping("/{id}/revisions")
  public List<PostRevisionView> listRevisions(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return postQueryService.listRevisions(userId, id);
  }

  @PostMapping("/{id}/revisions/{versionNumber}/restore")
  public PostView restoreRevision(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @PathVariable Integer versionNumber) {
    return PostView.from(
        restorePostRevision.execute(new RestorePostRevisionCommand(userId, id, versionNumber)));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    deletePost.execute(new DeletePostCommand(userId, id));
  }
}
