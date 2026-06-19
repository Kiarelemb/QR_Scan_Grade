package sg.qr.kiarelemb.test;

import method.qr.kiarelemb.utils.QRRandomUtils;
import org.bytedeco.opencv.opencv_core.Rect;
import sg.qr.kiarelemb.grading.layout.LayoutDetector;
import sg.qr.kiarelemb.grading.model.AnswerSheet;
import sg.qr.kiarelemb.grading.model.Question;
import sg.qr.kiarelemb.grading.model.Template;
import sg.qr.kiarelemb.grading.pipeline.TemplateProcessor;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AnswerSheetTestGenerator {
	private static final int DEFAULT_START_ID = 202606001;
	private static final int DEFAULT_END_ID = 202606039;
	private static final int IMAGE_WIDTH = 2480;
	private static final int IMAGE_HEIGHT = 3507;

	public static void main(String[] args) throws Exception {
		Template template = TemplateProcessor.load(new File("F:\\IdeaProjects\\QR_Scan_Grade\\sg\\小测答题卡.sg"));
		generate(template, new File("F:\\ans"), "answer_sheet_");
	}

	public static void generate(Template template, File outputDirectory, String filePrefix) throws IOException {
		generate(template, outputDirectory, filePrefix, DEFAULT_START_ID, DEFAULT_END_ID);
	}

	public static void generate(Template template, File outputDirectory, String filePrefix, int startExamId, int endExamId) throws IOException {
		if (template == null) {
			throw new IllegalArgumentException("template must not be null");
		}
		if (outputDirectory == null) {
			throw new IllegalArgumentException("outputDirectory must not be null");
		}
		if (filePrefix == null || filePrefix.isBlank()) {
			throw new IllegalArgumentException("filePrefix must not be blank");
		}
		if (startExamId > endExamId) {
			throw new IllegalArgumentException("startExamId must be <= endExamId");
		}

		Files.createDirectories(outputDirectory.toPath());
		for (int examId = startExamId; examId <= endExamId; examId++) {
			BufferedImage image = ImageIO.read(template.pictureFile());
			if (image == null) {
				throw new IOException("Unable to read template image: " + template.pictureFile());
			}
			fillAnswerSheet(image, template.answerSheet(), String.valueOf(examId), randomAnswers(template.answerSheet()));
			File outputFile = new File(outputDirectory, filePrefix + String.format(Locale.ROOT, "%03d", examId - startExamId + 1) + ".png");
			ImageIO.write(image, "png", outputFile);
			System.out.println(outputFile.getName() + " generated");
		}
	}

	public static Template buildTemplate(LayoutDetector detector, File templateImage) {
		AnswerSheet answerSheet = detector.buildAnswerSheet(IMAGE_WIDTH, IMAGE_HEIGHT, templateImage.getName(), new String[detector.choiceQuestionsPerCol == null ? 0 : countChoices(detector)]);
		return new Template(templateImage.getName(), templateImage, answerSheet);
	}

	private static int countChoices(LayoutDetector detector) {
		int total = 0;
		if (detector.choiceQuestionsPerCol != null) {
			for (int count : detector.choiceQuestionsPerCol) {
				total += count;
			}
		}
		return total;
	}

	private static void fillAnswerSheet(BufferedImage image, AnswerSheet answerSheet, String examId, List<String> answers) {
		Graphics2D graphics = image.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			drawTestBanner(graphics, image.getWidth());
			drawExamId(graphics, answerSheet, examId);
			drawChoices(graphics, answerSheet, answers);
		} finally {
			graphics.dispose();
		}
	}

	private static void drawTestBanner(Graphics2D graphics, int width) {
		graphics.setColor(new Color(255, 245, 204, 235));
		graphics.fill(new RoundRectangle2D.Double(90, 20, width - 180, 56, 18, 18));
		graphics.setColor(new Color(180, 120, 0));
	}

	private static void drawExamId(Graphics2D graphics, AnswerSheet answerSheet, String examId) {
		List<Question> questions = answerSheet.getExamIdQuestions();
		int digitCount = Math.min(examId.length(), questions.size());
		for (int i = 0; i < digitCount; i++) {
			markOption(graphics, questions.get(i), String.valueOf(examId.charAt(examId.length() - digitCount + i)));
		}
	}

	private static void drawChoices(Graphics2D graphics, AnswerSheet answerSheet, List<String> answers) {
		List<Question> questions = answerSheet.getChoiceQuestions();
		for (int i = 0; i < Math.min(questions.size(), answers.size()); i++) {
			markOption(graphics, questions.get(i), answers.get(i));
		}
	}

	private static void markOption(Graphics2D graphics, Question question, String answer) {
		List<Rect> regions = question.optionRegions();
		if (regions == null || regions.isEmpty()) {
			return;
		}
		int index = optionIndex(question, answer);
		if (index < 0 || index >= regions.size()) {
			return;
		}
		Rect rect = regions.get(index);
		graphics.setColor(Color.BLACK);
		graphics.fillRect(rect.x(), rect.y(), rect.width(), rect.height());
	}

	private static int optionIndex(Question question, String answer) {
		String normalized = answer == null ? "" : answer.trim().toUpperCase(Locale.ROOT);
		return switch (question.type()) {
			case EXAM_ID -> "0123456789".indexOf(normalized);
			case SINGLE_CHOICE -> "ABCD".indexOf(normalized);
			case FILL_BLANK -> -1;
		};
	}

	private static List<String> randomAnswers(AnswerSheet answerSheet) {
		String[] choices = {"A", "B", "C", "D"};
		List<String> result = new ArrayList<>(answerSheet.getChoiceQuestions().size());
		for (int i = 0; i < answerSheet.getChoiceQuestions().size(); i++) {
			result.add(choices[QRRandomUtils.getRandomInt(4)]);
		}
		return result;
	}
}