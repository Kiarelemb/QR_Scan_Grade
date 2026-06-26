package sg.qr.kiarelemb.exam.processing;

import method.qr.kiarelemb.utils.QRLoggerUtils;
import method.qr.kiarelemb.utils.QRStringUtils;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import sg.qr.kiarelemb.data.Utils;

import java.io.File;
import java.util.logging.Logger;

/**
 * @author Kiarelemb
 * @projectName QR_ScanGrade
 * @className SheetImagePreprocessor
 * @description 图像预处理：将原始答卷照片转换为可分析的二值图像
 * @create 2026/5/31 13:37
 */
public class SheetImagePreprocessor {
	private static final Logger logger = QRLoggerUtils.getLogger(SheetImagePreprocessor.class);

	/**
	 * 完整预处理管线：读入 → 灰度 → 模糊 → 二值化 → 透视校正
	 */
	public static Mat preprocess(File imageFile) {
		String path = imageFile.getAbsolutePath();
		Mat src;
		// 中文路径识别
		if (QRStringUtils.containsNonEnglishChar(path)) {
			src = Utils.imreadUnicode(imageFile);
		} else {
			src = opencv_imgcodecs.imread(path);
		}
		if (src.empty()) {
			src.release();
			throw new IllegalArgumentException("无法读取图像文件: " + path);
		}

		logger.info("原始图像尺寸: " + src.cols() + " x " + src.rows());

		Mat gray = new Mat();
		Mat binary = new Mat();
		Mat temp = new Mat();
		Mat result = null;

		try {
			opencv_imgproc.cvtColor(src, gray, opencv_imgproc.COLOR_BGR2GRAY);
			opencv_imgproc.GaussianBlur(gray, gray, new Size(5, 5), 0);

			// 第一步：用全局阈值提取所有深色区域（定位标记 + 填涂方块）
			// 填涂方块是纯黑（~20-40），文字是深灰（~80-120），背景是白（~220-255）
			opencv_imgproc.threshold(gray, temp, 150, 255, opencv_imgproc.THRESH_BINARY);

			// 第二步：反转，让深色区域变白，背景变黑
			opencv_core.bitwise_not(temp, binary);

			// 统计二值化后非零像素数
			int nonZeroPixels = opencv_core.countNonZero(binary);
			double fillRatio = (double) nonZeroPixels / (binary.rows() * binary.cols());
			logger.info("二值化后非零像素占比: " + String.format("%.2f%%", fillRatio * 100));

			result = deskew(binary);
			return result;
		} finally {
			src.release();
			gray.release();
			temp.release();
			if (result != binary) {
				binary.release();
			}
		}
	}

