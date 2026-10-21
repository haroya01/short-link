package com.example.short_link.post.domain.repository;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface PostRoomReader {

  boolean isMember(Long roomId, Long userId);

  Set<Long> roomsOf(Long userId);

  Set<Long> membersAmong(Long roomId, Collection<Long> userIds);

  Map<Long, String> names(Collection<Long> roomIds);
}
