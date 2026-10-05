package com.example.short_link.federation.application.signature;

import java.security.PrivateKey;

public record Signer(String keyId, PrivateKey privateKey) {}
