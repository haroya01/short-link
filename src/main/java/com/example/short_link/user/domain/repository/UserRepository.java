package com.example.short_link.user.domain.repository;

import com.example.short_link.user.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

  Optional<UserEntity> findByOauthProviderAndOauthId(String oauthProvider, String oauthId);

  Optional<UserEntity> findByEmail(String email);

  Optional<UserEntity> findByStripeCustomerId(String stripeCustomerId);

  Optional<UserEntity> findByUsername(String username);

  long countByCreatedAtAfter(Instant since);

  long countByUsernameIsNotNullAndDeletedAtIsNull();

  /**
   * All users with a claimed username and not soft-deleted, sorted oldest-first. Used by the
   * frontend sitemap generator to enumerate public /u/<handle> pages for Google to index.
   */
  List<UserEntity> findAllByUsernameIsNotNullAndDeletedAtIsNullOrderByCreatedAtAsc(
      Pageable pageable);

  List<UserEntity> findTop200ByDeletedAtBefore(Instant cutoff);
}
