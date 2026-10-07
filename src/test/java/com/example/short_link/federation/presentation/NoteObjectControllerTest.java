package com.example.short_link.federation.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.common.note.NoteSnapshotReader;
import com.example.short_link.common.note.NoteSnapshotReader.NoteSnapshot;
import com.example.short_link.federation.application.FederationActorService;
import com.example.short_link.federation.application.FederationProperties;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.LocalActor;
import com.example.short_link.federation.application.NoteDocuments;
import com.example.short_link.federation.application.RemoteParents;
import com.example.short_link.federation.domain.FederationUser;
import com.example.short_link.testsupport.KurlWebMvcTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = NoteObjectController.class)
@Import({FederationUrls.class, NoteDocuments.class, NoteObjectControllerTest.Props.class})
class NoteObjectControllerTest {

  @TestConfiguration
  static class Props {
    @Bean
    FederationProperties federationProperties() {
      return new FederationProperties("https://kurl.me", "https://blog.kurl.me");
    }
  }

  private static final NoteSnapshot NOTE =
      new NoteSnapshot(
          42L,
          7L,
          "yuki",
          "hi",
          Instant.parse("2026-10-06T00:00:00Z"),
          null,
          null,
          null,
          List.of());

  @Autowired private MockMvc mvc;
  @MockitoBean private NoteSnapshotReader notes;
  @MockitoBean private FederationActorService actors;
  @MockitoBean private RemoteParents remoteParents;

  @Test
  void remoteServersGetTheNoteAndBrowsersGoToItsPage() throws Exception {
    when(notes.find(42L)).thenReturn(Optional.of(NOTE));
    when(actors.byUsername("yuki"))
        .thenReturn(
            Optional.of(new LocalActor(new FederationUser(7L, "yuki", null, null), "pid", "PUB")));

    mvc.perform(get("/ap/notes/42").header("Accept", "application/activity+json"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/activity+json"))
        .andExpect(jsonPath("$['@context']").value("https://www.w3.org/ns/activitystreams"))
        .andExpect(jsonPath("$.id").value("https://kurl.me/ap/notes/42"))
        .andExpect(jsonPath("$.attributedTo").value("https://kurl.me/ap/actors/pid"));
    mvc.perform(get("/ap/notes/42").header("Accept", "text/html"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://blog.kurl.me/@yuki/notes/42"));
  }

  @Test
  void aMissingNoteOrAnOptedOutAuthorIs404() throws Exception {
    when(notes.find(1L)).thenReturn(Optional.empty());
    when(notes.find(42L)).thenReturn(Optional.of(NOTE));
    when(actors.byUsername("yuki")).thenReturn(Optional.empty());

    mvc.perform(get("/ap/notes/1")).andExpect(status().isNotFound());
    mvc.perform(get("/ap/notes/42")).andExpect(status().isNotFound());
  }
}
