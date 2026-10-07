package com.example.short_link.user.domain.repository;

import com.example.short_link.user.domain.UserMuteEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MuteRepository {

  Optional<UserMuteEntity> find(Long userId, Long mutedUserId);

  UserMuteEntity save(UserMuteEntity mute);

  void delete(UserMuteEntity mute);

  List<UserMuteEntity> active(Long userId, Instant now);

  boolean silences(Long recipientId, Long actorId, Instant now);

  boolean silences(
      Long recipientId, Long actorId, Long remoteActorId, Long conversationId, Instant now);

  int deleteAllInvolving(Long userId);
}
