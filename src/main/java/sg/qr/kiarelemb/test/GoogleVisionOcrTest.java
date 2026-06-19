package sg.qr.kiarelemb.test;

import sg.qr.kiarelemb.grading.pipeline.GoogleVisionOcrRecognizer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.Console;
import java.io.File;
import java.util.Scanner;

public final class GoogleVisionOcrTest {
	private static final String DEFAULT_IMAGE =
			"F:\\自编资料\\考试\\26.6.17周测\\答卷扫描\\6.17答题卡扫描. - 0001.png";

	private GoogleVisionOcrTest() {
	}

	public static void main(String[] args) throws Exception {
		String imagePath = args.length >= 1 ? args[0] : DEFAULT_IMAGE;
		BufferedImage image = ImageIO.read(new File(imagePath));
		if (image == null) {
			throw new IllegalArgumentException("Cannot read image: " + imagePath);
		}
		BufferedImage target = cropIfRequested(image, args);
		String apiKey = apiKey(args);

		GoogleVisionOcrRecognizer.Result result =
				GoogleVisionOcrRecognizer.recognizeJapaneseDocument(target, apiKey);
		System.out.println("===== Google Vision OCR raw response =====");
		System.out.println(result.rawResponse());
		System.out.println("===== Google Vision OCR lines =====");
		for (int i = 0; i < result.lines().size(); i++) {
			System.out.println((i + 1) + ": " + result.lines().get(i));
		}
		System.out.println("===== text =====");
		System.out.println(result.text());
	}

	private static BufferedImage cropIfRequested(BufferedImage image, String[] args) {
		int x;
		int y;
		int w;
		int h;
		if (args.length >= 5) {
			x = Integer.parseInt(args[1]);
			y = Integer.parseInt(args[2]);
			w = Integer.parseInt(args[3]);
			h = Integer.parseInt(args[4]);
		} else {
			x = 262;
			y = 1968;
			w = 1600;
			h = 729;
		}
		x = Math.max(0, Math.min(x, image.getWidth() - 1));
		y = Math.max(0, Math.min(y, image.getHeight() - 1));
		int right = Math.max(x + 1, Math.min(x + w, image.getWidth()));
		int bottom = Math.max(y + 1, Math.min(y + h, image.getHeight()));
		System.out.println("Image: " + image.getWidth() + "x" + image.getHeight());
		System.out.println("Crop: " + x + "," + y + "," + (right - x) + "," + (bottom - y));
		return image.getSubimage(x, y, right - x, bottom - y);
	}

	private static String apiKey(String[] args) {
		if (args.length >= 6 && !args[5].isBlank()) {
			return args[5].trim();
		}
		String env = System.getenv("GOOGLE_VISION_API_KEY");
		if (env != null && !env.isBlank()) {
			return env.trim();
		}
		Console console = System.console();
		if (console != null) {
			char[] chars = console.readPassword("Google Vision API Key: ");
			return chars == null ? "" : new String(chars).trim();
		}
		System.out.print("Google Vision API Key: ");
		return new Scanner(System.in).nextLine().trim();
	}
}
