package com.example.short_link.link.destination.presentation.request;

import jakarta.validation.constraints.Size;

public record LinkDestinationBlockedCountriesRequest(@Size(max = 255) String codes) {}
