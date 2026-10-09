package com.example.short_link.post.application.write;

import com.example.short_link.post.application.write.SetSeriesItemsCommand.Item;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.SeriesItemEntity;
import com.example.short_link.post.domain.SeriesItemType;
import com.example.short_link.post.domain.repository.SeriesItemRepository;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Clients that only know posts send the posts alone; the series' notes keep their places and the
// posts fill the post places in the order given, any extra ones joining at the end.
@Service
@RequiredArgsConstructor
public class SetSeriesPostsUseCase {

  private final SeriesOwnership seriesOwnership;
  private final SeriesItemRepository seriesItemRepository;
  private final SetSeriesItemsUseCase setSeriesItems;

  @Transactional
  public void execute(SetSeriesPostsCommand cmd) {
    SeriesEntity series = seriesOwnership.requireOwnedForUpdate(cmd.userId(), cmd.seriesId());
    Iterator<Long> posts = cmd.postIds().iterator();
    List<Item> items = new ArrayList<>();
    for (SeriesItemEntity existing : seriesItemRepository.findBySeriesId(series.getId())) {
      if (existing.getType() == SeriesItemType.NOTE) {
        items.add(new Item(SeriesItemType.NOTE, existing.getRefId()));
      } else if (posts.hasNext()) {
        items.add(new Item(SeriesItemType.POST, posts.next()));
      }
    }
    posts.forEachRemaining(id -> items.add(new Item(SeriesItemType.POST, id)));
    setSeriesItems.write(series, cmd.userId(), items);
  }
}
