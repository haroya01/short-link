package com.example.short_link.federation.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.federation.application.trends.LocalSeen;
import com.example.short_link.federation.application.trends.RemoteForYou;
import com.example.short_link.federation.application.trends.RemoteSeen;
import com.example.short_link.federation.application.trends.RemoteTrendingFeed;
import com.example.short_link.federation.domain.SeenViewer;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
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
  @MockitoBean private RemoteForYou forYou;
  @MockitoBean private LocalSeen local;

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
        .record(
            SeenViewer.of(null, DEVICE),
            List.of("https://mastodon.social/users/a/statuses/1"),
            null,
            null);
    verify(seen)
        .record(
            SeenViewer.member(7L),
            List.of("https://mastodon.social/users/a/statuses/1"),
            null,
            null);
  }

  @Test
  void howLongEachPostStayedAndWhichFeedShowedItGoAlong() throws Exception {
    mvc.perform(
            post("/api/v1/federation/seen")
                .header(RemoteTrendsController.DEVICE_HEADER, DEVICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"uris":["https://a.example/users/a/statuses/1"],"feed":"for-you",
                     "dwell":[{"uri":"https://a.example/users/a/statuses/1","ms":12000},
                              {"uri":"https://a.example/users/a/statuses/2"},null]}"""))
        .andExpect(status().isAccepted());

    verify(seen)
        .record(
            SeenViewer.of(null, DEVICE),
            List.of("https://a.example/users/a/statuses/1"),
            List.of(new RemoteSeen.Dwell("https://a.example/users/a/statuses/1", 12_000)),
            "for-you");
  }

  @Test
  void forYouTellsTheRankingWhoIsReadingInWhichLanguagesFromWhere() throws Exception {
    when(feed.latest(null, null, null, null, 20))
        .thenReturn(new RemoteTrendingFeed.Page(List.of(), null));
    when(forYou.page(7L, SeenViewer.member(7L), "ko-KR,ko;q=0.9", "f1.20", 10, false))
        .thenReturn(new RemoteTrendingFeed.Page(List.of(Map.of("uri", "b")), "f1.30", null, 3));
    when(forYou.page(null, SeenViewer.of(null, DEVICE), null, null, 20, false))
        .thenReturn(new RemoteTrendingFeed.Page(List.of(), null));

    mvc.perform(
            get("/api/v1/federation/for-you?limit=10&cursor=f1.20")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "ko-KR,ko;q=0.9"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].uri").value("b"))
        .andExpect(jsonPath("$.next").value("f1.30"))
        .andExpect(jsonPath("$.seenFrom").value(3))
        .andExpect(jsonPath("$.gap").doesNotExist());
    mvc.perform(
            get("/api/v1/federation/for-you").header(RemoteTrendsController.DEVICE_HEADER, DEVICE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.next").isEmpty())
        .andExpect(jsonPath("$.seenFrom").doesNotExist());
    mvc.perform(get("/api/v1/federation/latest")).andExpect(status().isOk());

    verify(feed).latest(null, null, null, null, 20);
  }

  @Test
  void theHomeAsksForYouToLeaveOutWhatWasSeenAndHandsOverKurlRowsToo() throws Exception {
    when(forYou.page(7L, SeenViewer.member(7L), null, null, 20, true))
        .thenReturn(new RemoteTrendingFeed.Page(List.of(), null));
    List<String> sent =
        List.of("kurl:note:12", "kurl:post:3", "https://a.example/users/a/statuses/1");

    mvc.perform(
            get("/api/v1/federation/for-you?seen=hide")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/federation/seen")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"uris\":[\"kurl:note:12\",\"kurl:post:3\","
                        + "\"https://a.example/users/a/statuses/1\"]}"))
        .andExpect(status().isAccepted());

    verify(forYou).page(7L, SeenViewer.member(7L), null, null, 20, true);
    verify(local).record(SeenViewer.member(7L), sent);
    verify(seen).record(SeenViewer.member(7L), sent, null, null);
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

    verifyNoInteractions(seen, local);
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
