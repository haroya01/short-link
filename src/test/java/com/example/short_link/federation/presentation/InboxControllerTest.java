package com.example.short_link.federation.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.federation.application.inbox.InboxMessage;
import com.example.short_link.federation.application.inbox.InboxOutcome;
import com.example.short_link.federation.application.inbox.InboxService;
import com.example.short_link.testsupport.KurlWebMvcTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = InboxController.class)
class InboxControllerTest {

  private static final String FOLLOW = "{\"type\":\"Follow\"}";

  @Autowired private MockMvc mvc;
  @MockitoBean private InboxService inbox;

  @Test
  void theSharedInboxHandsOverTheRawRequestAndAcknowledgesWith202() throws Exception {
    when(inbox.receive(any(), isNull()))
        .thenReturn(new InboxOutcome(InboxOutcome.Kind.ACCEPTED, "follow"));

    mvc.perform(
            post("/ap/inbox")
                .contentType("application/activity+json")
                .header("Signature", "keyId=\"k\",signature=\"s\"")
                .header("Date", "Mon, 05 Oct 2026 19:00:00 GMT")
                .content(FOLLOW))
        .andExpect(status().isAccepted())
        .andExpect(content().string(""));

    ArgumentCaptor<InboxMessage> request = ArgumentCaptor.forClass(InboxMessage.class);
    verify(inbox).receive(request.capture(), isNull());
    assertThat(request.getValue().path()).isEqualTo("/ap/inbox");
    assertThat(request.getValue().query()).isNull();
    assertThat(new String(request.getValue().body(), StandardCharsets.UTF_8)).isEqualTo(FOLLOW);
    assertThat(request.getValue().header("Signature")).isEqualTo("keyId=\"k\",signature=\"s\"");
    assertThat(request.getValue().header("date")).isEqualTo("Mon, 05 Oct 2026 19:00:00 GMT");
  }

  @Test
  void eachOutcomeMapsToTheStatusRemoteServersExpect() throws Exception {
    when(inbox.receive(any(), eq("pid123")))
        .thenReturn(
            new InboxOutcome(InboxOutcome.Kind.IGNORED, "unsupported"),
            new InboxOutcome(InboxOutcome.Kind.UNAUTHORIZED, "digest"),
            new InboxOutcome(InboxOutcome.Kind.MALFORMED, "json"),
            new InboxOutcome(InboxOutcome.Kind.NOT_FOUND, "inbox"));

    mvc.perform(post("/ap/actors/pid123/inbox").content(FOLLOW)).andExpect(status().isAccepted());
    mvc.perform(post("/ap/actors/pid123/inbox").content(FOLLOW))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/ap/actors/pid123/inbox").content(FOLLOW)).andExpect(status().isBadRequest());
    mvc.perform(post("/ap/actors/pid123/inbox").content(FOLLOW)).andExpect(status().isNotFound());
  }

  @Test
  void anEmptyBodyReachesTheServiceAsNoBytes() throws Exception {
    when(inbox.receive(any(), isNull()))
        .thenReturn(new InboxOutcome(InboxOutcome.Kind.MALFORMED, "json"));

    mvc.perform(post("/ap/inbox?x=1")).andExpect(status().isBadRequest());

    ArgumentCaptor<InboxMessage> request = ArgumentCaptor.forClass(InboxMessage.class);
    verify(inbox).receive(request.capture(), isNull());
    assertThat(request.getValue().body()).isEmpty();
    assertThat(request.getValue().query()).isEqualTo("x=1");
  }
}
