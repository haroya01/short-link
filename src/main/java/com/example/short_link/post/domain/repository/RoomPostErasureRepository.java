package com.example.short_link.post.domain.repository;

import java.time.Instant;
import java.util.List;

public interface RoomPostErasureRepository {

  record Request(Long userId, Long roomId) {}

  void request(Long userId, Long roomId, Instant at);

  List<Request> pending(int limit);

  void finish(Long userId, Long roomId);

  List<Long> postIdsBy(Long userId, Long roomId, int limit);

  List<Long> commentIdsBy(Long userId, Long roomId, int limit);

  List<Long> highlightIdsBy(Long userId, Long roomId, int limit);

  List<Long> highlightReplyIdsBy(Long userId, Long roomId, int limit);
}
