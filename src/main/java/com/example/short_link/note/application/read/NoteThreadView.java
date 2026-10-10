package com.example.short_link.note.application.read;

import java.util.List;

// continuation is the author's own parts under the note, in order; replies are everyone else's.
// hiddenReplyCount counts what hiddenReplies would list for the same viewer. viewerCanModerate:
// the viewer wrote the thread's first note, so may hide or remove others' replies in it.
public record NoteThreadView(
    NoteView note,
    NoteView parent,
    List<NoteView> replies,
    List<NoteView> continuation,
    NoteSeriesNavView series,
    int hiddenReplyCount,
    boolean viewerCanModerate) {}
