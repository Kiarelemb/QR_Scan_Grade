package sg.qr.kiarelemb.grading.pipeline;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import sg.qr.kiarelemb.grading.model.AnswerSheet;
import sg.qr.kiarelemb.grading.model.GradingResult;
import sg.qr.kiarelemb.grading.model.Question;

import java.util.*;

/**
 * @author Kiarelemb
 * @projectName QR_ScanGrade
 * @className AnswerComparator
 * @description TODO
 * @create 2026/5/31 14:10
 */
public class AnswerComparator {
	private final ChoiceReader choiceReader;

	public AnswerComparator(ChoiceReader choiceReader) {
		this.choiceReader = choiceReader;
	}

	/**
	 * 计算矩形区域内的白色像素占比
	 */
	private static double getWhitePixelRatio(Mat binary, org.bytedeco.opencv.opencv_core.Rect rect) {
		// 边界检查：确保ROI不超出图像范围
		int imgW = binary.cols();
		int imgH = binary.rows();

		int x = Math.max(0, rect.x());
		int y = Math.max(0, rect.y());
		int w = Math.min(rect.width(), imgW - x);
		int h = Math.min(rect.height(), imgH - y);

		// 如果裁剪后无效，返回0
		if (w <= 0 || h <= 0) {
			return 0.0;
		}

		// 提取ROI区域
		Mat roi = new Mat(binary, new org.bytedeco.opencv.opencv_core.Rect(x, y, w, h));
		double totalPixels = w * h;
		double whitePixels = opencv_core.countNonZero(roi);
		return whitePixels / totalPixels;
	}

	/**
	 * 对一张答题卡进行完整批改。
	 *
	 * @param binary      校正后的二值图
	 * @param answerSheet 答题卡布局（含正确答案）
	 * @param choiceMap   选择题选项序号→标签映射（如 0→"A", 1→"B"）
	 * @return 评分结果
	 */
	public GradingResult grade(Mat binary, AnswerSheet answerSheet, String[] choiceMap) {

		List<GradingResult.QuestionResult> results = new ArrayList<>();
		StringBuilder examIdBuilder = new StringBuilder();

		int totalScore = 0;
		int earnedScore = 0;

		for (int i = 0; i < answerSheet.getExamIdQuestions().size(); i++) {
			var q = answerSheet.getExamIdQuestions().get(i);
			DetectedDigit digit = detectExamIdDigit(binary, q.optionRegions());
			examIdBuilder.append(digit.ratio() >= ChoiceReader.DEFAULT_FILL_THRESHOLD ? digit.digit() : "?");
		}

		// 处理选择题和填空题
		for (Question question : answerSheet.getQuestions()) {
			switch (question.type()) {
				case SINGLE_CHOICE -> {
					totalScore++;
					String detected = detectBestOption(binary, question.optionRegions(), choiceMap);
					boolean uncertain = isUncertain(binary, question.optionRegions());
					boolean correct = detected != null && detected.equals(question.correctAnswer());
					if (correct) earnedScore++;

					results.add(new GradingResult.QuestionResult(
							question.number(), Question.QuestionType.SINGLE_CHOICE,
							question.correctAnswer(),
							detected != null ? detected : "未填",
							correct, uncertain));
				}
				case FILL_BLANK -> {
					totalScore++;
					results.add(new GradingResult.QuestionResult(
							question.number(), Question.QuestionType.FILL_BLANK,
							"[待OCR]",
							"[待OCR]",
							false, true));
				}
			}
		}

		return new GradingResult(examIdBuilder.toString(), answerSheet.getName(),
				results, totalScore, earnedScore);
	}

	private DetectedDigit detectExamIdDigit(Mat binary, List<Rect> regions) {
		List<Rect> digitRegions = examDigitRegions(regions);
		double max = 0;
		int maxNum = -1;
		for (int digit = 0; digit < digitRegions.size(); digit++) {
			double ratio = choiceReader.getFillRatio(binary, digitRegions.get(digit));
			if (ratio > max) {
				max = ratio;
				maxNum = digit;
			}
		}
		return new DetectedDigit(maxNum, max);
	}

	private List<Rect> examDigitRegions(List<Rect> regions) {
		if (regions == null || regions.size() != 10) {
			return regions == null ? List.of() : regions;
		}
		List<Rect> shifted = new ArrayList<>();
		for (int i = 1; i < regions.size(); i++) {
			shifted.add(regions.get(i));
		}
		Rect previous = regions.get(regions.size() - 2);
		Rect last = regions.get(regions.size() - 1);
		int dy = last.y() - previous.y();
		shifted.add(new Rect(last.x(), last.y() + dy, last.width(), last.height()));
		return shifted;
	}

	private record DetectedDigit(int digit, double ratio) {
	}



	/**
	 * 在一组选项中找出填涂比例最高的那个。
	 * 若所有选项都低于阈值，返回 null（未填涂）。
	 * 若有多个超过阈值，取比例最高的（同时标记 uncertain）。
	 */
	private String detectBestOption(Mat binary, List<Rect> regions, String[] labels) {
		double bestRatio = 0;
		int bestIndex = -1;

		for (int i = 0; i < regions.size(); i++) {
			double ratio = choiceReader.getFillRatio(binary, regions.get(i));
			if (ratio > bestRatio) {
				bestRatio = ratio;
				bestIndex = i;
			}
		}

		if (bestIndex < 0 || bestRatio < 0.15) {
			return null;
		}
		return bestIndex < labels.length ? labels[bestIndex] : "?";
	}



	/**
	 * 判断是否存疑：最佳选项和次佳选项比例接近（差距 < 10%）时标记不确定。
	 */
	private boolean isUncertain(Mat binary, List<Rect> regions) {
		if (regions.size() < 2) return false;

		List<Double> ratios = new ArrayList<>();
		for (Rect r : regions) {
			ratios.add(choiceReader.getFillRatio(binary, r));
		}
		ratios.sort(Collections.reverseOrder());

		return (ratios.get(0) - ratios.get(1)) < 0.10;
	}
}
