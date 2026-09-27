package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.PostLinkClick;
import java.time.Instant;
import java.util.List;

public interface PostLinkClickReader {

  long countByPostId(Long postId);

  long countByPostIdSince(Long postId, Instant since);

  long countByUserId(Long userId);

  long countByUserIdSince(Long userId, Instant since);

  List<PostLinkClick> breakdownByPostId(Long postId, int limit);
}
