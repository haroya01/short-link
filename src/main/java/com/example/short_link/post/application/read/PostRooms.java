package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRoomReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// The room each room post is in, named, for the posts in hand; nothing is asked when none is a room
// post.
@Component
@RequiredArgsConstructor
public class PostRooms {

  private final PostRoomReader rooms;

  public Map<Long, PostRoomView> of(Collection<PostEntity> posts) {
    Set<Long> roomIds =
        posts.stream()
            .filter(PostEntity::isInRoom)
            .map(PostEntity::getRoomId)
            .collect(Collectors.toSet());
    Map<Long, PostRoomView> byPost = new HashMap<>();
    if (roomIds.isEmpty()) {
      return byPost;
    }
    Map<Long, String> names = rooms.names(roomIds);
    for (PostEntity post : posts) {
      if (post.isInRoom()) {
        byPost.put(post.getId(), new PostRoomView(post.getRoomId(), names.get(post.getRoomId())));
      }
    }
    return byPost;
  }

  public PostRoomView of(PostEntity post) {
    if (!post.isInRoom()) {
      return null;
    }
    return new PostRoomView(
        post.getRoomId(), rooms.names(List.of(post.getRoomId())).get(post.getRoomId()));
  }
}
