package com.example.short_link.federation.domain.repository;

import com.example.short_link.federation.domain.FederationUser;
import java.util.Optional;

// Active (not soft-deleted) users only; a deleted account must stop resolving at once.
public interface FederationUserReader {

  Optional<FederationUser> findActiveByUsername(String username);

  Optional<FederationUser> findActiveById(Long userId);
}
