package archfixtures.config;

import java.time.Clock;

public class SystemClockConfig {
  public Clock clock() {
    return Clock.systemUTC();
  }
}
