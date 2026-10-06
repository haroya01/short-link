package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.crypto.SecretCipher;
import com.example.short_link.federation.domain.FederationActorEntity;
import com.example.short_link.federation.domain.FederationUser;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import com.example.short_link.federation.domain.repository.FederationUserReader;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class FederationActorServiceTest {

  private static final FederationUser YUKI = new FederationUser(7L, "yuki", "bio", null);

  @Mock private FederationUserReader users;
  @Mock private FederationActorRepository actors;
  @Mock private ActorKeys keys;
  @Mock private SecretCipher cipher;

  private FederationActorService service() {
    return new FederationActorService(users, actors, keys, cipher);
  }

  @Test
  void existingActorIsReusedWithoutNewKeys() {
    when(users.findActiveByUsername("yuki")).thenReturn(Optional.of(YUKI));
    when(actors.findByUserId(7L))
        .thenReturn(Optional.of(new FederationActorEntity(7L, "pid", "PUB", "enc")));

    LocalActor actor = service().byUsername("yuki").orElseThrow();

    assertThat(actor.publicId()).isEqualTo("pid");
    assertThat(actor.publicKeyPem()).isEqualTo("PUB");
    verify(keys, never()).generate();
  }

  @Test
  void firstLookupCreatesAnActorWithAnEncryptedPrivateKey() {
    when(users.findActiveByUsername("yuki")).thenReturn(Optional.of(YUKI));
    when(actors.findByUserId(7L)).thenReturn(Optional.empty());
    when(keys.generate()).thenReturn(new ActorKeys.Pem("PUB", "PRIV"));
    when(cipher.encrypt("PRIV")).thenReturn("v1:enc");
    when(actors.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

    LocalActor actor = service().byUsername("yuki").orElseThrow();

    ArgumentCaptor<FederationActorEntity> saved =
        ArgumentCaptor.forClass(FederationActorEntity.class);
    verify(actors).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getPrivateKeyCipher()).isEqualTo("v1:enc");
    assertThat(saved.getValue().getPublicId()).matches("[a-z0-9]{20}");
    assertThat(actor.publicId()).isEqualTo(saved.getValue().getPublicId());
  }

  @Test
  void aConcurrentFirstLookupReadsTheWinnersRow() {
    when(users.findActiveByUsername("yuki")).thenReturn(Optional.of(YUKI));
    when(actors.findByUserId(7L))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(new FederationActorEntity(7L, "winner", "PUB", "enc")));
    when(keys.generate()).thenReturn(new ActorKeys.Pem("PUB2", "PRIV2"));
    when(cipher.encrypt("PRIV2")).thenReturn("v1:enc2");
    when(actors.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

    assertThat(service().byUsername("yuki").orElseThrow().publicId()).isEqualTo("winner");
  }

  @Test
  void aConflictWithoutAWinnerRowIsRethrown() {
    when(users.findActiveByUsername("yuki")).thenReturn(Optional.of(YUKI));
    when(actors.findByUserId(7L)).thenReturn(Optional.empty());
    when(keys.generate()).thenReturn(new ActorKeys.Pem("PUB", "PRIV"));
    when(cipher.encrypt("PRIV")).thenReturn("v1:enc");
    when(actors.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

    assertThatThrownBy(() -> service().byUsername("yuki"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void unknownOrDeletedUsersDoNotResolve() {
    when(users.findActiveByUsername("ghost")).thenReturn(Optional.empty());
    assertThat(service().byUsername("ghost")).isEmpty();

    when(actors.findByPublicId("pid"))
        .thenReturn(Optional.of(new FederationActorEntity(7L, "pid", "PUB", "enc")));
    when(users.findActiveById(7L)).thenReturn(Optional.empty());
    assertThat(service().byPublicId("pid")).isEmpty();

    when(actors.findByPublicId("nope")).thenReturn(Optional.empty());
    assertThat(service().byPublicId("nope")).isEmpty();
  }

  @Test
  void publicIdLooksUpTheLiveUser() {
    when(actors.findByPublicId("pid"))
        .thenReturn(Optional.of(new FederationActorEntity(7L, "pid", "PUB", "enc")));
    when(users.findActiveById(7L)).thenReturn(Optional.of(YUKI));

    LocalActor actor = service().byPublicId("pid").orElseThrow();

    assertThat(actor.user()).isEqualTo(YUKI);
    assertThat(actor.publicKeyPem()).isEqualTo("PUB");
  }
}
