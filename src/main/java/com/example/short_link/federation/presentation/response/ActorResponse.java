package com.example.short_link.federation.presentation.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActorResponse(
    @JsonProperty("@context") List<Object> context,
    String id,
    String type,
    String preferredUsername,
    String name,
    String summary,
    String url,
    String inbox,
    String outbox,
    String followers,
    String following,
    String featured,
    List<PropertyValue> attachment,
    Endpoints endpoints,
    boolean manuallyApprovesFollowers,
    boolean discoverable,
    Image icon,
    PublicKey publicKey) {

  public record Endpoints(String sharedInbox) {}

  public record PropertyValue(String type, String name, String value) {}

  public record Image(String type, String url) {}

  public record PublicKey(String id, String owner, String publicKeyPem) {}
}
