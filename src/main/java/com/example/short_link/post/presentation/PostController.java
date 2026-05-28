package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.PostQueryService;
import com.example.short_link.post.application.read.PostView;
import com.example.short_link.post.application.write.CreatePostCommand;
import com.example.short_link.post.application.write.CreatePostUseCase;
import com.example.short_link.post.application.write.UpdatePostMetadataCommand;
import com.example.short_link.post.application.write.UpdatePostMetadataUseCase;
import com.example.short_link.post.presentation.request.CreatePostRequest;
import com.example.short_link.post.presentation.request.UpdatePostRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
  private final PostQueryService postQueryService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PostView create(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CreatePostRequest request) {
    return PostView.from(
        createPost.execute(
            new CreatePostCommand(userId, request.slug(), request.title(), request.languageTag())));
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
                request.languageTag())));
  }
}
