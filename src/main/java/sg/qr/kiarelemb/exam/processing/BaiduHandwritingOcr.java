package sg.qr.kiarelemb.exam.processing;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class BaiduHandwritingOcr {
	private static final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";
	private static final String HANDWRITING_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/handwriting";

	private BaiduHandwritingOcr() {
	}

	public static Result recognizeJapaneseAccurate(BufferedImage image, String apiKey, String secretKey) throws Exception {
		return recognizeJapaneseHandwriting(image, apiKey, secretKey);
	}

	public static Result recognizeJapaneseHandwriting(BufferedImage image, String apiKey, String secretKey) throws Exception {
		if (image == null) {
			throw new IllegalArgumentException("image must not be null");
		}
		if (apiKey == null || apiKey.isBlank() || secretKey == null || secretKey.isBlank()) {
			throw new IllegalArgumentException("Baidu OCR API Key and Secret Key are required.");
		}
		String token = fetchAccessToken(apiKey.trim(), secretKey.trim());
		String response = handwriting(token, image);
		return new Result(response, words(response));
	}

	private static String fetchAccessToken(String apiKey, String secretKey) throws Exception {
		String url = TOKEN_URL
					 + "?grant_type=client_credentials"
					 + "&client_id=" + urlEncode(apiKey)
					 + "&client_secret=" + urlEncode(secretKey);
		String response = request(url, "GET", null);
		String token = jsonString(response, "access_token", 0);
		if (token.isBlank()) {
			throw new IllegalStateException("Failed to get Baidu access_token: " + response);
		}
		return token;
	}

	private static String handwriting(String accessToken, BufferedImage image) throws Exception {
		String imageBase64 = Base64.getEncoder().encodeToString(pngBytes(image));
		String body = "image=" + urlEncode(imageBase64)
					  + "&language_type=JAP"
					  + "&detect_direction=true"
					  + "&probability=true"
					  + "&recognize_granularity=big"
					  + "&eng_granularity=word";
		return request(HANDWRITING_URL + "?access_token=" + urlEncode(accessToken), "POST", body);
	}

	private static byte[] pngBytes(BufferedImage image) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "png", output);
		return output.toByteArray();
	}

	private static String request(String url, String method, String body) throws Exception {
		HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
		connection.setRequestMethod(method);
		connection.setConnectTimeout(10_000);
		connection.setReadTimeout(60_000);
		connection.setRequestProperty("Accept", "application/json");
		connection.setRequestProperty("User-Agent", "QR-Scan-Grade/1.0");
		if (body != null) {
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			connection.setDoOutput(true);
			connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
			try (OutputStream out = connection.getOutputStream()) {
				out.write(bytes);
			}
		}
		int status = connection.getResponseCode();
		InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
		String response;
		try (InputStream in = stream) {
			response = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} finally {
			connection.disconnect();
		}
		return status >= 200 && status < 300 ? response : "HTTP " + status + ": " + response;
	}

	public static List<String> words(String json) {
		List<String> words = new ArrayList<>();
		int index = 0;
		while (json != null) {
			int key = json.indexOf("\"words\"", index);
			if (key < 0) {
				break;
			}
			String word = jsonString(json, "words", key);
			if (!word.isBlank()) {
				words.add(word);
			}
			index = key + 7;
		}
		return words;
	}

	private static String jsonString(String json, String key, int fromIndex) {
		if (json == null || json.isBlank()) {
			return "";
		}
		String marker = "\"" + key + "\"";
		int keyIndex = json.indexOf(marker, Math.max(0, fromIndex));
		if (keyIndex < 0) {
			return "";
		}
		int colon = json.indexOf(':', keyIndex + marker.length());
		int quote = json.indexOf('"', colon + 1);
		if (colon < 0 || quote < 0) {
			return "";
		}
		StringBuilder builder = new StringBuilder();
		boolean escaping = false;
		for (int i = quote + 1; i < json.length(); i++) {
			char ch = json.charAt(i);
			if (escaping) {
				builder.append(switch (ch) {
					case 'n' -> '\n';
					case 'r' -> '\r';
					case 't' -> '\t';
					case '"', '\\', '/' -> ch;
					default -> ch;
				});
				escaping = false;
			} else if (ch == '\\') {
				escaping = true;
			} else if (ch == '"') {
				return builder.toString();
			} else {
				builder.append(ch);
			}
		}
		return builder.toString();
	}

	private static String urlEncode(String text) {
		return URLEncoder.encode(text == null ? "" : text, StandardCharsets.UTF_8);
	}

	public record Result(String rawResponse, List<String> words) {
		public String text() {
			return String.join(System.lineSeparator(), words);
		}
	}
}
