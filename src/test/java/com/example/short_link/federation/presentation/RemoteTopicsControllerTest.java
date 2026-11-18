package com.example.short_link.federation.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.federation.application.trends.RemoteTopicFeed;
import com.example.short_link.federation.application.trends.TopicAsk;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = {RemoteTopicsController.class, FederationExceptionHandler.class})
class RemoteTopicsControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private RemoteTopicFeed feed;

  @Test
  void anyoneReadsTheTopicsInTheirLanguageWithTwentyUnlessTheyAskOtherwise() throws Exception {
    when(feed.topics(null, "ja-JP,ja;q=0.9", 20))
        .thenReturn(new RemoteTopicFeed.TopicList(List.of()));
    when(feed.topics(7L, null, 40)).thenReturn(new RemoteTopicFeed.TopicList(List.of()));

    mvc.perform(
            get("/api/v1/federation/topics").header(HttpHeaders.ACCEPT_LANGUAGE, "ja-JP,ja;q=0.9"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray());
    mvc.perform(
            get("/api/v1/federation/topics?limit=40")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isOk());

    verify(feed).topics(null, "ja-JP,ja;q=0.9", 20);
    verify(feed).topics(7L, null, 40);
  }

  @Test
  void aTopicsPostsAreAskedByItsTagOrItsAddressByAnyone() throws Exception {
    when(feed.posts(null, "ko", new TopicAsk("소풍", null), null, 20))
        .thenReturn(
            new RemoteTopicFeed.TopicPosts(null, List.of(Map.of("uri", "https://x/1")), "t20"));
    when(feed.posts(7L, null, new TopicAsk(null, "https://news.example/a?b=1&c=2"), "r90", 5))
        .thenReturn(new RemoteTopicFeed.TopicPosts(null, List.of(), null));

    mvc.perform(
            get("/api/v1/federation/topics/posts")
                .param("tag", "소풍")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "ko"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.topic").isEmpty())
        .andExpect(jsonPath("$.items[0].uri").value("https://x/1"))
        .andExpect(jsonPath("$.next").value("t20"));
    mvc.perform(
            get("/api/v1/federation/topics/posts")
                .param("url", "https://news.example/a?b=1&c=2")
                .param("cursor", "r90")
                .param("limit", "5")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.next").isEmpty());
  }

  @Test
  void neitherOrBothOfTagAndAddressIsRefusedBeforeAnythingIsRead() throws Exception {
    mvc.perform(get("/api/v1/federation/topics/posts"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TOPIC_ASK_INVALID"));
    mvc.perform(get("/api/v1/federation/topics/posts").param("tag", " ").param("url", ""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TOPIC_ASK_INVALID"));
    mvc.perform(
            get("/api/v1/federation/topics/posts")
                .param("tag", "소풍")
                .param("url", "https://news.example/a")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TOPIC_ASK_INVALID"));

    verifyNoInteractions(feed);
  }
}
