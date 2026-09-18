package com.example.short_link.common.lock;

/** Holds the account row until the surrounding write transaction completes. */
public interface UserMutationLock {
  void lock(Long userId);
}
