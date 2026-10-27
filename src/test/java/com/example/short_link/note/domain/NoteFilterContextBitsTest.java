package com.example.short_link.note.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.note.domain.NoteFilterEntity.Context;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class NoteFilterContextBitsTest {

  @Test
  void eachContextKeepsTheBitAlreadyStoredInNoteFilterContexts() {
    Map<Context, Integer> bits =
        Arrays.stream(Context.values())
            .collect(Collectors.toMap(Function.identity(), Context::bit));

    assertThat(bits)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                Context.HOME, 1,
                Context.PUBLIC, 2,
                Context.THREAD, 4,
                Context.ACCOUNT, 8,
                Context.NOTIFICATIONS, 16));
  }
}
