package sg.qr.kiarelemb.exam.processing;

import method.qr.kiarelemb.utils.QRLoggerUtils;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import sg.qr.kiarelemb.exam.geometry.CoordinateTransform;
import sg.qr.kiarelemb.exam.model.SheetLayout;
import sg.qr.kiarelemb.exam.model.SheetQuestion;
import sg.qr.kiarelemb.exam.model.SheetTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class SheetCalibrator {
	private static final Logger logger = QRLoggerUtils.getLogger(SheetCalibrator.class);
	private static String cachedTemplatePath;
	private static Mat cachedTemplateBinary;

	private SheetCalibrator() {
	}

	public static CalibrationResult calibrate(Mat answerBinary, SheetTemplate template, SheetLayout source) {
		if (answerBinary == null || answerBinary.empty() || template == null || source == null) {
			return CalibrationResult.uncalibrated(source, template);
		}

		try {
			Mat templateBinary = templateBinary(template);
			RegistrationMarkDetector.MarkBounds templateBounds = RegistrationMarkDetector.detectMarkBounds(templateBinary);
			RegistrationMarkDetector.MarkBounds answerBounds = RegistrationMarkDetector.detectMarkBounds(answerBinary);
			if (templateBounds == null || answerBounds == null) {
				logger.warning("答卷校准失败：定位黑块不足，使用模板原始坐标。");
				return CalibrationResult.uncalibrated(source, template);
			}

			CoordinateTransform transform = CoordinateTransform.from(
					templateBounds,
					answerBounds,
					(double) templateBinary.cols() / source.getImageWidth(),
					(double) templateBinary.rows() / source.getImageHeight());

			SheetLayout calibrated = transformAnswerSheet(source, transform, answerBinary, answerBinary.cols(), answerBinary.rows());
			logger.info("答卷校准完成：scaleX=" + String.format("%.5f", transform.scaleX())
						+ ", scaleY=" + String.format("%.5f", transform.scaleY())
						+ ", offsetX=" + String.format("%.1f", transform.offsetX())
						+ ", offsetY=" + String.format("%.1f", transform.offsetY())
						+ ", marks=" + answerBounds.count());
			return new CalibrationResult(
					calibrated,
					transform.transform(template.examRegionRect()),
					transform.transform(template.choiceRegionRect()),
					transform.transform(template.fillBlankRegionRect()),
					true);
		} catch (Exception ex) {
			logger.warning("答卷校准异常，使用模板原始坐标：" + ex.getMessage());
			return CalibrationResult.uncalibrated(source, template);
		}
	}

	private static synchronized Mat templateBinary(SheetTemplate template) {
		String path = template.pictureFile().getAbsolutePath();
		if (cachedTemplateBinary == null || cachedTemplateBinary.empty() || !path.equals(cachedTemplatePath)) {
			if (cachedTemplateBinary != null) {
				cachedTemplateBinary.release();
			}
			cachedTemplatePath = path;
			cachedTemplateBinary = SheetImagePreprocessor.preprocess(template.pictureFile());
		}
		return cachedTemplateBinary;
	}

	private static SheetLayout transformAnswerSheet(SheetLayout source, CoordinateTransform transform, Mat answerBinary, int imgW, int imgH) {
		List<SheetQuestion> questions = new ArrayList<>();
		for (SheetQuestion question : source.getQuestions()) {
			List<Rect> regions = new ArrayList<>();
			for (Rect rect : question.optionRegions()) {
				regions.add(BinaryRegionAnalyzer.clamp(transform.transform(rect), imgW, imgH));
			}
			questions.add(new SheetQuestion(question.number(), question.type(), regions, question.correctAnswer()));
		}
		alignChoiceColumnFirstRows(questions, answerBinary, imgW, imgH);
		return new SheetLayout(source.getName(), imgW, imgH, source.getExamIdDigits(), questions);
	}

	private static void alignChoiceColumnFirstRows(List<SheetQuestion> questions, Mat answerBinary, int imgW, int imgH) {
		List<List<Integer>> columnGroups = new ArrayList<>();
		List<Integer> group = new ArrayList<>();
		int groupX = Integer.MIN_VALUE;
		int lastY = Integer.MIN_VALUE;
		for (int i = 0; i < questions.size(); i++) {
			SheetQuestion question = questions.get(i);
			if (question.type() != SheetQuestion.QuestionType.SINGLE_CHOICE || question.optionRegions().isEmpty()) {
				continue;
			}
			Rect first = question.optionRegions().get(0);
			boolean sameColumn = !group.isEmpty()
								 && Math.abs(first.x() - groupX) <= Math.max(30, first.width() * 2)
								 && first.y() > lastY;
			if (!sameColumn) {
				if (!group.isEmpty()) {
					columnGroups.add(new ArrayList<>(group));
				}
				group.clear();
				groupX = first.x();
			}
			group.add(i);
			lastY = first.y();
		}
		if (!group.isEmpty()) {
			columnGroups.add(new ArrayList<>(group));
		}
		alignChoiceRowGroups(questions, columnGroups, answerBinary, imgW, imgH);
	}

	private static void alignChoiceRowGroups(List<SheetQuestion> questions, List<List<Integer>> columnGroups,
											 Mat answerBinary, int imgW, int imgH) {
		List<List<List<Integer>>> rowGroups = new ArrayList<>();
		for (List<Integer> columnGroup : columnGroups) {
			if (columnGroup.size() < 3) {
				continue;
			}
			int columnY = questions.get(columnGroup.get(0)).optionRegions().get(0).y();
			List<List<Integer>> rowGroup = null;
			for (List<List<Integer>> existing : rowGroups) {
				int existingY = questions.get(existing.get(0).get(0)).optionRegions().get(0).y();
				if (Math.abs(columnY - existingY) <= 12) {
					rowGroup = existing;
					break;
				}
			}
			if (rowGroup == null) {
				rowGroup = new ArrayList<>();
				rowGroups.add(rowGroup);
			}
			rowGroup.add(columnGroup);
		}

		for (List<List<Integer>> rowGroup : rowGroups) {
			alignChoiceRowGroup(questions, rowGroup, answerBinary, imgW, imgH);
		}
	}

	private static void alignChoiceRowGroup(List<SheetQuestion> questions, List<List<Integer>> rowGroup,
											Mat answerBinary, int imgW, int imgH) {
		if (rowGroup.isEmpty()) {
			return;
		}
		int rowGap = estimateRowGap(questions, rowGroup.get(0));
		if (rowGap <= 0) {
			return;
		}

		int bestOffset = 0;
		double currentScore = rowGroupScore(questions, rowGroup, answerBinary, 0);
		double bestScore = currentScore;
		for (int offset : new int[]{-2 * rowGap, -rowGap, rowGap}) {
			double score = rowGroupScore(questions, rowGroup, answerBinary, offset);
			if (score > bestScore) {
				bestScore = score;
				bestOffset = offset;
			}
		}
		if (bestOffset == 0 || bestScore < currentScore + 0.03 || bestScore < 0.16) {
			return;
		}

		int firstNumber = Integer.MAX_VALUE;
		int lastNumber = Integer.MIN_VALUE;
		for (List<Integer> columnGroup : rowGroup) {
			for (int index : columnGroup) {
				SheetQuestion question = questions.get(index);
				firstNumber = Math.min(firstNumber, question.number());
				lastNumber = Math.max(lastNumber, question.number());
				List<Rect> shifted = new ArrayList<>();
				for (Rect rect : question.optionRegions()) {
					shifted.add(BinaryRegionAnalyzer.clamp(new Rect(rect.x(), rect.y() + bestOffset, rect.width(), rect.height()), imgW, imgH));
				}
				questions.set(index, new SheetQuestion(question.number(), question.type(), shifted, question.correctAnswer()));
			}
		}
		logger.info("选择题大行纵向校正：题号 " + firstNumber + "-" + lastNumber
					+ " 偏移 " + bestOffset + "px"
					+ "，原评分=" + String.format("%.2f", currentScore)
					+ "，新评分=" + String.format("%.2f", bestScore));
	}

	private static double rowGroupScore(List<SheetQuestion> questions, List<List<Integer>> rowGroup, Mat binary, int yOffset) {
		double total = 0;
		int count = 0;
		for (List<Integer> columnGroup : rowGroup) {
			total += columnScore(questions, columnGroup, binary, yOffset);
			count++;
		}
		return count == 0 ? 0 : total / count;
	}

	private static void alignChoiceColumnGroup(List<SheetQuestion> questions, List<Integer> group, Mat answerBinary, int imgW, int imgH) {
		if (group.size() < 3) {
			return;
		}
		SheetQuestion firstQuestion = questions.get(group.get(0));
		SheetQuestion lastQuestion = questions.get(group.get(group.size() - 1));
		int rowGap = estimateRowGap(questions, group);
		if (rowGap <= 0) {
			return;
		}

		int bestOffset = 0;
		double currentScore = columnScore(questions, group, answerBinary, 0);
		double bestScore = currentScore;
		for (int offset : new int[]{-2 * rowGap, -rowGap, rowGap}) {
			double score = columnScore(questions, group, answerBinary, offset);
			if (score > bestScore) {
				bestScore = score;
				bestOffset = offset;
			}
		}
		if (bestOffset == 0 || bestScore < currentScore + 0.04 || bestScore < 0.18) {
			return;
		}

		for (int index : group) {
			SheetQuestion question = questions.get(index);
			List<Rect> shifted = new ArrayList<>();
			for (Rect rect : question.optionRegions()) {
				shifted.add(BinaryRegionAnalyzer.clamp(new Rect(rect.x(), rect.y() + bestOffset, rect.width(), rect.height()), imgW, imgH));
			}
			questions.set(index, new SheetQuestion(question.number(), question.type(), shifted, question.correctAnswer()));
		}
		logger.info("选择题小列纵向校正：题号 " + firstQuestion.number() + "-" + lastQuestion.number()
					+ " 偏移 " + bestOffset + "px"
					+ "，原评分=" + String.format("%.2f", currentScore)
					+ "，新评分=" + String.format("%.2f", bestScore));
	}

	private static double columnScore(List<SheetQuestion> questions, List<Integer> group, Mat binary, int yOffset) {
		double total = 0;
		for (int index : group) {
			total += rowStructureScore(binary, questions.get(index).optionRegions(), yOffset);
		}
		return total / group.size();
	}

	private static double rowStructureScore(Mat binary, List<Rect> optionRects, int yOffset) {
		double total = 0;
		for (Rect rect : optionRects) {
			total += BinaryRegionAnalyzer.whiteRatio(binary,
					BinaryRegionAnalyzer.inflate(new Rect(rect.x(), rect.y() + yOffset, rect.width(), rect.height()), 3));
		}
		return total / optionRects.size();
	}

	private static int estimateRowGap(List<SheetQuestion> questions, List<Integer> group) {
		List<Integer> gaps = new ArrayList<>();
		for (int i = 1; i < group.size(); i++) {
			Rect previous = questions.get(group.get(i - 1)).optionRegions().get(0);
			Rect current = questions.get(group.get(i)).optionRegions().get(0);
			int gap = current.y() - previous.y();
			if (gap > 0) {
				gaps.add(gap);
			}
		}
		if (gaps.isEmpty()) {
			return 0;
		}
		gaps.sort(Integer::compareTo);
		return gaps.get(gaps.size() / 2);
	}

	private static double rowScore(Mat binary, List<Rect> optionRects, int yOffset) {
		double best = 0;
		for (Rect rect : optionRects) {
			best = Math.max(best, BinaryRegionAnalyzer.whiteRatio(binary,
					BinaryRegionAnalyzer.inflate(new Rect(rect.x(), rect.y() + yOffset, rect.width(), rect.height()), 3)));
		}
		return best;
	}

	public record CalibrationResult(SheetLayout answerSheet, Rect examRegionRect, Rect choiceRegionRect,
									Rect fillBlankRegionRect, boolean calibrated) {
		private static CalibrationResult uncalibrated(SheetLayout source, SheetTemplate template) {
			return new CalibrationResult(
					source,
					template == null ? null : copy(template.examRegionRect()),
					template == null ? null : copy(template.choiceRegionRect()),
					template == null ? null : copy(template.fillBlankRegionRect()),
					false);
		}
	}

	private static Rect copy(Rect rect) {
		return rect == null ? null : new Rect(rect.x(), rect.y(), rect.width(), rect.height());
	}
}
