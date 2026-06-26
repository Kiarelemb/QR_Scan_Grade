package sg.qr.kiarelemb.exam.processing;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;

public final class BinaryRegionAnalyzer {
	private BinaryRegionAnalyzer() {
	}

	public static Rect clamp(Rect rect, int imgW, int imgH) {
		if (rect == null) {
			return null;
		}
		int x = Math.max(0, Math.min(rect.x(), imgW - 1));
		int y = Math.max(0, Math.min(rect.y(), imgH - 1));
		int right = Math.max(x + 1, Math.min(rect.x() + rect.width(), imgW));
		int bottom = Math.max(y + 1, Math.min(rect.y() + rect.height(), imgH));
		return new Rect(x, y, right - x, bottom - y);
	}

	public static Rect inflate(Rect rect, int padding) {
		return new Rect(rect.x() - padding, rect.y() - padding,
				rect.width() + padding * 2, rect.height() + padding * 2);
	}

	public static Rect shifted(Rect rect, int dx, int dy) {
		return new Rect(rect.x() + dx, rect.y() + dy, rect.width(), rect.height());
	}

	public static double whiteRatio(Mat binary, Rect rect) {
		int x = Math.max(0, rect.x());
		int y = Math.max(0, rect.y());
		int w = Math.min(rect.width(), binary.cols() - x);
		int h = Math.min(rect.height(), binary.rows() - y);
		if (w <= 0 || h <= 0) {
			return 0;
		}
		Mat roi = binary.apply(new Rect(x, y, w, h));
		try {
			return (double) opencv_core.countNonZero(roi) / (w * h);
		} finally {
			roi.release();
		}
	}

	public static double bestWhiteRatioNear(Mat binary, Rect region, int radiusX, int radiusY, int step) {
		double bestRatio = 0;
		for (int dy = -radiusY; dy <= radiusY; dy += step) {
			for (int dx = -radiusX; dx <= radiusX; dx += step) {
				bestRatio = Math.max(bestRatio, whiteRatio(binary, shifted(region, dx, dy)));
			}
		}
		return bestRatio;
	}
}
