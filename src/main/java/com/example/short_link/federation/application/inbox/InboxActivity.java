package com.example.short_link.federation.application.inbox;

import static com.example.short_link.federation.application.ActivityStreams.idOf;
import static com.example.short_link.federation.application.ActivityStreams.text;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

record InboxActivity(JsonNode json, String type, String id, String actorUri) {

  static InboxStep<InboxActivity> parse(JsonMapper mapper, byte[] body) {
    JsonNode json;
    try {
      json = mapper.readTree(body);
    } catch (JacksonException malformed) {
      return InboxStep.done(InboxOutcome.malformed("json"));
    }
    if (json == null || !json.isObject()) {
      return InboxStep.done(InboxOutcome.malformed("json"));
    }
    String type = text(json.get("type"));
    String id = text(json.get("id"));
    String actorUri = idOf(json.get("actor"));
    if (type == null || id == null || actorUri == null) {
      return InboxStep.done(InboxOutcome.malformed("fields"));
    }
    return InboxStep.next(new InboxActivity(json, type, id, actorUri));
  }

  JsonNode object() {
    return json.get("object");
  }
}
