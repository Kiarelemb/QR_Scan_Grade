package sg.qr.kiarelemb.exam.processing;

import method.qr.kiarelemb.utils.QRLoggerUtils;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import sg.qr.kiarelemb.exam.geometry.SheetGeometryUtils;

import java.util.logging.Logger;

public final class SheetDeskewer {
	private static final Logger logger = QRLoggerUtils.getLogger(SheetDeskewer.class);
	private static final int OUTPUT_WIDTH = 2480;
	private static final int OUTPUT_HEIGHT = 3507;

	/**
	 * 检测纸张边界并透视校正到固定答题卡尺寸。
	 */
	public static Mat deskewBinary(Mat binary) {
		MatVector contours = new MatVector();
		Mat hierarchy = new Mat();
		opencv_imgproc.findContours(binary, contours, hierarchy,
				opencv_imgproc.RETR_EXTERNAL,
				opencv_imgproc.CHAIN_APPROX_SIMPLE);

		Point[] paperCorners = detectPaperCorners(contours, binary.rows() * binary.cols());
		logger.info("轮廓总数: " + contours.size() + "，找到纸张边界: " + (paperCorners != null));

		if (paperCorners == null) {
			logger.info("未检测到纸张边界，回退为图像四角（无透视校正）");
			paperCorners = new Point[]{
					new Point(0, 0),
					new Point(binary.cols() - 1, 0),
					new Point(binary.cols() - 1, binary.rows() - 1),
					new Point(0, binary.rows() - 1)
			};
		}

		SheetGeometryUtils.sortCorners(paperCorners);
		logger.info("纸张四角坐标: TL(" + paperCorners[0].x() + "," + paperCorners[0].y()
		            + ") TR(" + paperCorners[1].x() + "," + paperCorners[1].y()
		            + ") BR(" + paperCorners[2].x() + "," + paperCorners[2].y()
		            + ") BL(" + paperCorners[3].x() + "," + paperCorners[3].y() + ")");

		int outWidth = (int) Math.max(
				euclideanDist(paperCorners[0], paperCorners[1]),
				euclideanDist(paperCorners[3], paperCorners[2]));
		int outHeight = (int) Math.max(
				euclideanDist(paperCorners[0], paperCorners[3]),
				euclideanDist(paperCorners[1], paperCorners[2]));

		if (outWidth <= 0 || outHeight <= 0) {
			logger.warning("计算出无效的输出尺寸，返回原图");
			hierarchy.release();
			return binary;
		}

		logger.info("纸张四边尺寸: " + outWidth + "x" + outHeight
		            + " -> 固定输出: " + OUTPUT_WIDTH + "x" + OUTPUT_HEIGHT);

		Mat srcMat = SheetGeometryUtils.buildCornerMat(
				paperCorners[0], paperCorners[1],
				paperCorners[2], paperCorners[3]);
		Mat dstMat = SheetGeometryUtils.buildCornerMat(
				new Point(0, 0),
				new Point(OUTPUT_WIDTH - 1, 0),
				new Point(OUTPUT_WIDTH - 1, OUTPUT_HEIGHT - 1),
				new Point(0, OUTPUT_HEIGHT - 1));
		Mat perspectiveMat = opencv_imgproc.getPerspectiveTransform(srcMat, dstMat);
		Mat result = new Mat();

		opencv_imgproc.warpPerspective(binary, result, perspectiveMat, new Size(OUTPUT_WIDTH, OUTPUT_HEIGHT));
		opencv_imgproc.threshold(result, result, 127, 255, opencv_imgproc.THRESH_BINARY);

		int resultNonZero = opencv_core.countNonZero(result);
		double resultFill = (double) resultNonZero / (result.rows() * result.cols());
		logger.info("透视校正完成，输出尺寸: " + OUTPUT_WIDTH + "x" + OUTPUT_HEIGHT
		            + "，非零像素占比: " + String.format("%.2f%%", resultFill * 100));

		srcMat.release();
		dstMat.release();
		perspectiveMat.release();
		hierarchy.release();
		return result;
	}

	private static Point[] detectPaperCorners(MatVector contours, double imgArea) {
		double bestArea = 0;
		Point[] paperCorners = null;

		for (int i = 0; i < contours.size(); i++) {
			Mat contour = contours.get(i);
			double area = opencv_imgproc.contourArea(contour);
			if (area < imgArea * 0.25) {
				contour.release();
				continue;
			}

			Mat approx = new Mat();
			double peri = opencv_imgproc.arcLength(contour, true);
			opencv_imgproc.approxPolyDP(contour, approx, 0.02 * peri, true);

			if (approx.total() == 4 && opencv_imgproc.isContourConvex(approx)) {
				Rect bbox = opencv_imgproc.boundingRect(approx);
				double aspect = (double) bbox.width() / bbox.height();
				if (aspect >= 0.55 && aspect <= 0.85 && area > bestArea) {
					bestArea = area;
					paperCorners = new Point[4];
					for (int j = 0; j < 4; j++) {
						BytePointer ptr = approx.ptr(j);
						paperCorners[j] = new Point(ptr.getInt(0), ptr.getInt(4));
					}
				}
			}
			approx.release();
			contour.release();
		}
		return paperCorners;
	}

	private static double euclideanDist(Point a, Point b) {
		double dx = a.x() - b.x();
		double dy = a.y() - b.y();
		return Math.sqrt(dx * dx + dy * dy);
	}
}