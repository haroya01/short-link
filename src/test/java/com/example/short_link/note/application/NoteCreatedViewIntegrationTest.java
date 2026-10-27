package com.example.short_link.note.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.note.application.read.NoteQueryService;
import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.note.application.write.NoteCommandService;
import com.example.short_link.note.application.write.NoteDraft;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NoteCreatedViewIntegrationTest {

  @Autowired private NoteCommandService command;
  @Autowired private NoteQueryService query;
  @Autowired private UserRepository users;
  @PersistenceContext private EntityManager em;

  @Test
  void theCreatedNoteCarriesTheWritersOwnMarksAsTheirFeedShowsThem() {
    String handle = "nc" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    UserEntity user = new UserEntity(handle + "@x.com", "google", "g-" + handle);
    user.claimUsername(handle);
    long writer = users.save(user).getId();

    NoteView created = command.create(writer, new NoteDraft("새 노트", List.of(), null, null));
    em.flush();
    em.clear();
    NoteView listed =
        query.byAuthor(handle, 0, 20, writer).items().stream()
            .filter(note -> note.id().equals(created.id()))
            .findFirst()
            .orElseThrow();

    assertThat(created)
        .extracting(
            NoteView::likedByMe,
            NoteView::repostedByMe,
            NoteView::bookmarkedByMe,
            NoteView::conversationMuted,
            NoteView::canReply)
        .containsExactly(
            listed.likedByMe(),
            listed.repostedByMe(),
            listed.bookmarkedByMe(),
            listed.conversationMuted(),
            listed.canReply());
  }
}
