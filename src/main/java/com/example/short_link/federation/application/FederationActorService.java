package com.example.short_link.federation.application;

import com.example.short_link.common.crypto.SecretCipher;
import com.example.short_link.federation.domain.FederationActorEntity;
import com.example.short_link.federation.domain.FederationPreferenceEntity;
import com.example.short_link.federation.domain.FederationUser;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import com.example.short_link.federation.domain.repository.FederationPreferenceRepository;
import com.example.short_link.federation.domain.repository.FederationUserReader;
import java.security.SecureRandom;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

// Actors are created on first lookup and hidden while the user has federation turned off. Two
// concurrent first lookups race on the user_id key; the loser re-reads the winner's row.
@Service
@RequiredArgsConstructor
public class FederationActorService {

  private static final String ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
  private static final int ID_LENGTH = 20;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final FederationUserReader users;
  private final FederationActorRepository actors;
  private final FederationPreferenceRepository preferences;
  private final ActorKeys keys;
  private final SecretCipher cipher;

  public Optional<LocalActor> byUsername(String username) {
    return users.findActiveByUsername(username).filter(this::federates).map(this::withActor);
  }

  public Optional<LocalActor> byPublicId(String publicId) {
    return actors
        .findByPublicId(publicId)
        .flatMap(
            actor ->
                users
                    .findActiveById(actor.getUserId())
                    .filter(this::federates)
                    .map(
                        user ->
                            new LocalActor(user, actor.getPublicId(), actor.getPublicKeyPem())));
  }

  private boolean federates(FederationUser user) {
    return preferences.find(user.id()).map(FederationPreferenceEntity::isEnabled).orElse(true);
  }

  private LocalActor withActor(FederationUser user) {
    FederationActorEntity actor = actors.findByUserId(user.id()).orElseGet(() -> create(user.id()));
    return new LocalActor(user, actor.getPublicId(), actor.getPublicKeyPem());
  }

  private FederationActorEntity create(Long userId) {
    ActorKeys.Pem pem = keys.generate();
    try {
      return actors.saveAndFlush(
          new FederationActorEntity(
              userId, newPublicId(), pem.publicKey(), cipher.encrypt(pem.privateKey())));
    } catch (DataIntegrityViolationException raced) {
      return actors.findByUserId(userId).orElseThrow(() -> raced);
    }
  }

  static String newPublicId() {
    StringBuilder id = new StringBuilder(ID_LENGTH);
    for (int i = 0; i < ID_LENGTH; i++) {
      id.append(ID_ALPHABET.charAt(RANDOM.nextInt(ID_ALPHABET.length())));
    }
    return id.toString();
  }
}
