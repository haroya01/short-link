package com.example.short_link.note.domain.repository;

import com.example.short_link.common.reply.ReplyPreview;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteStats;
import com.example.short_link.note.domain.NoteViewerMarks;
import com.example.short_link.note.domain.SelfReply;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface NotePageReader {

  List<NoteEntity> heardAmong(Collection<Long> ids, Long viewerId);

  List<ReplyPreview> latestReplies(Collection<Long> noteIds, Long viewerId, int perNote);

  Map<Long, List<String>> tagsOf(Collection<Long> noteIds);

  List<SelfReply> selfReplies(Collection<Long> rootIds, int depth);

  Map<Long, NoteStats> stats(Collection<Long> noteIds);

  NoteViewerMarks viewerMarks(Long userId, Collection<Long> noteIds);
}
