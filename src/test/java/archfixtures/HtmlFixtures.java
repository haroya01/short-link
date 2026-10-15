package archfixtures;

import org.jsoup.Jsoup;

// Outside every HTML reader package, so the jsoup confinement rule applies to it.
public final class HtmlFixtures {

  private HtmlFixtures() {}

  public static class ParsesHtmlOutsideAReader {
    public String run(String html) {
      return Jsoup.parse(html).text();
    }
  }
}
