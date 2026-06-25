package sg.qr.kiarelemb.grading.layout;

import method.qr.kiarelemb.utils.QRLoggerUtils;
import method.qr.kiarelemb.utils.QRStringUtils;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import sg.qr.kiarelemb.data.Utils;
import sg.qr.kiarelemb.grading.model.AnswerSheet;
import sg.qr.kiarelemb.grading.pipeline.RegionDetector;

import java.io.File;
import java.util.*;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * 自动检测答题卡布局。
 * 阶段①：找出所有矩形（包括红色边框的气泡），打印坐标。
 * 阶段②：分析并自动提取布局参数。
 */
public class LayoutDetector {
	private static final Logger logger = QRLoggerUtils.getLogger(LayoutDetector.class);

	// ==================== 布局参数（自动推断结果会存到这里） ====================
	public int examIdDigits;      // 准考证号位数
	public int examStartX;        // 考号区域起始 X
	public int examStartY;        // 考号区域起始 Y
	public int examBubbleW;       // 考号气泡宽度
	public int examBubbleH;       // 考号气泡高度
	public int examHGap;          // 考号水平间距
	public int examVGap;          // 考号垂直间距

	public int choiceStartX;      // 选择题区域起始 X
	public int choiceStartY;      // 选择题区域起始 Y
	public int choiceBubbleW;     // 选择题气泡宽度
	public int choiceBubbleH;     // 选择题气泡高度
	public int choiceHGap;        // 选择题水平间距
	public int choiceVGap;        // 选择题垂直间距
	public int choiceOptionCount = 4; // 每道选择题选项数，支持 ABC / ABCD

	public int fillStartX;        // 填空题大框起始 X
	public int fillStartY;        // 填空题大框起始 Y
	public int fillBoxW;          // 填空题大框宽度
	public int fillBoxH;          // 填空题大框高度

	public int fillBlankCount;
	public int detectedImageW;
	public int detectedImageH;

	public List<DetectedRect> allRects;

	/**
	 * 准考证号区域的外包围矩形
	 */
	public DetectedRect examRegionRect;

	/**
	 * 选择题区域的外包围矩形
	 */
	public Rect choiceRegionRect;

	// ==================== 选择题布局结构（从真实矩形检测） ====================

	/**
	 * 选择题行数
	 */
	public int choiceRows;

	/**
	 * 每行的列数（例如 [4, 2]）
	 */
	public int[] choiceColsPerRow;

	/**
	 * 每行起始Y（例如 [1600, 2014]）
	 */
	public int[] choiceRowStartYs;

	/**
	 * 所有列的起始X（展平，先第1行各列，再第2行各列）
	 */
	public int[] choiceColStartXs;
	public int[] choiceQuestionsPerCol;

	// ==================== 主入口 ====================

	public static LayoutDetector detectFromTemplate(File templateImage) {
		String filePath = templateImage.getAbsolutePath();
		Mat src;
		// 中文路径识别
		if (QRStringUtils.containsNonEnglishChar(filePath)) {
			src = Utils.imreadUnicode(templateImage);
		} else {
			src = opencv_imgcodecs.imread(filePath);
		}
		if (src.empty()) {
			throw new IllegalArgumentException("无法读取: " + templateImage);
		}
		LayoutDetector detector = new LayoutDetector();

		// 步骤1：透视校正（用灰度图）
		Mat gray = new Mat();
		opencv_imgproc.cvtColor(src, gray, opencv_imgproc.COLOR_BGR2GRAY);
		Mat deskewedGray;
		if (Math.abs(gray.cols() - 2480) <= 10 && Math.abs(gray.rows() - 3507) <= 10) {
			deskewedGray = gray.clone();
		} else {
			deskewedGray = deskewForLayout(gray);
		}
		int w = deskewedGray.cols();
		int h = deskewedGray.rows();
		detector.detectedImageW = w;
		detector.detectedImageH = h;
		logger.info("校正后尺寸: " + w + " × " + h);

		// 步骤2：检测矩形（用阈值法）
		detector.detectAllRectanglesByThreshold(deskewedGray);

		// 步骤4：自动分析并填充布局参数（新增）
		detector.analyzeLayout();

		// 步骤3：后处理和打印（原有的）
		detector.postProcessRects();

		return detector;
	}

