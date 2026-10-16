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
import org.hibernate.annotations.Formula;

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

  @Column(name = "account_domain", length = 255)
  private String accountDomain;

  @Column(name = "profile_url", length = 512)
  private String profileUrl;

  @Column(name = "display_name", length = 255)
  private String displayName;

  @Column(name = "avatar_url", length = 512)
  private String avatarUrl;

  @Column(nullable = false)
  private boolean discoverable;

  @Column(nullable = false)
  private boolean indexable;

  @Column(name = "fetched_at", nullable = false)
  private Instant fetchedAt;

  @Column(name = "outbox_checked_at", insertable = false, updatable = false)
  private Instant outboxCheckedAt;

  // The moderators' decision about this account's server rides along with every load of the
  // account, so the inbox, lookups and follows need no query of their own.
  // The strongest block on the account's domain or a parent down to two labels, as ServerBlockSql
  // reads it; each parent is its own unique-key lookup, because a formula cannot alias a derived
  // table.
  @Formula(
      "NULLIF(GREATEST("
          + "COALESCE((SELECT cb.severity FROM federation_domain_block cb"
          + " WHERE cb.domain = domain), ''),"
          + " COALESCE((SELECT cb.severity FROM federation_domain_block cb"
          + " WHERE cb.domain = SUBSTRING_INDEX(domain, '.', -6)), ''),"
          + " COALESCE((SELECT cb.severity FROM federation_domain_block cb"
          + " WHERE cb.domain = SUBSTRING_INDEX(domain, '.', -5)), ''),"
          + " COALESCE((SELECT cb.severity FROM federation_domain_block cb"
          + " WHERE cb.domain = SUBSTRING_INDEX(domain, '.', -4)), ''),"
          + " COALESCE((SELECT cb.severity FROM federation_domain_block cb"
          + " WHERE cb.domain = SUBSTRING_INDEX(domain, '.', -3)), ''),"
          + " COALESCE((SELECT cb.severity FROM federation_domain_block cb"
          + " WHERE cb.domain = SUBSTRING_INDEX(domain, '.', -2)), '')), '')")
  private String serverBlock;

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
    this.discoverable = document.discoverable();
    this.indexable = document.indexable();
    this.fetchedAt = fetchedAt;
  }

  public void knownAt(String accountDomain) {
    this.accountDomain = accountDomain;
  }

  public boolean onSuspendedServer() {
    return ServerBlockSeverity.SUSPEND.name().equals(serverBlock);
  }

  public String deliveryInbox() {
    return sharedInbox != null ? sharedInbox : inbox;
  }
}
