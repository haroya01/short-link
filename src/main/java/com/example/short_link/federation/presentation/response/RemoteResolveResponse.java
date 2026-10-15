package com.example.short_link.federation.presentation.response;

import com.example.short_link.federation.application.RemoteAccountView;
import com.example.short_link.federation.application.RemoteResolution;

public record RemoteResolveResponse(RemoteAccountView account, Object note) {

  public static RemoteResolveResponse of(RemoteResolution.Resolved resolved) {
    return new RemoteResolveResponse(resolved.account(), resolved.note());
  }
}
