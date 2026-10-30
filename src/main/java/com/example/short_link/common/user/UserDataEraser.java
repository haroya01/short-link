package com.example.short_link.common.user;

import java.util.List;

// Each slice deletes user-owned rows not covered by ON DELETE CASCADE before the user is
// hard-deleted, inside the caller's transaction and in its own FK order. Stored files are only
// named here; the caller removes them after that transaction commits.
public interface UserDataEraser {

  void eraseFor(long userId);

  default List<String> storedFilesOf(long userId) {
    return List.of();
  }
}
