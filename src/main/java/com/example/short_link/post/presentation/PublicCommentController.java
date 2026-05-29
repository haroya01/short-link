package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.CommentView;
import com.example.short_link.post.application.read.PostCommentQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 없이 글의 댓글 목록 조회. permitAll 는 GET /api/v1/public/** 가 커버. */
@RestController
@RequestMapping("/api/v1/public/posts")
@RequiredArgsConstructor
public class PublicCommentController {

  private final PostCommentQueryService postCommentQueryService;

  @GetMapping("/{postId}/comments")
  public List<CommentView> list(@PathVariable Long postId) {
    return postCommentQueryService.listForPost(postId);
  }
}
