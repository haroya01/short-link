package archfixtures.blogimport.application.feed;

import org.jsoup.Jsoup;

// Sits in a ..blogimport.application.feed.. package, where jsoup is allowed.
public final class FeedReaderFixtures {

  private FeedReaderFixtures() {}

  public static class ParsesHtmlInAFeedReader {
    public String run(String html) {
      return Jsoup.parse(html).text();
    }
  }
}