	// ==================== 自动分析布局参数（核心新增逻辑） ====================
	public void analyzeLayout() {
		if (allRects == null || allRects.isEmpty()) {
			logger.warning("没有矩形数据，无法推断布局。");
			return;
		}

		// 1. 提取大框（用于填空题）
		// 2. 提取气泡框参数
		Map<String, Integer> sizeMap = new HashMap<>();
		for (DetectedRect r : allRects) {
			if (!isBubbleCandidate(r)) continue;
			String key = r.w + "x" + r.h;
			sizeMap.put(key, sizeMap.getOrDefault(key, 0) + 1);
		}
		String mostCommonSize = sizeMap.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.orElse("44x28");
		String[] sizes = mostCommonSize.split("x");
		int bubbleW = Integer.parseInt(sizes[0]);
		int bubbleH = Integer.parseInt(sizes[1]);

		this.examBubbleW = bubbleW;
		this.examBubbleH = bubbleH;
		this.choiceBubbleW = bubbleW;
		this.choiceBubbleH = bubbleH;

		// 3. 先按列聚类，识别考号（竖排10个）
		Map<Integer, List<Integer>> colGroups = new LinkedHashMap<>();
		for (DetectedRect r : allRects) {
			if (Math.abs(r.w - bubbleW) > 10 || Math.abs(r.h - bubbleH) > 10) continue;
			int colKey = (r.cx / 10) * 10;
			colGroups.computeIfAbsent(colKey, k -> new ArrayList<>()).add(r.cy);
		}
		List<Integer> sortedCols = new ArrayList<>(colGroups.keySet());
		Collections.sort(sortedCols);

		int examEndY; // 考号区域底部Y坐标

		// 找出所有含10个均匀分布矩形的列
		List<Integer> examColXs = new ArrayList<>();
		for (int colKey : sortedCols) {
			List<Integer> yCoords = colGroups.get(colKey);
			if (yCoords.size() >= 10) {
				Collections.sort(yCoords);
				// 取前10个计算间距
				int firstGap = yCoords.get(1) - yCoords.get(0);
				boolean uniform = true;
				for (int i = 1; i < 9; i++) {
					int g = yCoords.get(i + 1) - yCoords.get(i);
					if (Math.abs(g - firstGap) > 5) {
						uniform = false;
						break;
					}
				}
				if (uniform) {
					examColXs.add(colKey);
				}
			}
		}
		Collections.sort(examColXs);

		// 从候选列中找出最大连续组（间距均匀的）
		if (!examColXs.isEmpty()) {
			// 计算所有相邻间距，取中位数作为标准间距
			List<Integer> allGaps = new ArrayList<>();
			for (int i = 1; i < examColXs.size(); i++) {
				allGaps.add(examColXs.get(i) - examColXs.get(i - 1));
			}
			Collections.sort(allGaps);
			int medianGap = allGaps.isEmpty() ? 86 : allGaps.get(allGaps.size() / 2);
			logger.info("[DEBUG] 准考证列间距中位数: " + medianGap);

			// 只保留间距在 medianGap±30% 范围内的连续列
			List<Integer> validCols = new ArrayList<>();
			validCols.add(examColXs.get(0));
			for (int i = 1; i < examColXs.size(); i++) {
				int gap = examColXs.get(i) - examColXs.get(i - 1);
				if (Math.abs(gap - medianGap) <= medianGap * 0.3) {
					validCols.add(examColXs.get(i));
				} else {
					break; // 间距异常，截断
				}
			}
			logger.info("[DEBUG] 有效准考证列数: " + validCols.size() + ", X=" + validCols);

			this.examIdDigits = validCols.size();
			this.examStartX = validCols.get(0) - bubbleW / 2;
			this.examHGap = medianGap; // 中心到中心间距

			// 用第一列的Y信息计算垂直参数
			List<Integer> firstColY = colGroups.get(validCols.get(0));
			Collections.sort(firstColY);
			this.examStartY = firstColY.get(0) - bubbleH / 2;
			this.examVGap = firstColY.get(1) - firstColY.get(0); // 涓績鍒颁腑蹇冮棿璺?

			examEndY = this.examStartY + 10 * this.examVGap; // 10涓€肩殑鍖哄煙楂樺害
		} else {
			// 兜底：用已知模板参数
			this.examIdDigits = 9;
			this.examStartX = 1398;
			this.examStartY = 858;
			this.examHGap = 86;
			this.examVGap = 58;
			examEndY = this.examStartY + 10 * this.examVGap;
		}

		// 4. 按行聚类，寻找选择题行（必须在考号区域下方）
		// 先用容差聚类把同一行的气泡归为一组
		List<DetectedRect> choiceRects = new ArrayList<>();
		for (DetectedRect r : allRects) {
			if (Math.abs(r.w - bubbleW) > 10 || Math.abs(r.h - bubbleH) > 10) continue;
			if (r.cy <= examEndY + 50) continue;
			choiceRects.add(r);
		}

		// 按 Y 坐标聚类（容差15px）
		int[] yValues = choiceRects.stream().mapToInt(r -> r.cy).sorted().toArray();
		List<Integer> yClusters = clusterValues(yValues, 15);

		// 构建 rowGroups 和 rowRectGroups（key = 聚类后的代表Y值）
		Map<Integer, List<Integer>> rowGroups = new LinkedHashMap<>();
		Map<Integer, List<DetectedRect>> rowRectGroups = new LinkedHashMap<>();
		for (DetectedRect r : choiceRects) {
			// 找到最近的聚类中心
			int rowKey = yClusters.stream()
					.min(Comparator.comparingInt(k -> Math.abs(k - r.cy)))
					.orElse(r.cy);
			rowGroups.computeIfAbsent(rowKey, k -> new ArrayList<>()).add(r.cx);
			rowRectGroups.computeIfAbsent(rowKey, k -> new ArrayList<>()).add(r);
		}
		List<Integer> sortedRows = new ArrayList<>(rowGroups.keySet());
		Collections.sort(sortedRows);

		// 寻找第一个（最靠上）有3个或4个等间距框的行作为选择题起始行
		for (int rowKey : sortedRows) {
			List<Integer> xCoords = rowGroups.get(rowKey);
			if (xCoords.size() >= 3) {
				Collections.sort(xCoords);
				int g1 = xCoords.get(1) - xCoords.get(0);
				int g2 = xCoords.get(2) - xCoords.get(1);
				if (Math.abs(g1 - g2) < 5) {
					this.choiceStartX = xCoords.get(0) - bubbleW / 2;
					this.choiceStartY = rowKey - bubbleH / 2;
					this.choiceHGap = g1;
					break;
				}
			}
		}

		// 计算选择题垂直间距 choiceVGap（使用后续行的间距）
		if (sortedRows.size() > 1) {
			List<Integer> centerGaps = new ArrayList<>();
			for (int i = 1; i < sortedRows.size(); i++) {
				List<DetectedRect> prevRow = rowRectGroups.get(sortedRows.get(i - 1));
				List<DetectedRect> currRow = rowRectGroups.get(sortedRows.get(i));
				if (prevRow.size() >= 3 && currRow.size() >= 3) {
					// 取两行的平均中心Y坐标
					int prevAvgY = (int) prevRow.stream().mapToInt(r -> r.cy).average().orElse(0);
					int currAvgY = (int) currRow.stream().mapToInt(r -> r.cy).average().orElse(0);
					centerGaps.add(currAvgY - prevAvgY);
				}
			}
			if (!centerGaps.isEmpty()) {
				Collections.sort(centerGaps);
				this.choiceVGap = centerGaps.get(centerGaps.size() / 2);
			}
		}

		// 5. 填空题大框（取底部最大的矩形）
		inferChoiceColumns(rowGroups, sortedRows, bubbleW, bubbleH);
	}

