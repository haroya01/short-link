package com.example.short_link.post.presentation;

import com.example.short_link.post.application.write.CreatePostCommand;
import com.example.short_link.post.application.write.CreatePostUseCase;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.presentation.request.CreatePostRequest;
import com.example.short_link.post.presentation.response.PostResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PostResponse create(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CreatePostRequest request) {
    PostEntity post =
        createPost.execute(
            new CreatePostCommand(userId, request.slug(), request.title(), request.languageTag()));
    return PostResponse.from(post);
  }
}
