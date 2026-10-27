package com.example.short_link.federation.presentation;

import com.example.short_link.federation.application.ActorDocument;
import com.example.short_link.federation.application.ActorDocuments;
import com.example.short_link.federation.application.signature.SigningKeys;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InstanceActorController {

  private final SigningKeys signingKeys;
  private final ActorDocuments documents;

  @GetMapping("/ap/instance")
  public ResponseEntity<ActorDocument> instance() {
    return ResponseEntity.ok()
        .contentType(ActivityPubMedia.ACTIVITY_JSON)
        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
        .body(documents.instance(signingKeys.instanceActor().getPublicKeyPem()));
  }
}
