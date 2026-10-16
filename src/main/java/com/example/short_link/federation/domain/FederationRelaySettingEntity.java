package com.example.short_link.federation.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "federation_relay_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FederationRelaySettingEntity extends BaseTimeEntity {

  public static final byte SINGLETON_ID = 1;

  @Id private Byte id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private RelayMode mode;

  @Column(nullable = false, length = 255)
  private String languages;

  public List<String> languageList() {
    return Arrays.stream(languages.split(","))
        .map(String::strip)
        .filter(l -> !l.isEmpty())
        .toList();
  }

  public void change(RelayMode mode, List<String> languages) {
    this.mode = mode;
    this.languages = String.join(",", languages);
  }
}
