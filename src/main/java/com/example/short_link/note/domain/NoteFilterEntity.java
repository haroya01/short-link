package com.example.short_link.note.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "note_filter")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoteFilterEntity extends BaseCreatedEntity {

  public static final int MAX_PHRASE_LENGTH = 100;
  public static final int MAX_FILTERS = 100;

  public enum Context {
    HOME,
    PUBLIC,
    THREAD,
    ACCOUNT,
    NOTIFICATIONS;

    int bit() {
      return 1 << ordinal();
    }

    public String apiName() {
      return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Context> parse(String raw) {
      return Arrays.stream(values()).filter(c -> c.apiName().equals(raw)).findFirst();
    }
  }

  public enum Action {
    WARN,
    HIDE;

    public String apiName() {
      return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Action> parse(String raw) {
      return Arrays.stream(values()).filter(a -> a.apiName().equals(raw)).findFirst();
    }
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = MAX_PHRASE_LENGTH)
  private String phrase;

  @Column(name = "whole_word", nullable = false)
  private boolean wholeWord;

  @Getter(AccessLevel.NONE)
  @Column(nullable = false)
  private int contexts;

  @Enumerated(EnumType.STRING)
  @Column(name = "filter_action", nullable = false, length = 8)
  private Action action;

  @Column(name = "expires_at")
  private Instant expiresAt;

  public NoteFilterEntity(
      Long userId,
      String phrase,
      boolean wholeWord,
      Set<Context> contexts,
      Action action,
      Instant expiresAt) {
    this.userId = userId;
    change(phrase, wholeWord, contexts, action, expiresAt);
  }

  public void change(
      String phrase, boolean wholeWord, Set<Context> contexts, Action action, Instant expiresAt) {
    this.phrase = phrase;
    this.wholeWord = wholeWord;
    this.contexts = contexts.stream().mapToInt(Context::bit).sum();
    this.action = action;
    this.expiresAt = expiresAt;
  }

  public Set<Context> contexts() {
    Set<Context> set = EnumSet.noneOf(Context.class);
    for (Context context : Context.values()) {
      if ((contexts & context.bit()) != 0) {
        set.add(context);
      }
    }
    return set;
  }
}
