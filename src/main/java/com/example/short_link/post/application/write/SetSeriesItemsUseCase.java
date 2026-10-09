package com.example.short_link.post.application.write;

import com.example.short_link.post.application.write.SetSeriesItemsCommand.Item;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.SeriesItemEntity;
import com.example.short_link.post.domain.SeriesItemType;
import com.example.short_link.post.domain.SeriesNote;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesItemReader;
import com.example.short_link.post.domain.repository.SeriesItemRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The single writer of a series' membership: posts.series_id and series_order mirror the post
// items so the post feeds keep working, and series_item holds the order of posts and notes
// together.
@Service
@RequiredArgsConstructor
public class SetSeriesItemsUseCase {

  private final SeriesOwnership seriesOwnership;
  private final PostRepository postRepository;
  private final SeriesItemRepository seriesItemRepository;
  private final SeriesItemReader seriesItemReader;

  @Transactional
  public void execute(SetSeriesItemsCommand cmd) {
    SeriesEntity series = seriesOwnership.requireOwnedForUpdate(cmd.userId(), cmd.seriesId());
    write(series, cmd.userId(), cmd.items());
  }

  void write(SeriesEntity series, Long userId, List<Item> items) {
    List<Long> postIds = idsOf(items, SeriesItemType.POST);
    // Lock the complete affected set before changing any row, regardless of requested order.
    List<PostEntity> affected =
        postRepository.findSeriesMembersAndRequestedForUpdate(series.getId(), postIds);
    Map<Long, PostEntity> posts = new HashMap<>();
    for (PostEntity post : affected) posts.put(post.getId(), post);
    for (Long postId : postIds) {
      PostEntity post = posts.get(postId);
      if (post == null) {
        throw new PostException(PostErrorCode.POST_NOT_FOUND, postId);
      }
      if (!post.isOwnedBy(userId)) {
        throw new PostException(PostErrorCode.PERMISSION_DENIED).with("postId", postId);
      }
    }
    requireOwnSharedNotes(userId, idsOf(items, SeriesItemType.NOTE));

    Set<Long> keep = new HashSet<>(postIds);
    for (PostEntity existing : affected) {
      if (!keep.contains(existing.getId())) {
        existing.clearSeries();
        postRepository.save(existing);
      }
    }

    List<SeriesItemEntity> rows = new ArrayList<>(items.size());
    for (int position = 0; position < items.size(); position++) {
      Item item = items.get(position);
      if (item.type() == SeriesItemType.POST) {
        PostEntity post = posts.get(item.id());
        post.assignToSeries(series.getId(), position);
        postRepository.save(post);
      }
      rows.add(new SeriesItemEntity(series.getId(), item.type(), item.id(), position));
    }
    seriesItemRepository.replace(series.getId(), rows);
  }

  private void requireOwnSharedNotes(Long userId, List<Long> noteIds) {
    Map<Long, SeriesNote> notes = seriesItemReader.notes(noteIds);
    for (Long noteId : noteIds) {
      SeriesNote note = notes.get(noteId);
      if (note == null) {
        throw new PostException(PostErrorCode.SERIES_NOTE_NOT_FOUND, noteId);
      }
      if (!note.authorId().equals(userId)) {
        throw new PostException(PostErrorCode.PERMISSION_DENIED).with("noteId", noteId);
      }
      if (!note.shared()) {
        throw new PostException(PostErrorCode.SERIES_NOTE_NOT_SHARED, noteId);
      }
    }
  }

  private static List<Long> idsOf(List<Item> items, SeriesItemType type) {
    return items.stream().filter(i -> i.type() == type).map(Item::id).toList();
  }
}
