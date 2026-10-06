package com.example.short_link.federation.application;

import com.example.short_link.federation.domain.FederationUser;

public record LocalActor(FederationUser user, String publicId, String publicKeyPem) {}
