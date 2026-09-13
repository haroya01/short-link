package com.example.short_link.common.user;

/**
 * Each slice deletes user-owned rows not covered by ON DELETE CASCADE before the user is
 * hard-deleted. Implementations share the caller's transaction and must respect their own FK order;
 * this port avoids repository dependency cycles.
 */
public interface UserDataEraser {

  void eraseFor(long userId);
}
