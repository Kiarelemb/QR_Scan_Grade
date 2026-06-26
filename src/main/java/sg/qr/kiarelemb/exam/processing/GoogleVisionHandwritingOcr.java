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

public final class GoogleVisionHandwritingOcr {
	private static final String VISION_URL = "https://vision.googleapis.com/v1/images:annotate";

	private GoogleVisionHandwritingOcr() {
	}

	public static Result recognizeJapaneseDocument(BufferedImage image, String apiKey) throws Exception {
		if (image == null) {
			throw new IllegalArgumentException("image must not be null");
		}
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalArgumentException("Google Vision API Key is required.");
		}
		String response = annotate(apiKey.trim(), image);
		List<String> lines = textLines(response);
		return new Result(response, lines);
	}

	private static String annotate(String apiKey, BufferedImage image) throws Exception {
		String imageBase64 = Base64.getEncoder().encodeToString(pngBytes(image));
		String json = """
				{
				  "requests": [
				    {
				      "image": {"content": "%s"},
				      "features": [{"type": "DOCUMENT_TEXT_DETECTION"}],
				      "imageContext": {"languageHints": ["ja"]}
				    }
				  ]
				}
				""".formatted(jsonEscape(imageBase64));
		return request(VISION_URL + "?key=" + urlEncode(apiKey), json);
	}

	private static byte[] pngBytes(BufferedImage image) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "png", output);
		return output.toByteArray();
	}

	private static String request(String url, String body) throws Exception {
		HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
		connection.setRequestMethod("POST");
		connection.setConnectTimeout(10_000);
		connection.setReadTimeout(60_000);
		connection.setRequestProperty("Accept", "application/json");
		connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
		connection.setRequestProperty("User-Agent", "QR-Scan-Grade/1.0");
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		connection.setDoOutput(true);
		connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
		try (OutputStream out = connection.getOutputStream()) {
			out.write(bytes);
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

	private static List<String> textLines(String json) {
		String text = fullText(json);
		if (text.isBlank()) {
			text = firstDescription(json);
		}
		if (text.isBlank()) {
			return List.of();
		}
		List<String> lines = new ArrayList<>();
		for (String line : text.split("\\R")) {
			String trimmed = line.trim();
			if (!trimmed.isEmpty()) {
				lines.add(trimmed);
			}
		}
		return lines;
	}

	private static String fullText(String json) {
		int fullTextIndex = json == null ? -1 : json.indexOf("\"fullTextAnnotation\"");
		return fullTextIndex < 0 ? "" : jsonString(json, "text", fullTextIndex);
	}

	private static String firstDescription(String json) {
		return jsonString(json, "description", 0);
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

	private static String jsonEscape(String text) {
		return text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static String urlEncode(String text) {
		return URLEncoder.encode(text == null ? "" : text, StandardCharsets.UTF_8);
	}

	public record Result(String rawResponse, List<String> lines) {
		public String text() {
			return String.join(System.lineSeparator(), lines);
		}
	}
}
