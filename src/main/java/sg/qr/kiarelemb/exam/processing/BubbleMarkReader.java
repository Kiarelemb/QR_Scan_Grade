package sg.qr.kiarelemb.exam.processing;

import method.qr.kiarelemb.utils.QRLoggerUtils;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;

import java.util.logging.Logger;

/**
 * @author Kiarelemb
 * @projectName QR_ScanGrade
 * @className BubbleMarkReader
 * @description TODO
 * @create 2026/5/31 14:09
 */
public class BubbleMarkReader {
	private static final Logger logger = QRLoggerUtils.getLogger(BubbleMarkReader.class);
	private static final int LOCAL_SEARCH_RADIUS_X = 20;
	private static final int LOCAL_SEARCH_RADIUS_Y = 16;
	private static final int LOCAL_SEARCH_STEP = 8;

	/**
	 * 默认填涂判定阈值：区域内黑色像素占比超过此值视为已填涂
	 */
	public static final double DEFAULT_FILL_THRESHOLD = 0.30;

	private final double fillThreshold;

	public BubbleMarkReader() {
		this(DEFAULT_FILL_THRESHOLD);
	}

	public BubbleMarkReader(double fillThreshold) {
		this.fillThreshold = fillThreshold;
	}

	/**
	 * 判断指定矩形区域内的选项是否被填涂。
	 *
	 * @param binary 二值图像（全局阈值+反转，白色像素=填涂区域，黑色=背景）
	 * @param region 选项框对应的矩形区域
	 * @return true 表示该选项已填涂
	 */
	public boolean isBubbleFilled(Mat binary, Rect region) {
		return BinaryRegionAnalyzer.whiteRatio(binary, region) >= fillThreshold;
	}

	/**
	 * 统计指定区域内白色像素占比，可用于调试。
	 * @return 白色像素占比 (0.0 ~ 1.0)
	 */
	public double getFillRatio(Mat binary, Rect region) {
		return BinaryRegionAnalyzer.bestWhiteRatioNear(binary, region,
				LOCAL_SEARCH_RADIUS_X, LOCAL_SEARCH_RADIUS_Y, LOCAL_SEARCH_STEP);
	}

	/**
	 * 调试方法：输出指定区域内各选项的填涂比例
	 *
	 * @param binary 二值图像
	 * @param regions 选项区域列表
	 * @param labels 选项标签（如 A,B,C,D）
	 */
	public void debugOptions(Mat binary, java.util.List<Rect> regions, String[] labels) {
		logger.info("=== 选项填涂比例 ===");
		for (int i = 0; i < regions.size(); i++) {
			double ratio = getFillRatio(binary, regions.get(i));
			String flag = ratio >= fillThreshold ? "✓" : " ";
			logger.info(String.format("  %s: %.2f%% %s", labels[i], ratio * 100, flag));
		}
		logger.info("==================");
	}
}
