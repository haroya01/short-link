package com.example.short_link.live.application;

import java.io.IOException;

public interface LiveChannel {

  void send(LiveEvent event) throws IOException;

  void close();
}
