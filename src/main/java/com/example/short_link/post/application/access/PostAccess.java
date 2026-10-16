package com.example.short_link.post.application.access;

import com.example.short_link.common.post.PostLock;

public record PostAccess(Outcome outcome, PostLock lock) {

  public enum Outcome {
    FULL,
    REDACTED,
    HIDDEN
  }

  public static final PostAccess FULL = new PostAccess(Outcome.FULL, null);
  public static final PostAccess HIDDEN = new PostAccess(Outcome.HIDDEN, null);

  public static PostAccess locked(PostLock lock) {
    return new PostAccess(Outcome.REDACTED, lock);
  }

  static PostAccess redactedCard() {
    return new PostAccess(Outcome.REDACTED, null);
  }

  public boolean full() {
    return outcome == Outcome.FULL;
  }

  public boolean hidden() {
    return outcome == Outcome.HIDDEN;
  }

  public boolean redacted() {
    return outcome == Outcome.REDACTED;
  }
}
