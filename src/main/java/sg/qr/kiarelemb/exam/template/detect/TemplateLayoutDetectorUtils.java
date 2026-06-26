package sg.qr.kiarelemb.exam.template.detect;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import sg.qr.kiarelemb.exam.geometry.SheetGeometryUtils;

import java.util.List;

/**
 * @author Kiarelemb
 * @projectName QR_Scan_Grade
 * @className TemplateLayoutDetectorUtils
 * @description TODO
 * @create 2026/6/26 08:05
 */
public class TemplateLayoutDetectorUtils {
	static boolean isBubbleCandidate(DetectedBox r) {
		if (r.w > 90 || r.h > 90) return false;
		int boxArea = r.w * r.h;
		if (boxArea < 80 || boxArea > 20000) return false;
		double aspect = (double) r.w / r.h;
		return aspect >= 0.35 && aspect <= 3.2;
	}

	static boolean isLargeRegionCandidate(Rect bbox, int imgW, int imgH) {
		int w = bbox.width();
		int h = bbox.height();
		int area = w * h;
		if (area < 50000) return false;
		if (w < 350 || h < 250) return false;
		if (w > imgW * 0.95 || h > imgH * 0.95) return false;
		double aspect = (double) w / h;
		return aspect >= 0.5 && aspect <= 8.0;
	}

	static DetectedBox findFillBlankRegion(List<DetectedBox> largeRects, DetectedBox choiceRect) {
		DetectedBox matchedFillRect = null;
		int bestScore = -1;
		int choiceBottom = choiceRect.y + choiceRect.h;
		int choiceRight = choiceRect.x + choiceRect.w;

		for (DetectedBox r : largeRects) {
			if (r.x == choiceRect.x && r.y == choiceRect.y && r.w == choiceRect.w && r.h == choiceRect.h) {
				continue;
			}
			boolean belowChoice = r.y >= choiceBottom + 40;
			boolean similarColumn = Math.abs(r.x - choiceRect.x) <= 120
									&& Math.abs((r.x + r.w) - choiceRight) <= 160;
			if (belowChoice && similarColumn && r.w * r.h > bestScore) {
				bestScore = r.w * r.h;
				matchedFillRect = r;
			}
		}
		return matchedFillRect;
	}

	static Mat deskewForLayout(Mat gray) {
		Mat binary = new Mat();
		opencv_imgproc.threshold(gray, binary, 150, 255, opencv_imgproc.THRESH_BINARY);
		opencv_core.bitwise_not(binary, binary);

		MatVector contours = new MatVector();
		Mat hierarchy = new Mat();
		opencv_imgproc.findContours(binary.clone(), contours, hierarchy,
				opencv_imgproc.RETR_EXTERNAL, opencv_imgproc.CHAIN_APPROX_SIMPLE);

		Point[] paperCorners = null;
		double bestArea = 0;
		double imgArea = gray.rows() * gray.cols();

		for (int i = 0; i < contours.size(); i++) {
			double area = opencv_imgproc.contourArea(contours.get(i));
			if (area < imgArea * 0.25) continue;

			Mat approx = new Mat();
			double peri = opencv_imgproc.arcLength(contours.get(i), true);
			opencv_imgproc.approxPolyDP(contours.get(i), approx, 0.02 * peri, true);

			if (approx.total() == 4 && opencv_imgproc.isContourConvex(approx) && area > bestArea) {
				Rect bbox = opencv_imgproc.boundingRect(approx);
				double aspect = (double) bbox.width() / bbox.height();
				if (aspect < 0.55 || aspect > 0.85) {
					continue;
				}
				bestArea = area;
				paperCorners = new Point[4];
				for (int j = 0; j < 4; j++) {
					org.bytedeco.javacpp.BytePointer ptr = approx.ptr(j);
					paperCorners[j] = new Point(ptr.getInt(0), ptr.getInt(4));
				}
			}
		}

		int outW = 2480, outH = 3507;
		if (paperCorners != null) {
			SheetGeometryUtils.sortCorners(paperCorners);
			Mat srcMat = SheetGeometryUtils.buildCornerMat(paperCorners[0], paperCorners[1], paperCorners[2], paperCorners[3]);
			Mat dstMat = SheetGeometryUtils.buildCornerMat(new Point(0, 0), new Point(outW - 1, 0), new Point(outW - 1, outH - 1), new Point(0, outH - 1));
			Mat perspectiveMat = opencv_imgproc.getPerspectiveTransform(srcMat, dstMat);
			Mat result = new Mat();
			opencv_imgproc.warpPerspective(gray, result, perspectiveMat, new Size(outW, outH));
			return result;
		} else {
			Mat result = new Mat();
			opencv_imgproc.resize(gray, result, new Size(outW, outH));
			return result;
		}
	}
}
