package com.example.short_link.federation.application.signature;

import com.example.short_link.common.crypto.SecretCipher;
import com.example.short_link.federation.application.ActorKeys;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.domain.FederationInstanceActorEntity;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import com.example.short_link.federation.domain.repository.FederationInstanceActorRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SigningKeys {

  private final FederationActorRepository actors;
  private final FederationInstanceActorRepository instance;
  private final ActorKeys keys;
  private final SecretCipher cipher;
  private final FederationUrls urls;

  public Optional<Signer> forUser(Long userId) {
    return actors
        .findByUserId(userId)
        .map(
            actor ->
                new Signer(
                    urls.key(actor.getPublicId()),
                    PemKeys.privateKey(cipher.decrypt(actor.getPrivateKeyCipher()))));
  }

  public Signer forInstance() {
    FederationInstanceActorEntity actor = instanceActor();
    return new Signer(
        urls.instanceKey(), PemKeys.privateKey(cipher.decrypt(actor.getPrivateKeyCipher())));
  }

  public FederationInstanceActorEntity instanceActor() {
    return instance.find().orElseGet(this::createInstance);
  }

  private FederationInstanceActorEntity createInstance() {
    ActorKeys.Pem pem = keys.generate();
    try {
      return instance.saveAndFlush(
          new FederationInstanceActorEntity(pem.publicKey(), cipher.encrypt(pem.privateKey())));
    } catch (DataIntegrityViolationException raced) {
      return instance.find().orElseThrow(() -> raced);
    }
  }
}
