package com.example.short_link.federation.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "federation_domain_block")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FederationDomainBlockEntity extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String domain;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ServerBlockSeverity severity;

  @Column(length = 500)
  private String reason;

  public FederationDomainBlockEntity(String domain, ServerBlockSeverity severity, String reason) {
    this.domain = domain;
    change(severity, reason);
  }

  public void change(ServerBlockSeverity severity, String reason) {
    this.severity = severity;
    this.reason = reason;
  }
}
