package com.example.short_link.federation.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.federation.application.trends.RemoteSeen;
import com.example.short_link.federation.application.trends.RemoteTrendingFeed;
import com.example.short_link.federation.domain.SeenViewer;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = {RemoteTrendsController.class, FederationExceptionHandler.class})
class RemoteTrendsControllerTest {

  private static final String DEVICE = "6f1c2a3b-4d5e-4f60-8a71-92b3c4d5e6f7";
  private static final String BODY = "{\"uris\":[\"https://mastodon.social/users/a/statuses/1\"]}";

  @Autowired private MockMvc mvc;
  @MockitoBean private RemoteTrendingFeed feed;
  @MockitoBean private RemoteSeen seen;

  @Test
  void aSignedOutDeviceAndAMemberBothHandOverWhatTheySaw() throws Exception {
    mvc.perform(
            post("/api/v1/federation/seen")
                .header(RemoteTrendsController.DEVICE_HEADER, DEVICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isAccepted())
        .andExpect(content().string(""));
    mvc.perform(
            post("/api/v1/federation/seen")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7")
                .header(RemoteTrendsController.DEVICE_HEADER, DEVICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isAccepted());

    verify(seen)
        .record(SeenViewer.of(null, DEVICE), List.of("https://mastodon.social/users/a/statuses/1"));
    verify(seen)
        .record(SeenViewer.member(7L), List.of("https://mastodon.social/users/a/statuses/1"));
  }

  @Test
  void withoutASignInOrARandomDeviceIdNothingIsTaken() throws Exception {
    mvc.perform(
            post("/api/v1/federation/seen").contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("SEEN_WITHOUT_VIEWER"));
    mvc.perform(
            post("/api/v1/federation/seen")
                .header(RemoteTrendsController.DEVICE_HEADER, "device-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(seen);
  }

  @Test
  void theLatestTellsTheFeedWhoIsReadingAndShowsWhereTheSeenStart() throws Exception {
    when(feed.latest(null, SeenViewer.of(null, DEVICE), null, null, 20))
        .thenReturn(new RemoteTrendingFeed.Page(List.of(Map.of("uri", "a")), "s1.t1", null, 0));
    when(feed.latest(null, null, null, null, 20))
        .thenReturn(new RemoteTrendingFeed.Page(List.of(), null));

    mvc.perform(
            get("/api/v1/federation/latest").header(RemoteTrendsController.DEVICE_HEADER, DEVICE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.seenFrom").value(0))
        .andExpect(jsonPath("$.next").value("s1.t1"));
    mvc.perform(get("/api/v1/federation/latest").header(RemoteTrendsController.DEVICE_HEADER, "x"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.seenFrom").doesNotExist())
        .andExpect(jsonPath("$.gap").doesNotExist());

    verify(feed).latest(null, null, null, null, 20);
    verifyNoInteractions(seen);
  }

  record View(long id, String body) {}

  @Test
  void aKeptNoteReadsAsTheNoteWithItsUriBesideIt() throws Exception {
    when(feed.latest(null, null, null, null, 20))
        .thenReturn(
            new RemoteTrendingFeed.Page(
                List.of(
                    new RemoteTrendingFeed.KeptNote(
                        new View(30L, "hi"), "https://mastodon.social/users/a/statuses/1")),
                null));

    mvc.perform(get("/api/v1/federation/latest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(30))
        .andExpect(jsonPath("$.items[0].body").value("hi"))
        .andExpect(jsonPath("$.items[0].uri").value("https://mastodon.social/users/a/statuses/1"))
        .andExpect(jsonPath("$.items[0].note").doesNotExist());
  }
}
