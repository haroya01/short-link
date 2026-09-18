package com.example.short_link.link.presentation.request;

import com.example.short_link.link.domain.ShortCode;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record FavoriteOrderRequest(@NotNull List<@NotNull ShortCode> shortCodes) {}
