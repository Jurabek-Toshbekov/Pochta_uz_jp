//package japan.it.telegram_bot_dars.service;
//
//
//import java.io.OutputStream;
//import java.net.HttpURLConnection;
//import java.net.URL;
//import java.nio.charset.StandardCharsets;
//
//public class GoogleAnalyticsUploader {
//
//    public void sendToGoogleAnalytics(String measurementId, String apiSecret, String clientId, String eventName, String postType) {
//        try {
//            // GA4 Measurement Protocol URL
//            String url = "https://yapon.uz/uzb_jp_elon_bot.html";
////            String url = "https://www.google-analytics.com/mp/collect?measurement_id=" + measurementId + "&api_secret=" + apiSecret;
//
//            // JSON ma'lumotini tayyorlash
//            String jsonData = "{"
//                    + "\"client_id\": \"" + clientId + "\","
//                    + "\"events\": [{"
//                    + "\"name\": \"" + eventName + "\","
//                    + "\"params\": {"
//                    + "\"post_type\": \"" + postType + "\""
//                    + "}"
//                    + "}]"
//                    + "}";
//
//            // URL yaratish
//            URL urlObj = new URL(url);
//            HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
//            connection.setRequestMethod("POST");
//            connection.setDoOutput(true);
//            connection.setRequestProperty("Content-Type", "application/json");
//
//            // JSON ma'lumotlarini so'rovga yozish
//            try (OutputStream os = connection.getOutputStream()) {
//                byte[] input = jsonData.getBytes(StandardCharsets.UTF_8);
//                os.write(input, 0, input.length);
//            }
//
//            // Javobni olish
//            int responseCode = connection.getResponseCode();
//            if (responseCode == HttpURLConnection.HTTP_OK) {
//                System.out.println("Ma'lumot yuborildi!");
//            } else {
//                System.out.println("Ma'lumot yuborishda xato: " + responseCode);
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//
//}
