package com.example.short_link.portability.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// One line of the uploaded file, its cells joined by a unit separator (a CSV cell cannot hold it).
@Entity
@Table(name = "account_import_row")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountImportRowEntity {

  public static final String SEPARATOR = "\u001F";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "import_id", nullable = false)
  private Long importId;

  @Column(nullable = false, length = 2048)
  private String fields;

  @Column(name = "processed_at")
  private Instant processedAt;

  public AccountImportRowEntity(Long importId, List<String> cells) {
    this.importId = importId;
    this.fields = String.join(SEPARATOR, cells);
  }

  public List<String> cells() {
    return List.of(fields.split(SEPARATOR, -1));
  }
}
