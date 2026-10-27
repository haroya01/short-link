package com.example.short_link.federation.application.inbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class InboxActivityTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String ALICE = "https://mastodon.example/users/alice";

  private static InboxStep<InboxActivity> parse(String body) {
    return InboxActivity.parse(JSON, body.getBytes(StandardCharsets.UTF_8));
  }

  @ParameterizedTest
  @ValueSource(strings = {"not json", "[1]", "\"Follow\"", "null", "", "{\"id\":"})
  void aBodyThatIsNotAJsonObjectIsMalformedJson(String body) {
    assertThat(parse(body)).isEqualTo(InboxStep.done(InboxOutcome.malformed("json")));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"type\":\"Follow\",\"actor\":\"" + ALICE + "\"}",
        "{\"id\":\"https://mastodon.example/1\",\"actor\":\"" + ALICE + "\"}",
        "{\"id\":\"https://mastodon.example/1\",\"type\":\"Follow\"}",
        "{\"id\":\" \",\"type\":\"Follow\",\"actor\":\"" + ALICE + "\"}",
        "{\"id\":\"https://mastodon.example/1\",\"type\":7,\"actor\":\"" + ALICE + "\"}",
        "{\"id\":\"https://mastodon.example/1\",\"type\":\"Follow\",\"actor\":{\"id\":7}}",
        "{\"id\":\"https://mastodon.example/1\",\"type\":\"Follow\",\"actor\":[\"" + ALICE + "\"]}"
      })
  void anActivityWithoutAnIdATypeAndAnActorIsMalformedFields(String body) {
    assertThat(parse(body)).isEqualTo(InboxStep.done(InboxOutcome.malformed("fields")));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "\"https://mastodon.example/users/alice\"",
        "{\"id\":\" https://mastodon.example/users/alice \",\"type\":\"Person\"}"
      })
  void theActorIsReadAsABareIdOrAnEmbeddedObject(String actor) {
    String body =
        """
        {"id":" https://mastodon.example/1 ","type":"Follow","actor":%s,"object":"https://kurl.me/ap/actors/a"}"""
            .formatted(actor);

    InboxStep<InboxActivity> parsed = parse(body);

    assertThat(parsed).isInstanceOf(InboxStep.Next.class);
    InboxActivity activity = ((InboxStep.Next<InboxActivity>) parsed).value();
    assertThat(activity.type()).isEqualTo("Follow");
    assertThat(activity.id()).isEqualTo("https://mastodon.example/1");
    assertThat(activity.actorUri()).isEqualTo(ALICE);
    assertThat(activity.object().asString()).isEqualTo("https://kurl.me/ap/actors/a");
    assertThat(activity.json()).isEqualTo(JSON.readTree(body));
  }
}
