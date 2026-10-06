package com.example.short_link.federation.application.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.crypto.SecretCipher;
import com.example.short_link.federation.application.ActorKeys;
import com.example.short_link.federation.application.FederationProperties;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.domain.FederationActorEntity;
import com.example.short_link.federation.domain.FederationInstanceActorEntity;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import com.example.short_link.federation.domain.repository.FederationInstanceActorRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class SigningKeysTest {

  private static final ActorKeys.Pem PEM = new ActorKeys().generate();

  @Mock private FederationActorRepository actors;
  @Mock private FederationInstanceActorRepository instance;
  @Mock private ActorKeys keys;

  private final SecretCipher cipher = new SecretCipher("");
  private final FederationUrls urls =
      new FederationUrls(new FederationProperties("https://kurl.me", null));

  private SigningKeys signingKeys() {
    return new SigningKeys(actors, instance, keys, cipher, urls);
  }

  @Test
  void aUserSignsWithTheirActorKey() {
    when(actors.findByUserId(7L))
        .thenReturn(
            Optional.of(
                new FederationActorEntity(
                    7L, "pid", PEM.publicKey(), cipher.encrypt(PEM.privateKey()))));

    Signer signer = signingKeys().forUser(7L).orElseThrow();

    assertThat(signer.keyId()).isEqualTo("https://kurl.me/ap/actors/pid#main-key");
    assertThat(signer.privateKey().getAlgorithm()).isEqualTo("RSA");
    assertThat(signingKeys().forUser(8L)).isEmpty();
  }

  @Test
  void theInstanceKeyIsCreatedOnceAndReused() {
    FederationInstanceActorEntity stored =
        new FederationInstanceActorEntity(PEM.publicKey(), cipher.encrypt(PEM.privateKey()));
    when(instance.find()).thenReturn(Optional.empty()).thenReturn(Optional.of(stored));
    when(keys.generate()).thenReturn(PEM);
    when(instance.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

    Signer first = signingKeys().forInstance();
    Signer second = signingKeys().forInstance();

    assertThat(first.keyId()).isEqualTo("https://kurl.me/ap/instance#main-key");
    assertThat(second.keyId()).isEqualTo(first.keyId());
    verify(keys).generate();
  }

  @Test
  void aRaceForTheInstanceKeyReadsTheWinnerOrRethrows() {
    FederationInstanceActorEntity winner =
        new FederationInstanceActorEntity(PEM.publicKey(), cipher.encrypt(PEM.privateKey()));
    when(instance.find()).thenReturn(Optional.empty()).thenReturn(Optional.of(winner));
    when(keys.generate()).thenReturn(PEM);
    when(instance.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

    assertThat(signingKeys().instanceActor()).isSameAs(winner);

    when(instance.find()).thenReturn(Optional.empty());
    assertThatThrownBy(() -> signingKeys().instanceActor())
        .isInstanceOf(DataIntegrityViolationException.class);
    verify(actors, never()).findByUserId(any());
  }
}
