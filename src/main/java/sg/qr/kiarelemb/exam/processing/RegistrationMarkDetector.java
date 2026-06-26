package sg.qr.kiarelemb.exam.processing;

import method.qr.kiarelemb.utils.QRLoggerUtils;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Rect;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class RegistrationMarkDetector {
	private static final Logger logger = QRLoggerUtils.getLogger(RegistrationMarkDetector.class);
	private static final int MIN_MARKS = 8;

	private RegistrationMarkDetector() {
	}

	public static MarkBounds detectMarkBounds(Mat binary) {
		MatVector contours = new MatVector();
		Mat hierarchy = new Mat();
		Mat work = binary.clone();
		List<Point> centers = new ArrayList<>();
		try {
			opencv_imgproc.findContours(work, contours, hierarchy,
					opencv_imgproc.RETR_EXTERNAL, opencv_imgproc.CHAIN_APPROX_SIMPLE);

			double imgArea = (double) binary.cols() * binary.rows();
			for (int i = 0; i < contours.size(); i++) {
				Mat contour = contours.get(i);
				Rect bbox = opencv_imgproc.boundingRect(contour);
				if (isRegistrationMark(binary, bbox, imgArea)) {
					centers.add(new Point(bbox.x() + bbox.width() / 2, bbox.y() + bbox.height() / 2));
				}
				contour.release();
			}
		} finally {
			work.release();
			hierarchy.release();
			contours.releaseReference();
		}

		if (centers.size() < MIN_MARKS) {
			logger.warning("定位黑块数量不足：" + centers.size());
			return null;
		}
		return MarkBounds.from(centers);
	}

	public static boolean isRegistrationMark(Mat binary, Rect bbox, double imgArea) {
		int area = bbox.width() * bbox.height();
		if (area < imgArea * 0.00025 || area > imgArea * 0.006) {
			return false;
		}
		double aspect = (double) bbox.width() / bbox.height();
		if (aspect < 1.15 || aspect > 2.6) {
			return false;
		}

		int cx = bbox.x() + bbox.width() / 2;
		int cy = bbox.y() + bbox.height() / 2;
		boolean nearEdge = cx < binary.cols() * 0.16
						   || cx > binary.cols() * 0.84
						   || cy < binary.rows() * 0.08
						   || cy > binary.rows() * 0.93;
		if (!nearEdge) {
			return false;
		}

		return BinaryRegionAnalyzer.whiteRatio(binary, bbox) > 0.65;
	}

	public record MarkBounds(double minX, double minY, double maxX, double maxY, int count) {
		private static MarkBounds from(List<Point> centers) {
			double minX = Double.MAX_VALUE;
			double minY = Double.MAX_VALUE;
			double maxX = -Double.MAX_VALUE;
			double maxY = -Double.MAX_VALUE;
			for (Point point : centers) {
				minX = Math.min(minX, point.x());
				minY = Math.min(minY, point.y());
				maxX = Math.max(maxX, point.x());
				maxY = Math.max(maxY, point.y());
			}
			if (maxX <= minX || maxY <= minY) {
				return null;
			}
			return new MarkBounds(minX, minY, maxX, maxY, centers.size());
		}
	}
}
