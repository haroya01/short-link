package com.example.short_link.federation.application;

import java.time.Instant;

public record RemoteMuteView(RemoteAccountView account, Instant expiresAt, boolean notifications) {}
