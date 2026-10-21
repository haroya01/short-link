package com.example.short_link.common.room;

// The post slice implements this so a member leaving a room can take their room posts, and what
// they
// said on room posts, with them. Asking returns at once; the posts go in the background.
public interface RoomPostRemover {

  void erasePostsBy(Long userId, Long roomId);
}
