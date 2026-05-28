package com.example.short_link.profile.application.read;

import java.util.List;

public interface PublicHandleReader {
  List<String> findPage(int page, int size);
}
