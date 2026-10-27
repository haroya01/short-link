package com.example.short_link.note.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PageAskTest {

  @Test
  void aPageBeforeTheFirstIsTheFirstAndASizeStaysBetweenOneAndFifty() {
    assertThat(new PageAsk(-3, 20)).isEqualTo(new PageAsk(0, 20));
    assertThat(new PageAsk(2, 0)).isEqualTo(new PageAsk(2, 1));
    assertThat(new PageAsk(2, -5)).isEqualTo(new PageAsk(2, 1));
    assertThat(new PageAsk(2, 10_000)).isEqualTo(new PageAsk(2, PageAsk.MAX_SIZE));
    assertThat(new PageAsk(2, 50).size()).isEqualTo(50);
  }

  @Test
  void aPageSkipsTheEarlierPagesAndReadsOneRowMoreThanItShows() {
    List<List<Integer>> asked = new ArrayList<>();

    new PageAsk(3, 20)
        .fetch(
            (offset, limit) -> {
              asked.add(List.of(offset, limit));
              return List.of();
            });

    assertThat(asked).containsExactly(List.of(60, 21));
  }

  @Test
  void theExtraRowSaysAnotherPageFollowsAndIsNotShown() {
    PageAsk ask = new PageAsk(0, 2);

    PageAsk.Rows<Integer> full = ask.fetch((offset, limit) -> rows(limit));
    PageAsk.Rows<Integer> exact = ask.fetch((offset, limit) -> rows(2));
    PageAsk.Rows<Integer> partial = ask.fetch((offset, limit) -> rows(1));

    assertThat(full.current()).containsExactly(0, 1);
    assertThat(full.hasNext()).isTrue();
    assertThat(exact.current()).containsExactly(0, 1);
    assertThat(exact.hasNext()).isFalse();
    assertThat(partial.current()).containsExactly(0);
    assertThat(partial.hasNext()).isFalse();
  }

  private static List<Integer> rows(int count) {
    return IntStream.range(0, count).boxed().toList();
  }
}
