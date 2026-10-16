package com.example.short_link.post.application.read;

import com.example.short_link.common.post.PostLock;
import com.example.short_link.post.application.access.PostAccess;

public record PostLockView(PostLock reason) {

  public static PostLockView of(PostAccess access) {
    return access.lock() == null ? null : new PostLockView(access.lock());
  }
}