	/**
	 * 从已检测的矩形中推断选择题的列X坐标和行Y坐标。
	 * 核心思路：在同一行内，连续的气泡间距<100px属于同一题组选项，间距>100px表示新的一列。
	 */
	private void inferChoiceColumns(Map<Integer, List<Integer>> rowGroups,
									List<Integer> sortedRows,
									int bubbleW, int bubbleH) {
		class RowInfo {
			final int y;
			final List<Integer> colStarts;

			RowInfo(int y, List<Integer> colStarts) {
				this.y = y;
				this.colStarts = colStarts;
			}
		}

		List<RowInfo> rowInfos = new ArrayList<>();
		List<Integer> detectedOptionCounts = new ArrayList<>();
		for (int rowKey : sortedRows) {
			List<Integer> xCoords = rowGroups.get(rowKey);
			if (xCoords == null || xCoords.size() < 3) continue;

			Collections.sort(xCoords);
			List<Integer> colStarts = new ArrayList<>();
			List<Integer> optionCounts = new ArrayList<>();
			int groupStart = xCoords.get(0);
			int optionGap = inferOptionGap(xCoords);
			if (this.choiceHGap <= 0 && optionGap > 0) {
				this.choiceHGap = optionGap;
			}
			int columnBreakGap = Math.max(100, optionGap * 2);

			for (int i = 1; i < xCoords.size(); i++) {
				int gap = xCoords.get(i) - xCoords.get(i - 1);
				if (gap > columnBreakGap) {
					int optionCount = optionCountInGroup(xCoords, groupStart, i);
					if (optionCount >= 3 && optionCount <= 4) {
						colStarts.add(groupStart - bubbleW / 2);
						optionCounts.add(optionCount);
					}
					groupStart = xCoords.get(i);
				}
			}
			int optionCount = optionCountInGroup(xCoords, groupStart, xCoords.size());
			if (optionCount >= 3 && optionCount <= 4) {
				colStarts.add(groupStart - bubbleW / 2);
				optionCounts.add(optionCount);
			}
			if (!colStarts.isEmpty()) {
				detectedOptionCounts.addAll(optionCounts);
				rowInfos.add(new RowInfo(rowKey - bubbleH / 2, colStarts));
			}
		}
		if (!detectedOptionCounts.isEmpty()) {
			this.choiceOptionCount = dominantCount(detectedOptionCounts);
		}

		if (rowInfos.isEmpty()) {
			this.choiceRows = 0;
			this.choiceColsPerRow = new int[0];
			this.choiceRowStartYs = new int[0];
			this.choiceColStartXs = new int[0];
			this.choiceQuestionsPerCol = new int[0];
			return;
		}

		rowInfos.sort(Comparator.comparingInt(r -> r.y));
		List<Integer> yGaps = new ArrayList<>();
		for (int i = 1; i < rowInfos.size(); i++) {
			yGaps.add(rowInfos.get(i).y - rowInfos.get(i - 1).y);
		}
		Collections.sort(yGaps);
		int normalRowGap = yGaps.isEmpty() ? choiceVGap : yGaps.get(yGaps.size() / 2);
		int bandBreakGap = Math.max(normalRowGap * 2, normalRowGap + 40);

		List<List<RowInfo>> bands = new ArrayList<>();
		List<RowInfo> currentBand = new ArrayList<>();
		currentBand.add(rowInfos.get(0));
		for (int i = 1; i < rowInfos.size(); i++) {
			int gap = rowInfos.get(i).y - rowInfos.get(i - 1).y;
			if (gap > bandBreakGap) {
				bands.add(currentBand);
				currentBand = new ArrayList<>();
			}
			currentBand.add(rowInfos.get(i));
		}
		bands.add(currentBand);

		this.choiceRows = bands.size();
		this.choiceRowStartYs = new int[choiceRows];
		this.choiceColsPerRow = new int[choiceRows];
		List<Integer> flatXs = new ArrayList<>();
		List<Integer> flatCounts = new ArrayList<>();

		for (int i = 0; i < bands.size(); i++) {
			List<RowInfo> band = bands.get(i);
			this.choiceRowStartYs[i] = band.get(0).y;

			List<Integer> bandCols = new ArrayList<>();
			for (RowInfo row : band) {
				for (int x : row.colStarts) {
					boolean exists = false;
					for (int oldX : bandCols) {
						if (Math.abs(oldX - x) <= 20) {
							exists = true;
							break;
						}
					}
					if (!exists) bandCols.add(x);
				}
			}
			Collections.sort(bandCols);
			this.choiceColsPerRow[i] = bandCols.size();

			for (int colX : bandCols) {
				int count = 0;
				for (RowInfo row : band) {
					for (int x : row.colStarts) {
						if (Math.abs(x - colX) <= 20) {
							count++;
							break;
						}
					}
				}
				flatXs.add(colX);
				flatCounts.add(count);
			}
		}

		this.choiceColStartXs = flatXs.stream().mapToInt(Integer::intValue).toArray();
		this.choiceQuestionsPerCol = flatCounts.stream().mapToInt(Integer::intValue).toArray();

		logger.info("========== 选择题布局结构 ==========");
		logger.info("每题选项数: " + choiceOptionCount);
		logger.info("大行数: " + choiceRows);
		for (int i = 0, base = 0; i < choiceRows; i++) {
			StringBuilder rowLog = new StringBuilder("第")
					.append(i + 1).append("大行: ")
					.append(choiceColsPerRow[i])
					.append("列, Y=")
					.append(choiceRowStartYs[i])
					.append(", X=[");
			for (int j = 0; j < choiceColsPerRow[i]; j++) {
				if (j > 0) rowLog.append(", ");
				rowLog.append(choiceColStartXs[base + j])
						.append("/")
						.append(choiceQuestionsPerCol[base + j])
						.append("题");
			}
			base += choiceColsPerRow[i];
			rowLog.append("]");
			logger.info(rowLog.toString());
		}
		logger.info("=====================================");
	}


