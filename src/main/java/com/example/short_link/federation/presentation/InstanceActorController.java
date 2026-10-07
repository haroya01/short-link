package com.example.short_link.federation.presentation;

import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.presentation.response.ActorResponse;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InstanceActorController {

  private final SigningKeys signingKeys;
  private final FederationUrls urls;

  @GetMapping("/ap/instance")
  public ResponseEntity<ActorResponse> instance() {
    String id = urls.instance();
    return ResponseEntity.ok()
        .contentType(ActivityPubMedia.ACTIVITY_JSON)
        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
        .body(
            new ActorResponse(
                List.of(ActivityPubMedia.CONTEXT, "https://w3id.org/security/v1"),
                id,
                "Application",
                urls.domain(),
                urls.domain(),
                null,
                null,
                urls.sharedInbox(),
                id + "/outbox",
                null,
                null,
                null,
                new ActorResponse.Endpoints(urls.sharedInbox()),
                true,
                false,
                null,
                new ActorResponse.PublicKey(
                    urls.instanceKey(), id, signingKeys.instanceActor().getPublicKeyPem())));
  }
}
