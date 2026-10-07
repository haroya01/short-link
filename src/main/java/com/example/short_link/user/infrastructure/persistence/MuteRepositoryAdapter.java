package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.UserMuteEntity;
import com.example.short_link.user.domain.repository.MuteRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class MuteRepositoryAdapter implements MuteRepository {

  private final JpaMuteRepository jpa;

  @PersistenceContext private EntityManager em;

  @Override
  public Optional<UserMuteEntity> find(Long userId, Long mutedUserId) {
    return jpa.findByUserIdAndMutedUserId(userId, mutedUserId);
  }

  @Override
  public UserMuteEntity save(UserMuteEntity mute) {
    return jpa.save(mute);
  }

  @Override
  public void delete(UserMuteEntity mute) {
    jpa.delete(mute);
  }

  @Override
  public List<UserMuteEntity> active(Long userId, Instant now) {
    return jpa.findActive(userId, now);
  }

  @Override
  public boolean silences(Long recipientId, Long actorId, Instant now) {
    Object hit =
        em.createNativeQuery(
                "SELECT EXISTS (SELECT 1 FROM user_block b"
                    + " WHERE b.blocker_id = :recipient AND b.blocked_id = :actor)"
                    + " OR EXISTS (SELECT 1 FROM user_mute m"
                    + " WHERE m.user_id = :recipient AND m.muted_user_id = :actor"
                    + " AND m.hide_notifications AND (m.expires_at IS NULL OR m.expires_at > :now))")
            .setParameter("recipient", recipientId)
            .setParameter("actor", actorId)
            .setParameter("now", now)
            .getSingleResult();
    return ((Number) hit).intValue() == 1;
  }

  // One statement either way. The conversation and server blocks belong to the note and federation
  // slices and are read here natively so a notice costs one check, not three.
  @Override
  public boolean silences(
      Long recipientId, Long actorId, Long remoteActorId, Long conversationId, Instant now) {
    List<String> checks = new ArrayList<>();
    if (actorId != null) {
      checks.add(
          "EXISTS (SELECT 1 FROM user_block b"
              + " WHERE b.blocker_id = :recipient AND b.blocked_id = :actor)"
              + " OR EXISTS (SELECT 1 FROM user_mute m"
              + " WHERE m.user_id = :recipient AND m.muted_user_id = :actor"
              + " AND m.hide_notifications AND (m.expires_at IS NULL OR m.expires_at > :now))");
    }
    if (remoteActorId != null) {
      checks.add(
          "EXISTS (SELECT 1 FROM user_domain_block d"
              + " JOIN federation_remote_actor a ON a.domain = d.domain"
              + " WHERE d.user_id = :recipient AND a.id = :remote)");
    }
    if (conversationId != null) {
      checks.add(
          "EXISTS (SELECT 1 FROM note_conversation_mute c"
              + " WHERE c.user_id = :recipient AND c.conversation_id = :conversation)");
    }
    var query =
        em.createNativeQuery("SELECT " + String.join(" OR ", checks))
            .setParameter("recipient", recipientId);
    if (actorId != null) {
      query.setParameter("actor", actorId).setParameter("now", now);
    }
    if (remoteActorId != null) {
      query.setParameter("remote", remoteActorId);
    }
    if (conversationId != null) {
      query.setParameter("conversation", conversationId);
    }
    return ((Number) query.getSingleResult()).intValue() == 1;
  }

  @Override
  public int deleteAllInvolving(Long userId) {
    return jpa.deleteAllInvolving(userId);
  }
}
