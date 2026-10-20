package com.example.short_link.note.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.note.application.read.NoteSearchTerms;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

// Not @Transactional: InnoDB FULLTEXT does not see rows a MATCH's own transaction inserted.
@SpringBootTest
@ActiveProfiles("test")
class NoteSearchIndexIntegrationTest {

  @Autowired private NoteRepository notes;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TransactionTemplate transactions;

  private Long writer;
  private final List<Long> people = new ArrayList<>();
  private final List<Long> written = new ArrayList<>();

  private Long person() {
    String name = "w" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    UserEntity user = new UserEntity(name + "@example.com", "google", name);
    user.claimUsername(name);
    Long id = users.save(user).getId();
    people.add(id);
    return id;
  }

  @BeforeEach
  void writer() {
    writer = person();
  }

  @AfterEach
  void cleanUp() {
    written.forEach(id -> jdbc.update("DELETE FROM note WHERE id = ?", id));
    people.forEach(id -> jdbc.update("DELETE FROM user_block WHERE blocker_id = ?", id));
    people.forEach(id -> jdbc.update("DELETE FROM users WHERE id = ?", id));
  }

  private Long note(String body) {
    return note(body, "PUBLIC");
  }

  private Long note(String body, String visibility) {
    jdbc.update(
        "INSERT INTO note (user_id, body, created_at, visibility) VALUES (?, ?, NOW(6), ?)",
        writer,
        body,
        visibility);
    Long id = jdbc.queryForObject("SELECT MAX(id) FROM note WHERE user_id = ?", Long.class, writer);
    written.add(id);
    return id;
  }

  private List<Long> found(String query) {
    return found(query, null);
  }

  private List<Long> found(String query, Long viewerId) {
    NoteSearchTerms terms = NoteSearchTerms.of(query);
    return transactions.execute(
        status ->
            notes.search(terms.match(), terms.like(), viewerId, 0, 500).stream()
                .map(NoteEntity::getId)
                .filter(written::contains)
                .toList());
  }

  @Test
  void aHashtagAndWordsOfStopwordLettersFindTheirNotesThroughTheIndex() {
    Long java = note("#Java 21 의 record 정리");
    Long data = note("data class 와 record 비교");
    note("도커 네트워크 브리지");

    assertThat(NoteSearchTerms.of("Java").match()).isNotNull();
    assertThat(found("Java")).containsExactly(java);
    assertThat(found("#Java")).containsExactly(java);
    assertThat(found("data")).containsExactly(data);
  }

  @Test
  void everyWordOfAQueryMustMatch() {
    Long kmeans = note("k-means 가속기 설계");
    note("this means trouble");

    assertThat(found("k-means 가속기")).containsExactly(kmeans);
    assertThat(found("가속기 설계")).containsExactly(kmeans);
  }

  @Test
  void theIndexFindsOnlyPublicNotesAndNothingBySomeoneTheReaderBlocked() {
    String word = "tamipadi" + UUID.randomUUID().toString().substring(0, 4);
    Long open = note(word + " 공개");
    note(word + " 팔로워만", "PRIVATE");
    note(word + " 둘만", "DIRECT");
    note(word + " 조용히", "UNLISTED");
    Long blocker = person();
    jdbc.update(
        "INSERT INTO user_block (blocker_id, blocked_id, created_at) VALUES (?, ?, NOW(6))",
        blocker,
        writer);

    assertThat(found(word)).containsExactly(open);
    assertThat(found(word, person())).containsExactly(open);
    assertThat(found(word, blocker)).isEmpty();
  }
}
