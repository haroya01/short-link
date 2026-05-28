package com.example.short_link.link.domain;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

record CountryBlocklist(Set<String> countryCodes) {

  static CountryBlocklist fromCsv(String csv) {
    return new CountryBlocklist(parse(csv));
  }

  static String normalize(String csv) {
    Set<String> countryCodes = parse(csv);
    return countryCodes.isEmpty() ? null : String.join(",", countryCodes);
  }

  boolean contains(String countryCode) {
    if (countryCode == null) {
      return false;
    }
    return countryCodes.contains(countryCode.toUpperCase(Locale.ROOT));
  }

  private static Set<String> parse(String csv) {
    if (csv == null || csv.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(csv.split(","))
        .map(String::trim)
        .map(code -> code.toUpperCase(Locale.ROOT))
        .filter(code -> code.length() == 2)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
