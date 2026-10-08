package com.example.short_link.note.presentation.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.note.application.write.NoteDraft;
import java.util.List;
import org.junit.jupiter.api.Test;

class CreateNoteRequestTest {

  @Test
  void theDeviceSizeOfEachPictureReachesTheDraft() {
    CreateNoteRequest request =
        new CreateNoteRequest(
            "hi",
            List.of(
                new CreateNoteRequest.ImageRequest("k1", "a", 1200, 1600),
                new CreateNoteRequest.ImageRequest("k2", null, null, null)),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    assertThat(request.toDraft().images())
        .containsExactly(
            new NoteDraft.Image("k1", "a", 1200, 1600), new NoteDraft.Image("k2", null));
  }
}
