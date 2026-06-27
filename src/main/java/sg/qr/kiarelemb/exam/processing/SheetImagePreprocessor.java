package sg.qr.kiarelemb.exam.processing;

import method.qr.kiarelemb.utils.QRLoggerUtils;
import method.qr.kiarelemb.utils.QRStringUtils;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
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

			result = SheetDeskewer.deskewBinary(binary);
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
}
