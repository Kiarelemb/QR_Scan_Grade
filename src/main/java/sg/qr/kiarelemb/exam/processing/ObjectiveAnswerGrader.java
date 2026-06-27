package sg.qr.kiarelemb.exam.processing;
import method.qr.kiarelemb.utils.QRLoggerUtils;
import java.util.logging.Logger;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import sg.qr.kiarelemb.exam.model.SheetLayout;
import sg.qr.kiarelemb.exam.model.GradingOutcome;
import sg.qr.kiarelemb.exam.model.SheetQuestion;

import java.util.*;

/**
 * @author Kiarelemb
 * @projectName QR_ScanGrade
 * @className ObjectiveAnswerGrader
 * @description TODO
 * @create 2026/5/31 14:10
 */
public class ObjectiveAnswerGrader {
	private static final Logger logger = QRLoggerUtils.getLogger(ObjectiveAnswerGrader.class);
	private final BubbleMarkReader bubbleMarkReader;

	public ObjectiveAnswerGrader(BubbleMarkReader bubbleMarkReader) {
		this.bubbleMarkReader = bubbleMarkReader;
	}

	/**
	 * 对一张答题卡进行完整批改。
	 *
	 * @param binary      校正后的二值图
	 * @param answerSheet 答题卡布局（含正确答案）
	 * @param choiceMap   选择题选项序号→标签映射（如 0→"A", 1→"B"）
	 * @return 评分结果
	 */
	public GradingOutcome grade(Mat binary, SheetLayout answerSheet, String[] choiceMap) {

		List<GradingOutcome.QuestionResult> results = new ArrayList<>();
		StringBuilder examIdBuilder = new StringBuilder();

		int totalScore = 0;
		int earnedScore = 0;

		for (int i = 0; i < answerSheet.getExamIdQuestions().size(); i++) {
			var q = answerSheet.getExamIdQuestions().get(i);
			DetectedDigit digit = detectExamIdDigit(binary, q.optionRegions());
			examIdBuilder.append(digit.ratio() >= BubbleMarkReader.DEFAULT_FILL_THRESHOLD ? digit.digit() : "?");
		}

		// 处理选择题和填空题
		for (SheetQuestion question : answerSheet.getQuestions()) {
			switch (question.type()) {
				case SINGLE_CHOICE -> {
					totalScore++;
					String detected = detectBestOption(binary, question.optionRegions(), choiceMap);
					boolean uncertain = isUncertain(binary, question.optionRegions());
					boolean correct = detected != null && detected.equals(question.correctAnswer());
					if (correct) earnedScore++;

					results.add(new GradingOutcome.QuestionResult(
							question.number(), SheetQuestion.QuestionType.SINGLE_CHOICE,
							question.correctAnswer(),
							detected != null ? detected : "未填",
							correct, uncertain));
				}
				case FILL_BLANK -> {
					totalScore++;
					results.add(new GradingOutcome.QuestionResult(
							question.number(), SheetQuestion.QuestionType.FILL_BLANK,
							"[待OCR]",
							"[待OCR]",
							false, true));
				}
			}
		}

		String examId = examIdBuilder.toString();
		long uncertainCount = results.stream().filter(r -> r.uncertain()).count();
		logger.info("批阅: " + answerSheet.getName() + " 考号=" + examId + " 得分=" + earnedScore + "/" + totalScore + " 不确定=" + uncertainCount);
		return new GradingOutcome(examId, answerSheet.getName(),
				results, totalScore, earnedScore);
	}

	private DetectedDigit detectExamIdDigit(Mat binary, List<Rect> regions) {
		double max = 0;
		int maxNum = -1;
		for (int digit = 0; digit < regions.size(); digit++) {
			double ratio = bubbleMarkReader.getFillRatio(binary, regions.get(digit));
			if (ratio > max) {
				max = ratio;
				maxNum = digit;
			}
		}
		return new DetectedDigit(maxNum, max);
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
			double ratio = bubbleMarkReader.getFillRatio(binary, regions.get(i));
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
			ratios.add(bubbleMarkReader.getFillRatio(binary, r));
		}
		ratios.sort(Collections.reverseOrder());

		return (ratios.get(0) - ratios.get(1)) < 0.10;
	}
}