	// ==================== 打印参数 ====================
	public AnswerSheet buildAnswerSheet(int imgW, int imgH, String sheetName, String[] correctAnswers) {
		// 打印参数（调试用）
		logger.info("========== 自动推断的布局参数 ==========");
		logger.info("考号位数: " + examIdDigits);
		logger.info("考号起始X: " + examStartX + ", 起始Y: " + examStartY);
		logger.info("考号气泡: " + examBubbleW + "x" + examBubbleH);
		logger.info("考号间距: H=" + examHGap + ", V=" + examVGap);
		logger.info("选择题起始X: " + choiceStartX + ", 起始Y: " + choiceStartY);
		logger.info("选择题气泡: " + choiceBubbleW + "x" + choiceBubbleH);
		logger.info("选择题间距: H=" + choiceHGap + ", V=" + choiceVGap);
		logger.info("选择题选项数: " + choiceOptionCount);
		logger.info("填空题大框: X=" + fillStartX + ", Y=" + fillStartY + ", W=" + fillBoxW + ", H=" + fillBoxH);
		logger.info("==========================================");

		// 计算选择题总数
		int choiceTotal = 0;
		if (choiceQuestionsPerCol != null) {
			for (int count : choiceQuestionsPerCol) {
				choiceTotal += count;
			}
		}

		// 调用 RegionDetector.buildExamSheet 构建 AnswerSheet
		return RegionDetector.buildExamSheet(
				imgW, imgH, sheetName,
				// 准考证号参数
				examIdDigits, examStartX, examStartY,
				examBubbleW, examBubbleH, examHGap, examVGap,
				// 选择题参数
				choiceTotal, choiceStartX, choiceStartY,
				choiceBubbleW, choiceBubbleH, choiceHGap, choiceVGap,
				// 选择题布局结构
				choiceRows, choiceColsPerRow, choiceRowStartYs, choiceColStartXs,
				choiceQuestionsPerCol, choiceOptionCount,
				// 填空题参数
				fillBlankCount, fillStartX, fillStartY,
				fillBoxW, fillBoxH,
				// 正确答案
				correctAnswers
		);
	}

