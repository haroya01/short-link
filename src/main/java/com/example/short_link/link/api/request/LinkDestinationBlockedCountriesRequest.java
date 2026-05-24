package com.example.short_link.link.api.request;

import jakarta.validation.constraints.Size;

public record LinkDestinationBlockedCountriesRequest(@Size(max = 255) String codes) {}
