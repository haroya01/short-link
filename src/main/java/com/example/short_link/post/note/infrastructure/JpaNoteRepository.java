package com.example.short_link.post.note.infrastructure;

import com.example.short_link.post.note.domain.NoteEntity;
import com.example.short_link.post.note.domain.NoteRow;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaNoteRepository extends JpaRepository<NoteEntity, Long> {

  String ROW_SELECT =
      """
      select new com.example.short_link.post.note.domain.NoteRow(
          n.id, n.body, n.createdAt,
          (select count(l) from NoteLikeEntity l where l.noteId = n.id),
          u.id, u.username, u.avatarUrl)
      from NoteEntity n, com.example.short_link.user.domain.UserEntity u
      where u.id = n.userId
      """;

  @Query(ROW_SELECT + " order by n.id desc")
  List<NoteRow> feedRows(Pageable pageable);

  @Query(ROW_SELECT + " and n.id = :id")
  Optional<NoteRow> findRowById(@Param("id") Long id);
}
