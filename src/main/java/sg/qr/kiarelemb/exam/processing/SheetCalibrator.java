package sg.qr.kiarelemb.exam.processing;

import method.qr.kiarelemb.utils.QRLoggerUtils;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import sg.qr.kiarelemb.exam.geometry.CoordinateTransform;
import sg.qr.kiarelemb.exam.model.SheetLayout;
import sg.qr.kiarelemb.exam.model.SheetQuestion;
import sg.qr.kiarelemb.exam.model.SheetTemplate;

import java.util.ArrayList;
import java.util.Comparator;
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
			RegistrationMarkDetector.MarkSet templateMarks = RegistrationMarkDetector.detectMarks(templateBinary);
			RegistrationMarkDetector.MarkSet answerMarks = RegistrationMarkDetector.detectMarks(answerBinary);
			if (templateMarks == null || answerMarks == null) {
				logger.warning("答卷校准失败：定位黑块不足，使用模板原始坐标。");
				return CalibrationResult.uncalibrated(source, template);
			}

			double sourceScaleX = (double) templateBinary.cols() / source.getImageWidth();
			double sourceScaleY = (double) templateBinary.rows() / source.getImageHeight();
			CoordinateTransform transform = affineTransformFromMarks(
					templateMarks,
					answerMarks,
					sourceScaleX,
					sourceScaleY);
			boolean affine = transform != null;
			if (transform == null) {
				transform = CoordinateTransform.from(
					templateMarks.bounds(),
					answerMarks.bounds(),
					(double) templateBinary.cols() / source.getImageWidth(),
					(double) templateBinary.rows() / source.getImageHeight());
			}

			SheetLayout calibrated = transformAnswerSheet(source, transform, answerBinary, answerBinary.cols(), answerBinary.rows());
			logger.info("答卷校准完成：" + (affine ? "多定位黑块仿射" : "定位黑块外包框")
						+ " scaleX=" + String.format("%.5f", transform.scaleX())
						+ ", scaleY=" + String.format("%.5f", transform.scaleY())
						+ ", offsetX=" + String.format("%.1f", transform.offsetX())
						+ ", offsetY=" + String.format("%.1f", transform.offsetY())
						+ ", marks=" + answerMarks.count());
			return new CalibrationResult(
					calibrated,
					transform.transform(template.examRegionRect()),
					transform.transform(template.choiceRegionRect()),
					transform.transform(template.fillBlankRegionRect()),
					transform,
					true);
		} catch (Exception ex) {
			logger.warning("答卷校准异常，使用模板原始坐标：" + ex.getMessage());
			return CalibrationResult.uncalibrated(source, template);
		}
	}

	private static CoordinateTransform affineTransformFromMarks(RegistrationMarkDetector.MarkSet templateMarks,
															   RegistrationMarkDetector.MarkSet answerMarks,
															   double sourceScaleX,
															   double sourceScaleY) {
		List<MarkPair> pairs = matchMarks(templateMarks, answerMarks);
		if (pairs.size() < 6) {
			logger.info("定位黑块仿射校准跳过：匹配点不足 " + pairs.size());
			return null;
		}
		double[] xCoeff = solveAffineCoefficients(pairs, true);
		double[] yCoeff = solveAffineCoefficients(pairs, false);
		if (xCoeff == null || yCoeff == null) {
			logger.info("定位黑块仿射校准跳过：矩阵不可解");
			return null;
		}
		double residual = averageResidual(pairs, xCoeff, yCoeff);
		if (residual > 80) {
			logger.info("定位黑块仿射校准跳过：平均残差 " + String.format("%.2f", residual) + "px");
			return null;
		}
		logger.info("定位黑块仿射匹配：" + pairs.size() + " 点，平均残差 "
					+ String.format("%.2f", residual) + "px");
		return CoordinateTransform.affine(sourceScaleX, sourceScaleY,
				xCoeff[0], xCoeff[1], xCoeff[2],
				yCoeff[0], yCoeff[1], yCoeff[2]);
	}

	private static List<MarkPair> matchMarks(RegistrationMarkDetector.MarkSet templateMarks,
											 RegistrationMarkDetector.MarkSet answerMarks) {
		List<MarkPair> candidates = new ArrayList<>();
		RegistrationMarkDetector.MarkBounds sourceBounds = templateMarks.bounds();
		RegistrationMarkDetector.MarkBounds targetBounds = answerMarks.bounds();
		double threshold = 0.08;
		for (RegistrationMarkDetector.MarkPoint source : templateMarks.centers()) {
			for (RegistrationMarkDetector.MarkPoint target : answerMarks.centers()) {
				double distance = normalizedDistance(source, sourceBounds, target, targetBounds);
				if (distance <= threshold) {
					candidates.add(new MarkPair(source, target, distance));
				}
			}
		}
		candidates.sort(Comparator.comparingDouble(MarkPair::normalizedDistance));
		List<MarkPair> pairs = new ArrayList<>();
		List<RegistrationMarkDetector.MarkPoint> usedSources = new ArrayList<>();
		List<RegistrationMarkDetector.MarkPoint> usedTargets = new ArrayList<>();
		for (MarkPair candidate : candidates) {
			if (usedSources.contains(candidate.source()) || usedTargets.contains(candidate.target())) {
				continue;
			}
			usedSources.add(candidate.source());
			usedTargets.add(candidate.target());
			pairs.add(candidate);
		}
		return pairs;
	}

	private static double normalizedDistance(RegistrationMarkDetector.MarkPoint source,
											 RegistrationMarkDetector.MarkBounds sourceBounds,
											 RegistrationMarkDetector.MarkPoint target,
											 RegistrationMarkDetector.MarkBounds targetBounds) {
		double sx = normalize(source.x(), sourceBounds.minX(), sourceBounds.maxX());
		double sy = normalize(source.y(), sourceBounds.minY(), sourceBounds.maxY());
		double tx = normalize(target.x(), targetBounds.minX(), targetBounds.maxX());
		double ty = normalize(target.y(), targetBounds.minY(), targetBounds.maxY());
		return Math.hypot(sx - tx, sy - ty);
	}

	private static double normalize(double value, double min, double max) {
		return (value - min) / Math.max(1.0, max - min);
	}

	private static double[] solveAffineCoefficients(List<MarkPair> pairs, boolean targetX) {
		double sumX2 = 0;
		double sumXY = 0;
		double sumX = 0;
		double sumY2 = 0;
		double sumY = 0;
		double sumT = 0;
		double sumXT = 0;
		double sumYT = 0;
		for (MarkPair pair : pairs) {
			double x = pair.source().x();
			double y = pair.source().y();
			double t = targetX ? pair.target().x() : pair.target().y();
			sumX2 += x * x;
			sumXY += x * y;
			sumX += x;
			sumY2 += y * y;
			sumY += y;
			sumT += t;
			sumXT += x * t;
			sumYT += y * t;
		}
		double[][] matrix = {
				{sumX2, sumXY, sumX},
				{sumXY, sumY2, sumY},
				{sumX, sumY, pairs.size()}
		};
		double[] rhs = {sumXT, sumYT, sumT};
		return solve3x3(matrix, rhs);
	}

	private static double[] solve3x3(double[][] matrix, double[] rhs) {
		double[][] a = new double[3][4];
		for (int r = 0; r < 3; r++) {
			System.arraycopy(matrix[r], 0, a[r], 0, 3);
			a[r][3] = rhs[r];
		}
		for (int col = 0; col < 3; col++) {
			int pivot = col;
			for (int row = col + 1; row < 3; row++) {
				if (Math.abs(a[row][col]) > Math.abs(a[pivot][col])) {
					pivot = row;
				}
			}
			if (Math.abs(a[pivot][col]) < 1e-8) {
				return null;
			}
			if (pivot != col) {
				double[] tmp = a[pivot];
				a[pivot] = a[col];
				a[col] = tmp;
			}
			double divisor = a[col][col];
			for (int c = col; c < 4; c++) {
				a[col][c] /= divisor;
			}
			for (int row = 0; row < 3; row++) {
				if (row == col) {
					continue;
				}
				double factor = a[row][col];
				for (int c = col; c < 4; c++) {
					a[row][c] -= factor * a[col][c];
				}
			}
		}
		return new double[]{a[0][3], a[1][3], a[2][3]};
	}

	private static double averageResidual(List<MarkPair> pairs, double[] xCoeff, double[] yCoeff) {
		double sum = 0;
		for (MarkPair pair : pairs) {
			double x = pair.source().x();
			double y = pair.source().y();
			double predictedX = xCoeff[0] * x + xCoeff[1] * y + xCoeff[2];
			double predictedY = yCoeff[0] * x + yCoeff[1] * y + yCoeff[2];
			sum += Math.hypot(predictedX - pair.target().x(), predictedY - pair.target().y());
		}
		return sum / pairs.size();
	}

	private record MarkPair(RegistrationMarkDetector.MarkPoint source,
							RegistrationMarkDetector.MarkPoint target,
							double normalizedDistance) {
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
		if (bestOffset == 0 || bestScore < currentScore + 0.06 || bestScore < 0.20) {
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
		List<Double> ratios = new ArrayList<>();
		for (Rect rect : optionRects) {
			ratios.add(BinaryRegionAnalyzer.whiteRatio(binary,
					BinaryRegionAnalyzer.inflate(new Rect(rect.x(), rect.y() + yOffset, rect.width(), rect.height()), 3)));
		}
		if (ratios.isEmpty()) {
			return 0;
		}
		ratios.sort(Double::compareTo);
		double best = ratios.get(ratios.size() - 1);
		double second = ratios.size() < 2 ? 0 : ratios.get(ratios.size() - 2);
		double average = ratios.stream().mapToDouble(Double::doubleValue).average().orElse(0);
		return Math.max(0, best - Math.max(second, average) * 0.55);
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
									Rect fillBlankRegionRect, CoordinateTransform transform, boolean calibrated) {
		public Rect transform(Rect rect) {
			if (rect == null || transform == null) {
				return copy(rect);
			}
			int width = answerSheet == null ? Integer.MAX_VALUE : answerSheet.getImageWidth();
			int height = answerSheet == null ? Integer.MAX_VALUE : answerSheet.getImageHeight();
			return BinaryRegionAnalyzer.clamp(transform.transform(rect), width, height);
		}

		private static CalibrationResult uncalibrated(SheetLayout source, SheetTemplate template) {
			return new CalibrationResult(
					source,
					template == null ? null : copy(template.examRegionRect()),
					template == null ? null : copy(template.choiceRegionRect()),
					template == null ? null : copy(template.fillBlankRegionRect()),
					null,
					false);
		}
	}

	private static Rect copy(Rect rect) {
		return rect == null ? null : new Rect(rect.x(), rect.y(), rect.width(), rect.height());
	}
}
