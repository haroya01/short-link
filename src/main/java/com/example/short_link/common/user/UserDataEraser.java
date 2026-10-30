package com.example.short_link.common.user;

// Each slice deletes user-owned rows not covered by ON DELETE CASCADE before the user is
// hard-deleted, inside the caller's transaction and in its own FK order.
public interface UserDataEraser {

  void eraseFor(long userId);
}
