package com.example.short_link.note.application.live;

import com.example.short_link.common.live.LiveScope;
import com.example.short_link.common.live.LiveScopeReader;
import com.example.short_link.common.live.LiveTopic;
import com.example.short_link.note.application.read.NoteQueryService;
import com.example.short_link.note.domain.repository.NotePageReader;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class NoteRepliesLiveScope implements LiveScopeReader {

  private final NoteQueryService query;
  private final NotePageReader pages;

  @Override
  public LiveTopic.Scope scope() {
    return LiveTopic.Scope.NOTE;
  }

  @Override
  public Optional<LiveScope> open(long id, long viewerId, String clientIp) {
    return query
        .readable(viewerId, id)
        .map(note -> new LiveScope(note.getUserId(), note.getRoomId()));
  }

  @Override
  @Transactional(readOnly = true)
  public long latest(long id) {
    return pages.latestReply(id);
  }
}