	private static Mat buildCornerMat(Point tl, Point tr, Point br, Point bl) {
		Mat mat = new Mat(4, 1, opencv_core.CV_32FC2);
		org.bytedeco.javacpp.indexer.FloatIndexer idx = mat.createIndexer();
		idx.put(0, 0, (float) tl.x(), (float) tl.y());
		idx.put(1, 0, (float) tr.x(), (float) tr.y());
		idx.put(2, 0, (float) br.x(), (float) br.y());
		idx.put(3, 0, (float) bl.x(), (float) bl.y());
		idx.release();
		return mat;
	}

	// ==================== 辅助方法：一维数值聚类 ====================
	private static List<Integer> clusterValues(int[] values, int tolerance) {
		if (values.length == 0) return Collections.emptyList();
		int[] sorted = values.clone();
		Arrays.sort(sorted);
		List<Integer> clusters = new ArrayList<>();
		List<Integer> group = new ArrayList<>();
		group.add(sorted[0]);
		for (int i = 1; i < sorted.length; i++) {
			if (sorted[i] - sorted[i - 1] <= tolerance) {
				group.add(sorted[i]);
			} else {
				clusters.add(group.get(group.size() / 2)); // 取中位数为代表值
				group.clear();
				group.add(sorted[i]);
			}
		}
		clusters.add(group.get(group.size() / 2));
		return clusters;
	}

	private static int medianInt(int[] values) {
		if (values.length == 0) return 0;
		int[] sorted = values.clone();
		Arrays.sort(sorted);
		return sorted[sorted.length / 2];
	}

	private static int inferOptionGap(List<Integer> sortedXCoords) {
		if (sortedXCoords == null || sortedXCoords.size() < 3) {
			return 0;
		}
		List<Integer> smallGaps = new ArrayList<>();
		for (int i = 1; i < sortedXCoords.size(); i++) {
			int gap = sortedXCoords.get(i) - sortedXCoords.get(i - 1);
			if (gap > 0 && gap <= 100) {
				smallGaps.add(gap);
			}
		}
		if (smallGaps.isEmpty()) {
			return sortedXCoords.get(1) - sortedXCoords.get(0);
		}
		Collections.sort(smallGaps);
		return smallGaps.get(smallGaps.size() / 2);
	}

	private static int optionCountInGroup(List<Integer> sortedXCoords, int groupStartX, int endExclusive) {
		int count = 0;
		for (int i = 0; i < endExclusive && i < sortedXCoords.size(); i++) {
			if (sortedXCoords.get(i) >= groupStartX) {
				count++;
			}
		}
		return count;
	}

	private static boolean isBubbleCandidate(DetectedRect r) {
		if (r.w > 90 || r.h > 90) return false;
		int boxArea = r.w * r.h;
		if (boxArea < 80 || boxArea > 20000) return false;
		double aspect = (double) r.w / r.h;
		return aspect >= 0.35 && aspect <= 3.2;
	}

