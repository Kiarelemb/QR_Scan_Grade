package sg.qr.kiarelemb.grading.pipeline;

import method.qr.kiarelemb.utils.QRLoggerUtils;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Rect;
import sg.qr.kiarelemb.grading.model.AnswerSheet;
import sg.qr.kiarelemb.grading.model.Question;
import sg.qr.kiarelemb.grading.model.Template;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class AnswerSheetCalibrator {
	private static final Logger logger = QRLoggerUtils.getLogger(AnswerSheetCalibrator.class);
	private static final int MIN_MARKS = 8;
	private static String cachedTemplatePath;
	private static Mat cachedTemplateBinary;

	private AnswerSheetCalibrator() {
	}

	public static CalibrationResult calibrate(Mat answerBinary, Template template, AnswerSheet source) {
		if (answerBinary == null || answerBinary.empty() || template == null || source == null) {
			return CalibrationResult.uncalibrated(source, template);
		}

		try {
			Mat templateBinary = templateBinary(template);
			MarkBounds templateBounds = detectMarkBounds(templateBinary);
			MarkBounds answerBounds = detectMarkBounds(answerBinary);
			if (templateBounds == null || answerBounds == null) {
				logger.warning("答卷校准失败：定位黑块不足，使用模板原始坐标。");
				return CalibrationResult.uncalibrated(source, template);
			}

			CoordinateTransform transform = CoordinateTransform.from(
					templateBounds,
					answerBounds,
					(double) templateBinary.cols() / source.getImageWidth(),
					(double) templateBinary.rows() / source.getImageHeight());

			AnswerSheet calibrated = transformAnswerSheet(source, transform, answerBinary, answerBinary.cols(), answerBinary.rows());
			logger.info("答卷校准完成：scaleX=" + String.format("%.5f", transform.scaleX)
						+ ", scaleY=" + String.format("%.5f", transform.scaleY)
						+ ", offsetX=" + String.format("%.1f", transform.offsetX)
						+ ", offsetY=" + String.format("%.1f", transform.offsetY)
						+ ", marks=" + answerBounds.count);
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

	private static synchronized Mat templateBinary(Template template) {
		String path = template.pictureFile().getAbsolutePath();
		if (cachedTemplateBinary == null || cachedTemplateBinary.empty() || !path.equals(cachedTemplatePath)) {
			if (cachedTemplateBinary != null) {
				cachedTemplateBinary.release();
			}
			cachedTemplatePath = path;
			cachedTemplateBinary = ImagePreprocessor.preprocess(template.pictureFile());
		}
		return cachedTemplateBinary;
	}

	private static AnswerSheet transformAnswerSheet(AnswerSheet source, CoordinateTransform transform, Mat answerBinary, int imgW, int imgH) {
		List<Question> questions = new ArrayList<>();
		for (Question question : source.getQuestions()) {
			List<Rect> regions = new ArrayList<>();
			for (Rect rect : question.optionRegions()) {
				regions.add(clamp(transform.transform(rect), imgW, imgH));
			}
			questions.add(new Question(question.number(), question.type(), regions, question.correctAnswer()));
		}
		alignChoiceColumnFirstRows(questions, answerBinary, imgW, imgH);
		return new AnswerSheet(source.getName(), imgW, imgH, source.getExamIdDigits(), questions);
	}

	private static void alignChoiceColumnFirstRows(List<Question> questions, Mat answerBinary, int imgW, int imgH) {
		List<List<Integer>> columnGroups = new ArrayList<>();
		List<Integer> group = new ArrayList<>();
		int groupX = Integer.MIN_VALUE;
		int lastY = Integer.MIN_VALUE;
		for (int i = 0; i < questions.size(); i++) {
			Question question = questions.get(i);
			if (question.type() != Question.QuestionType.SINGLE_CHOICE || question.optionRegions().isEmpty()) {
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

	private static void alignChoiceRowGroups(List<Question> questions, List<List<Integer>> columnGroups,
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

	private static void alignChoiceRowGroup(List<Question> questions, List<List<Integer>> rowGroup,
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
				Question question = questions.get(index);
				firstNumber = Math.min(firstNumber, question.number());
				lastNumber = Math.max(lastNumber, question.number());
				List<Rect> shifted = new ArrayList<>();
				for (Rect rect : question.optionRegions()) {
					shifted.add(clamp(new Rect(rect.x(), rect.y() + bestOffset, rect.width(), rect.height()), imgW, imgH));
				}
				questions.set(index, new Question(question.number(), question.type(), shifted, question.correctAnswer()));
			}
		}
		logger.info("选择题大行纵向校正：题号 " + firstNumber + "-" + lastNumber
					+ " 偏移 " + bestOffset + "px"
					+ "，原评分=" + String.format("%.2f", currentScore)
					+ "，新评分=" + String.format("%.2f", bestScore));
	}

	private static double rowGroupScore(List<Question> questions, List<List<Integer>> rowGroup, Mat binary, int yOffset) {
		double total = 0;
		int count = 0;
		for (List<Integer> columnGroup : rowGroup) {
			total += columnScore(questions, columnGroup, binary, yOffset);
			count++;
		}
		return count == 0 ? 0 : total / count;
	}

	private static void alignChoiceColumnGroup(List<Question> questions, List<Integer> group, Mat answerBinary, int imgW, int imgH) {
		if (group.size() < 3) {
			return;
		}
		Question firstQuestion = questions.get(group.get(0));
		Question lastQuestion = questions.get(group.get(group.size() - 1));
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
			Question question = questions.get(index);
			List<Rect> shifted = new ArrayList<>();
			for (Rect rect : question.optionRegions()) {
				shifted.add(clamp(new Rect(rect.x(), rect.y() + bestOffset, rect.width(), rect.height()), imgW, imgH));
			}
			questions.set(index, new Question(question.number(), question.type(), shifted, question.correctAnswer()));
		}
		logger.info("选择题小列纵向校正：题号 " + firstQuestion.number() + "-" + lastQuestion.number()
					+ " 偏移 " + bestOffset + "px"
					+ "，原评分=" + String.format("%.2f", currentScore)
					+ "，新评分=" + String.format("%.2f", bestScore));
	}

	private static double columnScore(List<Question> questions, List<Integer> group, Mat binary, int yOffset) {
		double total = 0;
		for (int index : group) {
			total += rowStructureScore(binary, questions.get(index).optionRegions(), yOffset);
		}
		return total / group.size();
	}

	private static double rowStructureScore(Mat binary, List<Rect> optionRects, int yOffset) {
		double total = 0;
		for (Rect rect : optionRects) {
			total += fillRatio(binary, inflate(new Rect(rect.x(), rect.y() + yOffset, rect.width(), rect.height()), 3));
		}
		return total / optionRects.size();
	}

	private static int estimateRowGap(List<Question> questions, List<Integer> group) {
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
			best = Math.max(best, fillRatio(binary, inflate(new Rect(rect.x(), rect.y() + yOffset, rect.width(), rect.height()), 3)));
		}
		return best;
	}

	private static double fillRatio(Mat binary, Rect rect) {
		int x = Math.max(0, rect.x());
		int y = Math.max(0, rect.y());
		int w = Math.min(rect.width(), binary.cols() - x);
		int h = Math.min(rect.height(), binary.rows() - y);
		if (w <= 0 || h <= 0) {
			return 0;
		}
		Mat roi = binary.apply(new Rect(x, y, w, h));
		try {
			return (double) opencv_core.countNonZero(roi) / (w * h);
		} finally {
			roi.release();
		}
	}

	private static Rect inflate(Rect rect, int padding) {
		return new Rect(rect.x() - padding, rect.y() - padding,
				rect.width() + padding * 2, rect.height() + padding * 2);
	}

	private static MarkBounds detectMarkBounds(Mat binary) {
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

	private static boolean isRegistrationMark(Mat binary, Rect bbox, double imgArea) {
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

		Mat roi = binary.apply(bbox);
		try {
			double fillRatio = (double) opencv_core.countNonZero(roi) / area;
			return fillRatio > 0.65;
		} finally {
			roi.release();
		}
	}

	private static Rect clamp(Rect rect, int imgW, int imgH) {
		if (rect == null) {
			return null;
		}
		int x = Math.max(0, Math.min(rect.x(), imgW - 1));
		int y = Math.max(0, Math.min(rect.y(), imgH - 1));
		int right = Math.max(x + 1, Math.min(rect.x() + rect.width(), imgW));
		int bottom = Math.max(y + 1, Math.min(rect.y() + rect.height(), imgH));
		return new Rect(x, y, right - x, bottom - y);
	}

	public record CalibrationResult(AnswerSheet answerSheet, Rect examRegionRect, Rect choiceRegionRect,
									Rect fillBlankRegionRect, boolean calibrated) {
		private static CalibrationResult uncalibrated(AnswerSheet source, Template template) {
			return new CalibrationResult(
					source,
					template == null ? null : copy(template.examRegionRect()),
					template == null ? null : copy(template.choiceRegionRect()),
					template == null ? null : copy(template.fillBlankRegionRect()),
					false);
		}
	}

	private record MarkBounds(double minX, double minY, double maxX, double maxY, int count) {
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

	private static final class CoordinateTransform {
		private final double sourceScaleX;
		private final double sourceScaleY;
		private final double scaleX;
		private final double scaleY;
		private final double offsetX;
		private final double offsetY;

		private CoordinateTransform(double sourceScaleX, double sourceScaleY,
									double scaleX, double scaleY, double offsetX, double offsetY) {
			this.sourceScaleX = sourceScaleX;
			this.sourceScaleY = sourceScaleY;
			this.scaleX = scaleX;
			this.scaleY = scaleY;
			this.offsetX = offsetX;
			this.offsetY = offsetY;
		}

		private static CoordinateTransform from(MarkBounds source, MarkBounds target,
												double sourceScaleX, double sourceScaleY) {
			double scaleX = (target.maxX - target.minX) / (source.maxX - source.minX);
			double scaleY = (target.maxY - target.minY) / (source.maxY - source.minY);
			double offsetX = target.minX - source.minX * scaleX;
			double offsetY = target.minY - source.minY * scaleY;
			return new CoordinateTransform(sourceScaleX, sourceScaleY, scaleX, scaleY, offsetX, offsetY);
		}

		private Rect transform(Rect rect) {
			if (rect == null) {
				return null;
			}
			double x1 = transformX(rect.x());
			double y1 = transformY(rect.y());
			double x2 = transformX(rect.x() + rect.width());
			double y2 = transformY(rect.y() + rect.height());
			int x = (int) Math.round(Math.min(x1, x2));
			int y = (int) Math.round(Math.min(y1, y2));
			int w = Math.max(1, (int) Math.round(Math.abs(x2 - x1)));
			int h = Math.max(1, (int) Math.round(Math.abs(y2 - y1)));
			return new Rect(x, y, w, h);
		}

		private double transformX(int x) {
			return x * sourceScaleX * scaleX + offsetX;
		}

		private double transformY(int y) {
			return y * sourceScaleY * scaleY + offsetY;
		}
	}

	private static Rect copy(Rect rect) {
		return rect == null ? null : new Rect(rect.x(), rect.y(), rect.width(), rect.height());
	}
}
