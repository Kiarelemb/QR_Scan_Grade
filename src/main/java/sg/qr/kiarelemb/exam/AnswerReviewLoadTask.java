package sg.qr.kiarelemb.exam;

import org.bytedeco.opencv.opencv_core.Mat;
import sg.qr.kiarelemb.exam.model.GradingOutcome;
import sg.qr.kiarelemb.exam.model.GradingProject;
import sg.qr.kiarelemb.exam.model.SheetLayout;
import sg.qr.kiarelemb.exam.model.SheetTemplate;
import sg.qr.kiarelemb.exam.processing.ObjectiveAnswerGrader;
import sg.qr.kiarelemb.exam.processing.SheetCalibrator;
import sg.qr.kiarelemb.exam.processing.SheetImagePreprocessor;
import swing.qr.kiarelemb.task.QRTask;
import swing.qr.kiarelemb.task.QRTaskContext;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class AnswerReviewLoadTask implements QRTask<AnswerReviewLoadTask.Result> {
	private static final Map<CacheKey, CachedAnalysis> ANALYSIS_CACHE = new ConcurrentHashMap<>();

	private final GradingProject project;
	private final SheetTemplate template;
	private final SheetLayout answerSheet;
	private final ObjectiveAnswerGrader grader;
	private final File answerFile;
	private final int answerIndex;

	AnswerReviewLoadTask(GradingProject project, SheetTemplate template, SheetLayout answerSheet,
						 ObjectiveAnswerGrader grader, File answerFile, int answerIndex) {
		this.project = project;
		this.template = template;
		this.answerSheet = answerSheet;
		this.grader = grader;
		this.answerFile = answerFile;
		this.answerIndex = answerIndex;
	}

	@Override
	public Result run(QRTaskContext context) throws Exception {
		CacheKey key = CacheKey.from(answerFile, template);
		GradingProject.ReviewedAnswer reviewedAnswer = project.reviewedAnswerFor(answerFile);
		CachedAnalysis cached = ANALYSIS_CACHE.get(key);
		Result cachedResult = cachedResult(cached, reviewedAnswer);
		if (cachedResult != null) {
			context.message("正在使用已缓存的答卷分析...");
			return cachedResult;
		}

		context.message("正在读取答卷图片...");
		BufferedImage image = ImageIO.read(answerFile);
		context.checkCancelled();
		if (image == null) {
			throw new IOException("不支持的图片格式或图片已损坏。");
		}
		context.message("正在预处理答卷...");
		Mat binary = SheetImagePreprocessor.preprocess(answerFile);
		context.checkCancelled();
		context.message("正在校准答卷...");
		SheetCalibrator.CalibrationResult calibration = SheetCalibrator.calibrate(binary, template, answerSheet);
		context.checkCancelled();
		SheetLayout loadedAnswerSheet = calibration.answerSheet();
		SheetTemplate currentTemplate = new SheetTemplate(
				template.name(),
				template.pictureFile(),
				loadedAnswerSheet,
				calibration.examRegionRect(),
				calibration.choiceRegionRect(),
				calibration.fillBlankRegionRect(),
				template.defaultScoreRules(),
				template.pageCount());
		GradingOutcome recognizedResult = null;
		GradingProject.ReviewedAnswer resultReview = reviewedAnswer;
		if (reviewedAnswer == null) {
			context.message("正在识别客观题...");
			recognizedResult = withFixedExamIdPrefix(
					grader.grade(binary, loadedAnswerSheet, loadedAnswerSheet.getChoiceLabels()), loadedAnswerSheet);
		} else if (!savedExamIdUsable(reviewedAnswer.examineeId())) {
			context.message("正在复核准考证号...");
			recognizedResult = withFixedExamIdPrefix(
					grader.grade(binary, loadedAnswerSheet, loadedAnswerSheet.getChoiceLabels()), loadedAnswerSheet);
			if (recognizedResult != null && savedExamIdUsable(recognizedResult.examineeId())) {
				resultReview = new GradingProject.ReviewedAnswer(recognizedResult.examineeId(), reviewedAnswer.answers());
			}
		}
		ANALYSIS_CACHE.put(key, new CachedAnalysis(image, currentTemplate, loadedAnswerSheet, recognizedResult));
		return new Result(answerIndex, answerFile, image, currentTemplate, loadedAnswerSheet,
				recognizedResult, resultReview, reviewedAnswer == null);
	}

	private Result cachedResult(CachedAnalysis cached, GradingProject.ReviewedAnswer reviewedAnswer) {
		if (cached == null) {
			return null;
		}
		GradingOutcome recognizedResult = null;
		GradingProject.ReviewedAnswer resultReview = reviewedAnswer;
		boolean newRecognition = false;
		if (reviewedAnswer == null) {
			if (cached.recognizedResult() == null) {
				return null;
			}
			recognizedResult = cached.recognizedResult();
			newRecognition = true;
		} else if (!savedExamIdUsable(reviewedAnswer.examineeId())) {
			if (cached.recognizedResult() == null) {
				return null;
			}
			recognizedResult = cached.recognizedResult();
			if (savedExamIdUsable(recognizedResult.examineeId())) {
				resultReview = new GradingProject.ReviewedAnswer(recognizedResult.examineeId(), reviewedAnswer.answers());
			}
		}
		return new Result(answerIndex, answerFile, cached.image(), cached.template(), cached.answerSheet(),
				recognizedResult, resultReview, newRecognition);
	}

	private boolean savedExamIdUsable(String examineeId) {
		String value = examineeId == null ? "" : examineeId.trim();
		if (value.isEmpty()) {
			return false;
		}
		Map<String, String> names = project.studentNamesByExamId();
		return names == null || names.isEmpty() || names.containsKey(value);
	}

	private GradingOutcome withFixedExamIdPrefix(GradingOutcome result, SheetLayout sheetLayout) {
		if (result == null) {
			return null;
		}
		String fixedPrefix = project.examIdPrefix();
		if (fixedPrefix.isBlank()) {
			return result;
		}
		String recognized = result.examineeId() == null ? "" : result.examineeId().trim();
		String suffix = recognized.length() > fixedPrefix.length() ? recognized.substring(fixedPrefix.length()) : "";
		int expectedDigits = sheetLayout == null ? 0 : sheetLayout.getExamIdDigits();
		int suffixDigits = expectedDigits <= 0 ? suffix.length() : Math.max(0, expectedDigits - fixedPrefix.length());
		if (suffix.length() > suffixDigits) {
			suffix = suffix.substring(suffix.length() - suffixDigits);
		}
		while (suffix.length() < suffixDigits) {
			suffix += "?";
		}
		String corrected = fixedPrefix + suffix;
		if (corrected.equals(recognized)) {
			return result;
		}
		return new GradingOutcome(corrected, result.sheetName(), result.questionResults(), result.totalScore(), result.earnedScore());
	}

	record Result(int answerIndex, File answerFile, BufferedImage image, SheetTemplate template,
				  SheetLayout answerSheet, GradingOutcome recognizedResult,
				  GradingProject.ReviewedAnswer reviewedAnswer, boolean newRecognition) {
	}

	private record CachedAnalysis(BufferedImage image, SheetTemplate template, SheetLayout answerSheet,
	                              GradingOutcome recognizedResult) {
	}

	private record CacheKey(String answerPath, long answerLastModified, long answerLength,
	                        String templatePath, long templateLastModified) {
		private static CacheKey from(File answerFile, SheetTemplate template) {
			File templateFile = template.pictureFile();
			return new CacheKey(
					answerFile.getAbsolutePath(),
					answerFile.lastModified(),
					answerFile.length(),
					templateFile.getAbsolutePath(),
					templateFile.lastModified());
		}
	}
}
