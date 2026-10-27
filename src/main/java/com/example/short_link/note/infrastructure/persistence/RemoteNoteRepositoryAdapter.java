package com.example.short_link.note.infrastructure.persistence;

import static com.example.short_link.note.infrastructure.persistence.NoteSql.notReply;

import com.example.short_link.common.note.NoteAccessSql;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.RemoteNoteRow;
import com.example.short_link.note.domain.repository.RemoteNoteRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class RemoteNoteRepositoryAdapter implements RemoteNoteRepository {

  private final JpaNoteRepository jpa;

  @PersistenceContext private EntityManager em;

  @Override
  public Optional<Long> idByUri(String uri) {
    List<?> rows =
        em.createNativeQuery("SELECT id FROM note WHERE uri = :uri")
            .setParameter("uri", uri)
            .getResultList();
    return rows.stream().findFirst().map(id -> ((Number) id).longValue());
  }

  // INSERT IGNORE on the uri key: a note delivered twice (shared and personal inbox, retries) is
  // stored once, and the second delivery reads back the first row.
  @Override
  public Optional<Long> insertRemote(RemoteNoteRow row) {
    RemoteNoteRow.Poll poll = row.poll();
    int inserted =
        em.createNativeQuery(
                "INSERT IGNORE INTO note (remote_actor_id, uri, remote_url, body, created_at,"
                    + " content_warning, marked_sensitive, visibility, in_reply_to_id,"
                    + " conversation_id, reply, reply_policy, language, remote_kind, remote_title,"
                    + " remote_community, poll_options, poll_expires_at, poll_multiple,"
                    + " poll_closed_at, poll_votes, poll_voters, quoted_note_id) VALUES"
                    + " (:actor, :uri, :url, :body, :createdAt, :warning, :sensitive, :visibility,"
                    + " :parent, :conversation, :reply, :replyPolicy, :language, :kind, :title,"
                    + " :community, :pollOptions, :pollExpiresAt, :pollMultiple, :pollClosedAt,"
                    + " :pollVotes, :pollVoters, :quoted)")
            .setParameter("actor", row.remoteActorId())
            .setParameter("uri", row.uri())
            .setParameter("url", row.url())
            .setParameter("body", row.body())
            .setParameter("createdAt", row.createdAt())
            .setParameter("warning", row.contentWarning())
            .setParameter("sensitive", row.sensitive())
            .setParameter("visibility", row.visibility().name())
            .setParameter("parent", row.inReplyToId())
            .setParameter("conversation", row.conversationId())
            .setParameter("reply", row.reply())
            .setParameter("replyPolicy", row.replyPolicy().name())
            .setParameter("language", row.language())
            .setParameter("kind", row.kind())
            .setParameter("title", row.title())
            .setParameter("community", row.community())
            .setParameter(
                "pollOptions", poll == null ? null : NoteEntity.optionsColumn(poll.options()))
            .setParameter("pollExpiresAt", poll == null ? null : poll.expiresAt())
            .setParameter("pollMultiple", poll != null && poll.multiple())
            .setParameter("pollClosedAt", poll == null ? null : poll.closedAt())
            .setParameter("pollVotes", poll == null ? null : NoteEntity.votesColumn(poll.votes()))
            .setParameter(
                "pollVoters", poll == null ? null : Math.clamp(poll.voters(), 0, Integer.MAX_VALUE))
            .setParameter("quoted", row.quotedNoteId())
            .executeUpdate();
    return inserted == 0 ? Optional.empty() : idByUri(row.uri());
  }

  @Override
  public int countListed(Long remoteActorId, int atMost) {
    Object count =
        em.createNativeQuery(
                "SELECT COUNT(*) FROM (SELECT 1 FROM note n WHERE n.remote_actor_id = :actor"
                    + " AND "
                    + NoteAccessSql.shareable("n")
                    + " AND "
                    + notReply("n")
                    + " LIMIT :atMost) listed")
            .setParameter("actor", remoteActorId)
            .setParameter("atMost", atMost)
            .getSingleResult();
    return ((Number) count).intValue();
  }

  @Override
  public Optional<NoteEntity> findRemoteForUpdate(Long remoteActorId, Long noteId) {
    return jpa.findRemoteForUpdate(noteId, remoteActorId);
  }

  @Override
  public int deleteRemote(Long remoteActorId, String uri) {
    return em.createNativeQuery("DELETE FROM note WHERE uri = :uri AND remote_actor_id = :actor")
        .setParameter("uri", uri)
        .setParameter("actor", remoteActorId)
        .executeUpdate();
  }
}