	private static boolean isLargeRegionCandidate(Rect bbox, int imgW, int imgH) {
		int w = bbox.width();
		int h = bbox.height();
		int area = w * h;
		if (area < 50000) return false;
		if (w < 350 || h < 250) return false;
		if (w > imgW * 0.95 || h > imgH * 0.95) return false;
		double aspect = (double) w / h;
		return aspect >= 0.5 && aspect <= 8.0;
	}

	private static DetectedRect findFillBlankRegion(List<DetectedRect> largeRects, DetectedRect choiceRect) {
		DetectedRect matchedFillRect = null;
		int bestScore = -1;
		int choiceBottom = choiceRect.y + choiceRect.h;
		int choiceRight = choiceRect.x + choiceRect.w;

		for (DetectedRect r : largeRects) {
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

	private void detectAllRectanglesByThreshold(Mat grayDeskewed) {
		Mat binary = new Mat();
		opencv_imgproc.adaptiveThreshold(grayDeskewed, binary, 255,
				opencv_imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
				opencv_imgproc.THRESH_BINARY_INV,
				31, 12);

		Mat kernel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_RECT, new Size(2, 2));
		opencv_imgproc.morphologyEx(binary, binary, opencv_imgproc.MORPH_CLOSE, kernel);

		MatVector contours = new MatVector();
		Mat hierarchy = new Mat();
		opencv_imgproc.findContours(binary.clone(), contours, hierarchy,
				opencv_imgproc.RETR_LIST, opencv_imgproc.CHAIN_APPROX_SIMPLE);

		logger.info("轮廓总数: " + contours.size());
		allRects = new ArrayList<>();

		int imgW = grayDeskewed.cols();
		int imgH = grayDeskewed.rows();

		for (int i = 0; i < contours.size(); i++) {
			Rect bbox = opencv_imgproc.boundingRect(contours.get(i));
			int w = bbox.width();
			int h = bbox.height();
			int boxArea = w * h;
			int x = bbox.x();
			int y = bbox.y();

			if (isLargeRegionCandidate(bbox, imgW, imgH)) {
				allRects.add(new DetectedRect(x, y, w, h));
				continue;
			}

			if (boxArea < 80 || boxArea > 20000) continue;
			if (w > 90 || h > 90) continue;

			double aspect = (double) w / h;
			if (aspect < 0.35 || aspect > 3.2) continue;

			boolean nearEdge = x < imgW * 0.08
							   || x + w > imgW * 0.92
							   || y < imgH * 0.04
							   || y + h > imgH * 0.96;
			if (nearEdge) continue;

			Mat approx = new Mat();
			double peri = opencv_imgproc.arcLength(contours.get(i), true);
			opencv_imgproc.approxPolyDP(contours.get(i), approx, 0.06 * peri, true);
			if (approx.total() < 4 || approx.total() > 8) continue;

			allRects.add(new DetectedRect(x, y, w, h));
		}

		logger.info("保留小矩形数: " + allRects.size());
	}

	public void postProcessRects() {
		if (allRects == null || allRects.isEmpty()) {
			logger.warning("没有可处理的矩形");
			return;
		}

		// 筛选大矩形（面积>50000，用于区域框匹配）
		fillBlankCount = 0;
		fillStartX = 0;
		fillStartY = 0;
		fillBoxW = 0;
		fillBoxH = 0;

		List<DetectedRect> largeRects = new ArrayList<>();
		for (DetectedRect r : allRects) {
			if (r.w * r.h > 50000) { // 大框阈值
				largeRects.add(r);
			}
		}
		logger.info("大矩形数量: " + largeRects.size());
//		for (DetectedRect r : largeRects) {
//			logger.info(String.format("  大框: x=%d, y=%d, w=%d, h=%d", r.x, r.y, r.w, r.h));
//		}

		// ==================== 1. 准考证号区域 ====================
		if (examStartX > 0 && examStartY > 0 && examIdDigits > 0) {
			// 找出包含准考证起始坐标的大矩形
			DetectedRect matchedExamRect = null;
			int bestScore = -1;

			for (DetectedRect r : largeRects) {
				// 计算该矩形覆盖准考证区域的程度
				// 只要求包含起始坐标附近区域
				int examEndX = examStartX + examIdDigits * (examBubbleW + examHGap);
				int examEndY = examStartY + examIdDigits * (examBubbleH + examVGap);

				// 检查是否包含起始点（容差30px）
				boolean containsStart = r.x <= examStartX + 30 && r.x + r.w >= examStartX - 30
										&& r.y <= examStartY + 30 && r.y + r.h >= examStartY - 30;

				// 检查是否覆盖大部分区域（至少50%）
				int overlapX = Math.min(r.x + r.w, examEndX) - Math.max(r.x, examStartX);
				int overlapY = Math.min(r.y + r.h, examEndY) - Math.max(r.y, examStartY);
				double coverageX = (double) overlapX / (examEndX - examStartX);
				double coverageY = (double) overlapY / (examEndY - examStartY);
				double coverage = coverageX * coverageY;

				if (containsStart && coverage > 0.3 && r.w * r.h > bestScore) {
					bestScore = r.w * r.h;
					matchedExamRect = r;
				}
			}

			if (matchedExamRect != null) {
				examRegionRect = new DetectedRect(matchedExamRect.x, matchedExamRect.y, matchedExamRect.w, matchedExamRect.h);
				logger.info("✅ 准考证号区域: x=" + examRegionRect.x() + ", y=" + examRegionRect.y()
							+ ", w=" + examRegionRect.w() + ", h=" + examRegionRect.h());
			} else {
				logger.warning("⚠️ 未找到包含准考证起始坐标的大矩形");

				// 调试信息
				logger.info(String.format("  期望范围: X[%d, %d], Y[%d, %d]",
						examStartX, examStartX + examIdDigits * (examBubbleW + examHGap),
						examStartY, examStartY + examIdDigits * (examBubbleH + examVGap)));
			}
		}

		// ==================== 2. 閫夋嫨棰樺尯鍩?====================
		if (choiceRows > 0
			&& choiceRowStartYs != null && choiceRowStartYs.length > 0
			&& choiceColStartXs != null && choiceColStartXs.length > 0) {

			int firstChoiceX = choiceColStartXs[0];
			int firstChoiceY = choiceRowStartYs[0];

			DetectedRect matchedChoiceRect = null;
			int bestScore = -1;

			for (DetectedRect r : largeRects) {
				if (examRegionRect != null
					&& r.x == examRegionRect.x()
					&& r.y == examRegionRect.y()
					&& r.w == examRegionRect.w()
					&& r.h == examRegionRect.h()) {
					continue;
				}

				boolean containsFirstChoice =
						r.x <= firstChoiceX + 30 && r.x + r.w >= firstChoiceX - 30
						&& r.y <= firstChoiceY + 30 && r.y + r.h >= firstChoiceY - 30;

				// examVGap 现在是中心距；最后一个数字框 top-left = startY + 9 * examVGap
				int examBottomY = examStartY + 9 * examVGap + examBubbleH;
				boolean belowExam = r.y > examBottomY - 80;

				if (containsFirstChoice && belowExam && r.w * r.h > bestScore) {
					bestScore = r.w * r.h;
					matchedChoiceRect = r;
				}
			}

			if (matchedChoiceRect != null) {
				choiceRegionRect = new Rect(
						matchedChoiceRect.x,
						matchedChoiceRect.y,
						matchedChoiceRect.w,
						matchedChoiceRect.h
				);
				logger.info("✅ 选择题区域: x=" + choiceRegionRect.x()
							+ ", y=" + choiceRegionRect.y()
							+ ", w=" + choiceRegionRect.width()
							+ ", h=" + choiceRegionRect.height());
				DetectedRect matchedFillRect = findFillBlankRegion(largeRects, matchedChoiceRect);
				if (matchedFillRect != null) {
					fillStartX = matchedFillRect.x;
					fillStartY = matchedFillRect.y;
					fillBoxW = matchedFillRect.w;
					fillBoxH = matchedFillRect.h;
					fillBlankCount = 1;
				}
				trimAndNormalizeChoiceLayout(choiceRegionRect);
			} else {
				logger.warning("⚠️ 未找到包含选择题起始坐标的大矩形");
			}
		}

		// ==================== 3. 打印结果 ====================
		logger.info("========== 区域大矩形汇总 ==========");
		if (examRegionRect != null) {
			logger.info(String.format("准考证号区域: (%d, %d) 大小=%d×%d",
					examRegionRect.x(), examRegionRect.y(),
					examRegionRect.w(), examRegionRect.h()));
		}
		if (choiceRegionRect != null) {
			logger.info(String.format("选择题区域: (%d, %d) 大小=%d×%d",
					choiceRegionRect.x(), choiceRegionRect.y(),
					choiceRegionRect.width(), choiceRegionRect.height()));
		}
		logger.info("=====================================");
	}

	private void trimAndNormalizeChoiceLayout(Rect validChoiceRegion) {
		if (validChoiceRegion == null
			|| choiceRows <= 0
			|| choiceColsPerRow == null
			|| choiceRowStartYs == null
			|| choiceColStartXs == null
			|| choiceQuestionsPerCol == null) {
			return;
		}

		int oldTotal = Arrays.stream(choiceQuestionsPerCol).sum();
		List<Integer> rowStarts = new ArrayList<>();
		List<Integer> colsPerRow = new ArrayList<>();
		List<Integer> colStarts = new ArrayList<>();
		List<Integer> questionsPerCol = new ArrayList<>();
		int regionBottom = validChoiceRegion.y() + validChoiceRegion.height();
		int sourceIndex = 0;

		for (int row = 0; row < choiceRows; row++) {
			int rowY = choiceRowStartYs[row];
			List<Integer> keptColStarts = new ArrayList<>();
			List<Integer> keptCounts = new ArrayList<>();
			for (int col = 0; col < choiceColsPerRow[row] && sourceIndex < choiceQuestionsPerCol.length; col++) {
				int colX = choiceColStartXs[sourceIndex];
				int count = choiceQuestionsPerCol[sourceIndex];
				sourceIndex++;
				if (count <= 0 || colX < validChoiceRegion.x() - choiceBubbleW
					|| colX > validChoiceRegion.x() + validChoiceRegion.width()
					|| rowY < validChoiceRegion.y() - choiceBubbleH
					|| rowY >= regionBottom) {
					continue;
				}
				int visibleCount = 0;
				for (int q = 0; q < count; q++) {
					int questionY = rowY + q * choiceVGap;
					if (questionY >= validChoiceRegion.y() - choiceBubbleH && questionY < regionBottom) {
						visibleCount++;
					}
				}
				if (visibleCount > 0) {
					keptColStarts.add(colX);
					keptCounts.add(visibleCount);
				}
			}
			if (keptCounts.isEmpty()) {
				continue;
			}
			int normalizedCount = dominantCount(keptCounts);
			rowStarts.add(rowY);
			colsPerRow.add(keptCounts.size());
			for (int i = 0; i < keptCounts.size(); i++) {
				colStarts.add(keptColStarts.get(i));
				questionsPerCol.add(Math.max(keptCounts.get(i), normalizedCount));
			}
		}

		if (questionsPerCol.isEmpty()) {
			return;
		}

		this.choiceRows = rowStarts.size();
		this.choiceRowStartYs = rowStarts.stream().mapToInt(Integer::intValue).toArray();
		this.choiceColsPerRow = colsPerRow.stream().mapToInt(Integer::intValue).toArray();
		this.choiceColStartXs = colStarts.stream().mapToInt(Integer::intValue).toArray();
		this.choiceQuestionsPerCol = questionsPerCol.stream().mapToInt(Integer::intValue).toArray();
		int newTotal = Arrays.stream(choiceQuestionsPerCol).sum();
		if (newTotal != oldTotal) {
			logger.info("选择题布局已按选择题大框修正: " + oldTotal + " -> " + newTotal);
		}
	}

	private static int dominantCount(List<Integer> counts) {
		if (counts == null || counts.isEmpty()) {
			return 0;
		}
		Map<Integer, Integer> frequencies = new HashMap<>();
		for (int count : counts) {
			frequencies.put(count, frequencies.getOrDefault(count, 0) + 1);
		}
		return frequencies.entrySet().stream()
				.max(Comparator.<Map.Entry<Integer, Integer>>comparingInt(Map.Entry::getValue)
						.thenComparingInt(Map.Entry::getKey))
				.map(Map.Entry::getKey)
				.orElse(0);
	}

	private static Mat deskewForLayout(Mat gray) {
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
			sortCorners(paperCorners);
			Mat srcMat = buildCornerMat(paperCorners[0], paperCorners[1], paperCorners[2], paperCorners[3]);
			Mat dstMat = buildCornerMat(new Point(0, 0), new Point(outW - 1, 0), new Point(outW - 1, outH - 1), new Point(0, outH - 1));
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

	private static void sortCorners(Point[] corners) {
		double cx = 0, cy = 0;
		for (Point p : corners) {
			cx += p.x();
			cy += p.y();
		}
		cx /= 4;
		cy /= 4;
		final double cxx = cx, cyy = cy;
		Arrays.sort(corners, Comparator.comparingDouble(a -> Math.atan2(a.y() - cyy, a.x() - cxx)));
		int tl = 0;
		double minSum = corners[0].x() + corners[0].y();
		for (int i = 1; i < 4; i++) {
			double s = corners[i].x() + corners[i].y();
			if (s < minSum) {
				minSum = s;
				tl = i;
			}
		}
		Point[] sorted = new Point[4];
		for (int i = 0; i < 4; i++) sorted[i] = corners[(tl + i) % 4];
		System.arraycopy(sorted, 0, corners, 0, 4);
	}
}
