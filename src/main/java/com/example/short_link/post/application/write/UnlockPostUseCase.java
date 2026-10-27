package com.example.short_link.post.application.write;

import com.example.short_link.common.user.Usernames;
import com.example.short_link.post.application.access.PostAccess;
import com.example.short_link.post.application.access.PostAccessPolicy;
import com.example.short_link.post.application.access.PostGrants;
import com.example.short_link.post.application.access.PostPasswordAttempts;
import com.example.short_link.post.application.access.PostPasswords;
import com.example.short_link.post.application.access.PostViewer;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.example.short_link.user.application.read.UserSummary;
import com.example.short_link.user.application.read.UserSummaryReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UnlockPostUseCase {

  private final UserSummaryReader users;
  private final PostRepository posts;
  private final PostAccessPolicy access;
  private final PostPasswords passwords;
  private final PostPasswordAttempts attempts;
  private final PostGrants grants;

  public PostGrants.Grant unlock(String username, String slug, String password, PostViewer reader) {
    String handle = Usernames.canonical(username);
    UserSummary author =
        users
            .findActiveByUsername(handle)
            .orElseThrow(() -> new ProfileException(ProfileErrorCode.PROFILE_NOT_FOUND, handle));
    PostEntity post =
        posts
            .findUnblockedByUserIdAndSlug(author.id(), slug, reader.viewerId())
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, slug));
    PostAccess decided = access.decide(post, reader.withoutCredentials());
    if (decided.hidden() || !post.getAudience().needsPassword()) {
      throw new PostException(PostErrorCode.POST_NOT_FOUND, slug);
    }
    if (decided.redacted() && decided.lock() != post.getAudience().lock()) {
      throw new PostException(PostErrorCode.POST_LOCKED, slug).with("reason", decided.lock());
    }
    if (!attempts.tryAttempt(post.getId(), reader.clientIp())) {
      throw new PostException(PostErrorCode.POST_PASSWORD_ATTEMPTS_EXCEEDED)
          .with("retryAfterSeconds", attempts.retryAfterSeconds(post.getId(), reader.clientIp()));
    }
    if (!passwords.matches(password, post.getPasswordHash())) {
      throw new PostException(PostErrorCode.POST_PASSWORD_INVALID);
    }
    attempts.reset(post.getId(), reader.clientIp());
    return grants.issue(post);
  }
}
