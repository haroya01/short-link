package com.example.short_link.profile.visit;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileVisitRepository extends JpaRepository<ProfileVisitEntity, Long> {

  long countByProfileUserId(Long profileUserId);

  long countByProfileUserIdAndBotFalse(Long profileUserId);

  long countByProfileUserIdAndVisitedAtAfter(Long profileUserId, Instant after);

  long countByProfileUserIdAndBotFalseAndVisitedAtAfter(Long profileUserId, Instant after);
}
