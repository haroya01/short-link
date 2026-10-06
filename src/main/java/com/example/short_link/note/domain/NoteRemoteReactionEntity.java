package com.example.short_link.note.domain;

import com.example.short_link.common.note.RemoteNoteReactions;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "note_remote_reaction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoteRemoteReactionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "note_id", nullable = false)
  private Long noteId;

  @Column(name = "remote_actor_id", nullable = false)
  private Long remoteActorId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 8)
  private RemoteNoteReactions.Kind kind;

  @Column(name = "activity_id", nullable = false, length = 512)
  private String activityId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
