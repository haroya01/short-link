package com.example.short_link.federation.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "federation_remote_actor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RemoteActorEntity extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "actor_uri", nullable = false, length = 512)
  private String actorUri;

  @Column(name = "key_id", nullable = false, length = 600)
  private String keyId;

  @Column(name = "public_key_pem", nullable = false, columnDefinition = "TEXT")
  private String publicKeyPem;

  @Column(nullable = false, length = 512)
  private String inbox;

  @Column(name = "shared_inbox", length = 512)
  private String sharedInbox;

  @Column(length = 255)
  private String username;

  @Column(nullable = false, length = 255)
  private String domain;

  @Column(name = "profile_url", length = 512)
  private String profileUrl;

  @Column(name = "display_name", length = 255)
  private String displayName;

  @Column(name = "avatar_url", length = 512)
  private String avatarUrl;

  @Column(name = "fetched_at", nullable = false)
  private Instant fetchedAt;

  public RemoteActorEntity(RemoteActorDocument document, Instant fetchedAt) {
    this.actorUri = document.actorUri();
    refresh(document, fetchedAt);
  }

  public void refresh(RemoteActorDocument document, Instant fetchedAt) {
    this.keyId = document.keyId();
    this.publicKeyPem = document.publicKeyPem();
    this.inbox = document.inbox();
    this.sharedInbox = document.sharedInbox();
    this.username = document.username();
    this.domain = document.domain();
    this.profileUrl = document.profileUrl();
    this.displayName = document.displayName();
    this.avatarUrl = document.avatarUrl();
    this.fetchedAt = fetchedAt;
  }

  public String deliveryInbox() {
    return sharedInbox != null ? sharedInbox : inbox;
  }
}
