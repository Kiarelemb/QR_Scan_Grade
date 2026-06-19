package sg.qr.kiarelemb.test;

import org.bytedeco.opencv.opencv_core.Rect;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Scanner;

public class FillTest {
	private static final File INPUT = new File("C:\\Users\\Kiare\\Downloads\\1777445474376..jpg");
	private static final File OUTPUT_DIR = new File("ans/fill-test");
	private static final Rect FILL_RECT = new Rect(190, 2391, 2191 - 190, 2712 - 2391);

	public static void main(String[] args) throws Exception {
		Files.createDirectories(OUTPUT_DIR.toPath());
		BufferedImage image = ImageIO.read(INPUT);
		if (image == null) {
			throw new IllegalArgumentException("Cannot read image: " + INPUT.getAbsolutePath());
		}
		System.out.println("Input image: " + INPUT.getAbsolutePath());
		System.out.println("Image size: " + image.getWidth() + " x " + image.getHeight());

		File cropFile = exportFillRegion(image);
		BaiduKeys keys = readBaiduKeys();
		if (keys.apiKey().isBlank() || keys.secretKey().isBlank()) {
			System.out.println("No Baidu OCR key provided. Skip OCR test.");
			return;
		}

		String token = fetchAccessToken(keys.apiKey(), keys.secretKey());
		String response = handwriting(token, cropFile);
		System.out.println("===== Baidu OCR raw response =====");
		System.out.println(response);
		System.out.println("===== words_result =====");
		printWords(response);
	}

	private static File exportFillRegion(BufferedImage image) throws Exception {
		Rect rect = clamp(FILL_RECT, image.getWidth(), image.getHeight());
		BufferedImage crop = image.getSubimage(rect.x(), rect.y(), rect.width(), rect.height());
		File cropFile = new File(OUTPUT_DIR, "fill_manual.png");
		ImageIO.write(crop, "png", cropFile);
		System.out.println("Fill crop: " + rectText(rect) + " -> " + cropFile.getAbsolutePath());

		BufferedImage overview = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = overview.createGraphics();
		try {
			g.drawImage(image, 0, 0, null);
			g.setColor(new Color(20, 145, 80));
			g.setStroke(new BasicStroke(Math.max(3f, image.getWidth() / 900f)));
			g.drawRect(rect.x(), rect.y(), rect.width(), rect.height());
			g.setFont(new Font("Microsoft YaHei", Font.BOLD, Math.max(28, image.getWidth() / 70)));
			g.drawString("fill region", rect.x(), Math.max(30, rect.y() - 12));
		} finally {
			g.dispose();
		}
		File overviewFile = new File(OUTPUT_DIR, "fill_manual_overview.png");
		ImageIO.write(overview, "png", overviewFile);
		System.out.println("Fill overview: " + overviewFile.getAbsolutePath());
		return cropFile;
	}

	private static BaiduKeys readBaiduKeys() {
		String apiKey = System.getenv("BAIDU_OCR_API_KEY");
		String secretKey = System.getenv("BAIDU_OCR_SECRET_KEY");
		if (apiKey != null && !apiKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
			return new BaiduKeys(apiKey.trim(), secretKey.trim());
		}
		Scanner scanner = new Scanner(System.in);
		System.out.print("Baidu OCR API Key: ");
		apiKey = scanner.nextLine();
		System.out.print("Baidu OCR Secret Key: ");
		secretKey = scanner.nextLine();
		return new BaiduKeys(apiKey == null ? "" : apiKey.trim(), secretKey == null ? "" : secretKey.trim());
	}

	private static String fetchAccessToken(String apiKey, String secretKey) throws Exception {
		String url = "https://aip.baidubce.com/oauth/2.0/token"
					 + "?grant_type=client_credentials"
					 + "&client_id=" + urlEncode(apiKey)
					 + "&client_secret=" + urlEncode(secretKey);
		String response = request(url, "GET", null);
		String token = jsonString(response, "access_token", 0);
		if (token.isBlank()) {
			throw new IllegalStateException("Failed to get access_token: " + response);
		}
		return token;
	}

	private static String handwriting(String accessToken, File imageFile) throws Exception {
		String imageBase64 = Base64.getEncoder().encodeToString(Files.readAllBytes(imageFile.toPath()));
		String body = "image=" + urlEncode(imageBase64)
					  + "&language_type=JAP"
					  + "&detect_direction=true"
					  + "&probability=true"
					  + "&recognize_granularity=big"
					  + "&eng_granularity=word";
		String url = "https://aip.baidubce.com/rest/2.0/ocr/v1/handwriting?access_token="
					 + urlEncode(accessToken);
		return request(url, "POST", body);
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

	private static void printWords(String json) {
		int index = 0;
		boolean found = false;
		while (true) {
			int key = json.indexOf("\"words\"", index);
			if (key < 0) {
				break;
			}
			String word = jsonString(json, "words", key);
			if (!word.isBlank()) {
				found = true;
				System.out.println(word);
			}
			index = key + 7;
		}
		if (!found) {
			System.out.println("No words field parsed.");
		}
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

	private static Rect clamp(Rect rect, int imageW, int imageH) {
		int x = Math.max(0, Math.min(rect.x(), imageW - 1));
		int y = Math.max(0, Math.min(rect.y(), imageH - 1));
		int right = Math.max(x + 1, Math.min(rect.x() + rect.width(), imageW));
		int bottom = Math.max(y + 1, Math.min(rect.y() + rect.height(), imageH));
		return new Rect(x, y, right - x, bottom - y);
	}

	private static String rectText(Rect rect) {
		return "x=" + rect.x() + ", y=" + rect.y() + ", w=" + rect.width() + ", h=" + rect.height();
	}

	private record BaiduKeys(String apiKey, String secretKey) {
	}
}
