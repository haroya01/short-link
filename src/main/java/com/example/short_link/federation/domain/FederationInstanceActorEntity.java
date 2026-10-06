package com.example.short_link.federation.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// The server's own actor signs fetches on behalf of no particular user; servers in authorized-fetch
// mode reject unsigned GETs.
@Entity
@Table(name = "federation_instance_actor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FederationInstanceActorEntity extends BaseTimeEntity {

  public static final byte SINGLETON_ID = 1;

  @Id private Byte id;

  @Column(name = "public_key_pem", nullable = false, columnDefinition = "TEXT")
  private String publicKeyPem;

  @Column(name = "private_key_cipher", nullable = false, columnDefinition = "TEXT")
  private String privateKeyCipher;

  public FederationInstanceActorEntity(String publicKeyPem, String privateKeyCipher) {
    this.id = SINGLETON_ID;
    this.publicKeyPem = publicKeyPem;
    this.privateKeyCipher = privateKeyCipher;
  }
}
