package com.example.short_link.note.application.write;

import com.example.short_link.note.application.read.NoteFeedView;
import com.example.short_link.note.application.read.NoteViews;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteListEntity;
import com.example.short_link.note.domain.repository.NoteListRepository;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NoteListService {

  private static final int MAX_PAGE_SIZE = 50;

  private final NoteListRepository lists;
  private final NotePeopleReader people;
  private final NoteViews views;

  @Transactional(readOnly = true)
  public List<ListView> mine(Long userId) {
    return lists.summaries(userId).stream()
        .map(summary -> new ListView(summary.id(), summary.title(), summary.memberCount()))
        .toList();
  }

  @Transactional
  public ListView create(Long userId, String rawTitle) {
    String title = title(rawTitle);
    if (lists.countLists(userId) >= NoteListEntity.MAX_LISTS) {
      throw new NoteException(NoteErrorCode.NOTE_LIST_LIMIT, NoteListEntity.MAX_LISTS);
    }
    NoteListEntity list = lists.save(new NoteListEntity(userId, title));
    return new ListView(list.getId(), list.getTitle(), 0);
  }

  @Transactional
  public ListView rename(Long userId, Long listId, String rawTitle) {
    NoteListEntity list = owned(userId, listId);
    list.rename(title(rawTitle));
    return new ListView(list.getId(), list.getTitle(), lists.countMembers(listId));
  }

  @Transactional
  public void delete(Long userId, Long listId) {
    lists.delete(owned(userId, listId));
  }

  @Transactional(readOnly = true)
  public List<NoteAuthor> members(Long userId, Long listId) {
    owned(userId, listId);
    List<Long> ids = lists.memberIds(listId);
    Map<Long, NoteAuthor> found = people.activeAuthors(ids);
    return ids.stream().map(found::get).filter(Objects::nonNull).toList();
  }

  @Transactional
  public void add(Long userId, Long listId, String username) {
    owned(userId, listId);
    NoteAuthor member = person(username);
    if (member.id().equals(userId)) {
      throw new NoteException(NoteErrorCode.NOTE_LIST_SELF);
    }
    if (lists.countMembers(listId) >= NoteListEntity.MAX_MEMBERS) {
      throw new NoteException(NoteErrorCode.NOTE_LIST_MEMBER_LIMIT, NoteListEntity.MAX_MEMBERS);
    }
    lists.addMember(listId, member.id());
  }

  @Transactional
  public void remove(Long userId, Long listId, String username) {
    owned(userId, listId);
    lists.removeMember(listId, person(username).id());
  }

  @Transactional(readOnly = true)
  public Membership membership(Long userId, String username) {
    return new Membership(lists.listsContaining(userId, person(username).id()));
  }

  @Transactional(readOnly = true)
  public NoteFeedView feed(Long userId, Long listId, int page, int size) {
    owned(userId, listId);
    int safePage = Math.max(page, 0);
    int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
    List<NoteEntity> rows = lists.feed(listId, userId, safePage * safeSize, safeSize + 1);
    boolean hasNext = rows.size() > safeSize;
    List<NoteEntity> current = hasNext ? rows.subList(0, safeSize) : rows;
    return new NoteFeedView(views.ofFeed(current, userId), safePage, hasNext);
  }

  private NoteListEntity owned(Long userId, Long listId) {
    return lists
        .owned(listId, userId)
        .orElseThrow(() -> new NoteException(NoteErrorCode.NOTE_LIST_NOT_FOUND, listId));
  }

  private NoteAuthor person(String username) {
    return people
        .activeByUsername(username)
        .orElseThrow(() -> new NoteException(NoteErrorCode.NOTE_NOT_FOUND, username));
  }

  private static String title(String raw) {
    String title = raw == null ? "" : raw.strip();
    int length = title.codePointCount(0, title.length());
    if (length == 0 || length > NoteListEntity.MAX_TITLE_LENGTH) {
      throw new NoteException(
          NoteErrorCode.NOTE_LIST_TITLE_INVALID, NoteListEntity.MAX_TITLE_LENGTH);
    }
    return title;
  }

  public record ListView(Long id, String title, long memberCount) {}

  public record Membership(List<Long> listIds) {}
}
