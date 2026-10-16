package com.example.short_link.post.application.access;

public interface PostPasswordAttempts {

  // Counts this attempt before the password is checked; false once the window's limit is passed.
  boolean tryAttempt(Long postId, String clientIp);

  long retryAfterSeconds(Long postId, String clientIp);

  void reset(Long postId, String clientIp);
}