	/**
	 * 检测四角定位标记，执行透视校正。
	 * <p>
	 * 答题卡需在四角印刷实心黑色方块作为定位标记（registration marks）。
	 * 该方法自动查找这些方块，将其映射到矩形的四角，消除拍照产生的透视畸变。
	 */
	public static Mat deskew(Mat binary) {
		MatVector contours = new MatVector();
		Mat hierarchy = new Mat();
		opencv_imgproc.findContours(binary, contours, hierarchy,
				opencv_imgproc.RETR_EXTERNAL,
				opencv_imgproc.CHAIN_APPROX_SIMPLE);

		double imgArea = binary.rows() * binary.cols();

		// 找面积最大的可近似为四边形的轮廓（纸张边界）
		double bestArea = 0;
		Point[] paperCorners = null;

		for (int i = 0; i < contours.size(); i++) {
			Mat contour = contours.get(i);
			double area = opencv_imgproc.contourArea(contour);

			// 必须覆盖画面 25% 以上才可能是纸张
			if (area < imgArea * 0.25) {
				contour.release();
				continue;
			}

			Mat approx = new Mat();
			double peri = opencv_imgproc.arcLength(contour, true);
			opencv_imgproc.approxPolyDP(contour, approx, 0.02 * peri, true);

			if (approx.total() != 4 || !opencv_imgproc.isContourConvex(approx)) {
				approx.release();
				contour.release();
				continue;
			}

			Rect bbox = opencv_imgproc.boundingRect(approx);
			double aspect = (double) bbox.width() / bbox.height();
			if (aspect < 0.55 || aspect > 0.85) {
				approx.release();
				contour.release();
				continue;
			}

			if (area > bestArea) {
				bestArea = area;
				paperCorners = new Point[4];
				for (int j = 0; j < 4; j++) {
					BytePointer ptr = approx.ptr(j);
					paperCorners[j] = new Point(ptr.getInt(0), ptr.getInt(4));
				}
			}
			approx.release();
			contour.release();
		}

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

		// 将四个角排序为 TL, TR, BR, BL
		sortCornersClockwise(paperCorners);

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


		// 固定输出为标准尺寸，确保坐标参数与输入图像分辨率无关
		int fixedWidth = 2480;
		int fixedHeight = 3507;
		logger.info("纸张四边尺寸: " + outWidth + "x" + outHeight + " → 固定输出: " + fixedWidth + "x" + fixedHeight);

		Mat srcMat = buildCornerMat(
				paperCorners[0], paperCorners[1],
				paperCorners[2], paperCorners[3]);
		Mat dstMat = buildCornerMat(
				new Point(0, 0),
				new Point(fixedWidth - 1, 0),
				new Point(fixedWidth - 1, fixedHeight - 1),
				new Point(0, fixedHeight - 1));

		Mat perspectiveMat = opencv_imgproc.getPerspectiveTransform(srcMat, dstMat);
		Mat result = new Mat();

		outWidth = fixedWidth;
		outHeight = fixedHeight;

		opencv_imgproc.warpPerspective(binary, result, perspectiveMat,
				new Size(outWidth, outHeight));

		// ============================================================
		// 强制重新二值化，消除边缘灰色像素，让填涂变纯白！
		// ============================================================
		opencv_imgproc.threshold(result, result, 127, 255, opencv_imgproc.THRESH_BINARY);

		int resultNonZero = opencv_core.countNonZero(result);
		double resultFill = (double) resultNonZero / (result.rows() * result.cols());
		logger.info("透视校正完成，输出尺寸: " + outWidth + "x" + outHeight
					+ "，非零像素占比: " + String.format("%.2f%%", resultFill * 100));
		srcMat.release();
		dstMat.release();
		perspectiveMat.release();
		hierarchy.release();
		return result;
	}

	/**
	 * 将无序的四个角点按顺序排列：左上(0) → 右上(1) → 右下(2) → 左下(3)
	 */
	private static void sortCornersClockwise(Point[] corners) {
		// 计算质心
		double cx = 0, cy = 0;
		for (Point p : corners) {
			cx += p.x();
			cy += p.y();
		}
		cx /= 4;
		cy /= 4;

		final double cxx = cx;
		final double cyy = cy;

		// 以质心为原点，按角度排序（atan2）
		java.util.Arrays.sort(corners, (a, b) -> {
			double angleA = Math.atan2(a.y() - cyy, a.x() - cxx);
			double angleB = Math.atan2(b.y() - cyy, b.x() - cxx);
			return Double.compare(angleA, angleB);
		});

		// 此时角点按逆时针排列，需要找到真正的左上角（x+y 最小）
		int tlIndex = 0;
		double minSum = corners[0].x() + corners[0].y();
		for (int i = 1; i < 4; i++) {
			double sum = corners[i].x() + corners[i].y();
			if (sum < minSum) {
				minSum = sum;
				tlIndex = i;
			}
		}

		// 旋转数组，使左上角在索引 0
		Point[] sorted = new Point[4];
		for (int i = 0; i < 4; i++) {
			sorted[i] = corners[(tlIndex + i) % 4];
		}
		System.arraycopy(sorted, 0, corners, 0, 4);
	}

	/**
	 * 将四个角点构建为 4x1 CV_32FC2 的 Mat，供 getPerspectiveTransform 使用。
	 */
	private static Mat buildCornerMat(Point tl, Point tr, Point br, Point bl) {
		Mat mat = new Mat(4, 1, opencv_core.CV_32FC2);
		FloatIndexer idx = mat.createIndexer();
		idx.put(0, 0, (float) tl.x(), (float) tl.y());
		idx.put(1, 0, (float) tr.x(), (float) tr.y());
		idx.put(2, 0, (float) br.x(), (float) br.y());
		idx.put(3, 0, (float) bl.x(), (float) bl.y());
		idx.release();
		return mat;
	}

