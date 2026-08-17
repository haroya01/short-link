package com.example.short_link.admin.application.helper;

/** 운영자 경고 알림 카피 — 수신자 계정의 {@code user.locale} 로 렌더된다(미지원 값은 ko 폴백). */
public final class WarningCopy {

  private WarningCopy() {}

  public record Copy(String subtitle, String body) {}

  public static String subtitle(String locale) {
    return switch (normalize(locale)) {
      case "en" -> "Policy warning";
      case "ja" -> "運営者警告";
      case "vi" -> "Cảnh báo vi phạm";
      case "hi" -> "नीति चेतावनी";
      default -> "운영자 경고";
    };
  }

  public static Copy domainBlocked(String locale, String domain) {
    String body =
        switch (normalize(locale)) {
          case "en" ->
              "The domain "
                  + domain
                  + " was blocked for violating our policies. Your links pointing to it will no"
                  + " longer open. Repeated violations may suspend your account.";
          case "ja" ->
              "運営ポリシー違反のため "
                  + domain
                  + " ドメインをブロックしました。このドメインへのリンクは今後開けません。違反が繰り返されるとアカウントが停止される場合があります。";
          case "vi" ->
              "Tên miền "
                  + domain
                  + " đã bị chặn do vi phạm chính sách. Các liên kết của bạn trỏ tới tên miền này"
                  + " sẽ không mở được nữa. Tái phạm có thể khiến tài khoản bị đình chỉ.";
          case "hi" ->
              "नीति उल्लंघन के कारण "
                  + domain
                  + " डोमेन ब्लॉक कर दिया गया है। इस डोमेन की ओर जाने वाले आपके लिंक अब नहीं खुलेंगे।"
                  + " बार-बार उल्लंघन पर खाता निलंबित हो सकता है।";
          default ->
              "운영 정책 위반으로 "
                  + domain
                  + " 도메인이 차단되었어요. 이 도메인으로 연결되는 회원님의 링크는 더 이상 열리지 않아요. 반복 위반 시 계정이 정지될 수 있어요.";
        };
    return new Copy(subtitle(locale), body);
  }

  private static String normalize(String locale) {
    return locale == null ? "ko" : locale;
  }
}
