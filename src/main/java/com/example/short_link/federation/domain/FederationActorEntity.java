package com.example.short_link.federation.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// The actor URL is built from publicId, never the username: remote servers key follows and keys by
// actor id, and a username can change.
@Entity
@Table(name = "federation_actor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FederationActorEntity extends BaseTimeEntity {

  @Id
  @Column(name = "user_id")
  private Long userId;

  @Column(name = "public_id", nullable = false, length = 32)
  private String publicId;

  @Column(name = "public_key_pem", nullable = false, columnDefinition = "TEXT")
  private String publicKeyPem;

  @Column(name = "private_key_cipher", nullable = false, columnDefinition = "TEXT")
  private String privateKeyCipher;

  public FederationActorEntity(
      Long userId, String publicId, String publicKeyPem, String privateKeyCipher) {
    this.userId = userId;
    this.publicId = publicId;
    this.publicKeyPem = publicKeyPem;
    this.privateKeyCipher = privateKeyCipher;
  }
}