	/**
	 * 计算轮廓的 solidity（面积 / 凸包面积），用于判断是否为实心方块。
	 */
	private static double computeSolidity(Mat contour, double contourArea) {
		if (contourArea <= 0) {
			return 0;
		}
		Mat hull = new Mat();
		opencv_imgproc.convexHull(contour, hull);
		double hullArea = opencv_imgproc.contourArea(hull);
		return hullArea > 0 ? contourArea / hullArea : 0;
	}

	/**
	 * 四点去重：确保四个角定位标记是四个不同的点。
	 * 若有两个角映射到同一个标记（距离 < 阈值），返回 null 表示失败。
	 */
	private static Point[] deduplicate(Point tl, Point tr, Point br, Point bl) {
		Point[] points = {tl, tr, br, bl};
		double threshold = 15.0;

		for (int i = 0; i < points.length; i++) {
			for (int j = i + 1; j < points.length; j++) {
				if (euclideanDist(points[i], points[j]) < threshold) {
					return null;
				}
			}
		}
		return points;
	}

	private static double euclideanDist(Point a, Point b) {
		double dx = a.x() - b.x();
		double dy = a.y() - b.y();
		return Math.sqrt(dx * dx + dy * dy);
	}

	/**
	 * 兜底处理：检测"白边黑心"的矩形轮廓（疑似被遗漏的填涂方块），
	 * 将其内部填充为白色，确保填涂区域在二值图中完全变白。
	 * <p>
	 * 判断逻辑：
	 * <ol>
	 *   <li>轮廓近似为矩形（4~6 个顶点）</li>
	 *   <li>近似正方形（宽高比 0.6~1.7）</li>
	 *   <li>尺寸在合理范围（面积 50~画面 2%）</li>
	 *   <li>外接矩形内白色占比在 3%~50%（黑心特征）</li>
	 * </ol>
	 * 满足条件则用 drawContours 填充整个封闭区域为白色。
	 */
	private static void fillHollowRectangles(Mat binary) {
		MatVector contours = new MatVector();
		Mat hierarchy = new Mat();
		opencv_imgproc.findContours(binary.clone(), contours, hierarchy,
				opencv_imgproc.RETR_EXTERNAL,
				opencv_imgproc.CHAIN_APPROX_SIMPLE);

		double maxArea = binary.rows() * binary.cols() * 0.02;
		int filledCount = 0;

		for (int i = 0; i < contours.size(); i++) {
			Mat contour = contours.get(i);
			double area = opencv_imgproc.contourArea(contour);

			if (area < 50 || area > maxArea) {
				continue;
			}

			Mat approx = new Mat();
			double peri = opencv_imgproc.arcLength(contour, true);
			opencv_imgproc.approxPolyDP(contour, approx, 0.04 * peri, true);

			long vertCount = approx.total();
			if (vertCount < 4 || vertCount > 6) {
				continue;
			}

			Rect bbox = opencv_imgproc.boundingRect(contour);
			double aspect = (double) bbox.width() / bbox.height();
			if (aspect < 0.6 || aspect > 1.7) {
				continue;
			}

			Mat roi = binary.apply(bbox);
			int whitePixels = opencv_core.countNonZero(roi);
			double whiteRatio = (double) whitePixels / (bbox.width() * bbox.height());

			// 白边黑心：白色占比在 3%~50% 之间
			// 太低（<3%）= 噪点轮廓；太高（>50%）= 已正常识别的实心块
			if (whiteRatio > 0.03 && whiteRatio < 0.50) {
				opencv_imgproc.drawContours(binary, contours, i, new Scalar(255));
				filledCount++;
			}
		}

		if (filledCount > 0) {
			logger.info("fillHollowRectangles: 填充了 " + filledCount + " 个黑心方块");
		}
	}
}
