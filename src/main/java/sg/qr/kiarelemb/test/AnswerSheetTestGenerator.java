package sg.qr.kiarelemb.test;

import method.qr.kiarelemb.utils.QRRandomUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.bytedeco.opencv.opencv_core.Rect;
import sg.qr.kiarelemb.exam.model.SheetLayout;
import sg.qr.kiarelemb.exam.model.SheetQuestion;
import sg.qr.kiarelemb.exam.model.SheetTemplate;
import sg.qr.kiarelemb.exam.model.SubjectiveAnswerRegion;
import sg.qr.kiarelemb.exam.processing.DocumentPageLoader;
import sg.qr.kiarelemb.exam.processing.SheetTemplateFileStore;
import sg.qr.kiarelemb.exam.template.detect.TemplateLayoutDetector;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AnswerSheetTestGenerator {
	private static final int DEFAULT_START_ID = 202606001;
	private static final int DEFAULT_END_ID = 202606005;
	private static final int LISTENING_QUESTION_COUNT = 20;
	private static final int LISTENING_OPTION_COUNT = 3;
	private static final int FILL_BLANK_COUNT = 10;
	private static final int FILL_BLANK_COLUMNS = 3;
	private static final int COMPOSITION_CHARS_PER_LINE = 15;
	private static final int COMPOSITION_LINE_COUNT = 2;
	private static final int FIXED_CHOICE_MARK_W = 43;
	private static final int FIXED_CHOICE_MARK_H = 27;
	private static final int[][][] FIXED_CHOICE_RECTS = fixedChoiceRects();
	private static final int FIXED_EXAM_MARK_W = 43;
	private static final int FIXED_EXAM_MARK_H = 27;
	private static final int[] FIXED_EXAM_COLUMN_XS = {1408, 1493, 1578, 1663, 1749, 1834, 1919, 2004, 2089};
	private static final int[] FIXED_EXAM_DIGIT_YS = {893, 950, 1009, 1069, 1128, 1187, 1246, 1305, 1364, 1424};
	private static final float PDF_JPEG_QUALITY = 0.82f;
	private static final int IMAGE_WIDTH = 2480;
	private static final int IMAGE_HEIGHT = 3507;
	private static final File DEFAULT_TEMPLATE_FILE = new File("sg/期末答题卡.sg");
	private static final File DEFAULT_SOURCE_PDF = new File("ans/AnswerSheets.pdf");
	private static final File DEFAULT_OUTPUT_DIRECTORY = new File("ans/generated-answer-sheets");
	private static final String DEFAULT_FILE_PREFIX = "answer_sheet_";
	private static final File DEFAULT_MERGED_PDF = new File(DEFAULT_OUTPUT_DIRECTORY, "answer_sheets_all.pdf");

	public static void main(String[] args) throws Exception {
		String mode = args.length > 0 ? args[0].trim().toLowerCase(Locale.ROOT) : "pdf";
		File sourcePdf = args.length > 1 ? new File(args[1]) : DEFAULT_SOURCE_PDF;
		File templateFile = args.length > 2 ? new File(args[2]) : DEFAULT_TEMPLATE_FILE;
		File output = args.length > 3 ? new File(args[3]) : ("images".equals(mode) ? DEFAULT_OUTPUT_DIRECTORY : DEFAULT_MERGED_PDF);
		int startExamId = args.length > 4 ? Integer.parseInt(args[4]) : DEFAULT_START_ID;
		int endExamId = args.length > 5 ? Integer.parseInt(args[5]) : DEFAULT_END_ID;

		SheetTemplate template = SheetTemplateFileStore.load(templateFile);
		switch (mode) {
			case "pdf" -> generateOnePdf(sourcePdf, template, output, startExamId, endExamId);
			case "images" -> generateImages(sourcePdf, template, output, DEFAULT_FILE_PREFIX, startExamId, endExamId);
			default -> throw new IllegalArgumentException("Unknown mode: " + mode + ". Use pdf or images.");
		}
	}

	public static void generate(SheetTemplate template, File outputDirectory, String filePrefix) throws IOException {
		generate(template, outputDirectory, filePrefix, DEFAULT_START_ID, DEFAULT_END_ID);
	}

	public static void generate(SheetTemplate template, File outputDirectory, String filePrefix, int startExamId, int endExamId) throws IOException {
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
		GeneratedAnswers answers = GeneratedAnswers.randomFor(template);
		for (int examId = startExamId; examId <= endExamId; examId++) {
			BufferedImage image = ImageIO.read(template.pictureFile());
			if (image == null) {
				throw new IOException("Unable to read template image: " + template.pictureFile());
			}
			fillAnswerSheet(image, template, String.valueOf(examId), answers);
			File outputFile = new File(outputDirectory, filePrefix + String.format(Locale.ROOT, "%03d", examId - startExamId + 1) + ".png");
			ImageIO.write(image, "png", outputFile);
			System.out.println(outputFile.getName() + " generated");
		}
	}

	public static void generatePdf(File sourcePdf, SheetTemplate template, File outputDirectory, String filePrefix) throws IOException {
		generatePdf(sourcePdf, template, outputDirectory, filePrefix, DEFAULT_START_ID, DEFAULT_END_ID);
	}

	public static void generatePdf(File sourcePdf, SheetTemplate template, File outputDirectory, String filePrefix, int startExamId, int endExamId) throws IOException {
		generateOnePdf(sourcePdf, template, new File(outputDirectory, "answer_sheets_all.pdf"), startExamId, endExamId);
	}

	public static void generateOnePdf(File sourcePdf, SheetTemplate template, File outputFile) throws IOException {
		generateOnePdf(sourcePdf, template, outputFile, DEFAULT_START_ID, DEFAULT_END_ID);
	}

	public static void generateOnePdf(File sourcePdf, SheetTemplate template, File outputFile, int startExamId, int endExamId) throws IOException {
		if (sourcePdf == null || !sourcePdf.isFile()) {
			throw new IllegalArgumentException("sourcePdf must be an existing file");
		}
		if (!DocumentPageLoader.isPdfFile(sourcePdf)) {
			throw new IllegalArgumentException("sourcePdf must be a PDF file: " + sourcePdf.getAbsolutePath());
		}
		if (template == null) {
			throw new IllegalArgumentException("template must not be null");
		}
		if (outputFile == null) {
			throw new IllegalArgumentException("outputFile must not be null");
		}
		if (startExamId > endExamId) {
			throw new IllegalArgumentException("startExamId must be <= endExamId");
		}

		File parent = outputFile.getParentFile();
		if (parent != null) {
			Files.createDirectories(parent.toPath());
		}
		List<BufferedImage> templatePages = renderPdfPages(sourcePdf);
		if (templatePages.size() != 2) {
			throw new IOException("Expected a two-page answer sheet PDF, but found " + templatePages.size() + " pages: " + sourcePdf.getAbsolutePath());
		}

		GeneratedAnswers answers = GeneratedAnswers.randomFor(template);
		System.out.println("测试答案: 选择题=" + String.join("", answers.choiceAnswers())
		                   + ", 填空题=" + String.join(" / ", answers.fillBlankAnswers())
		                   + ", 作文=" + answers.compositionTitle());
		try (PDDocument document = new PDDocument()) {
			int total = endExamId - startExamId + 1;
			for (int examId = startExamId; examId <= endExamId; examId++) {
				List<BufferedImage> pages = copyPages(templatePages);
				fillAnswerSheet(pages, template, String.valueOf(examId), answers);
				for (BufferedImage page : pages) {
					addImagePage(document, page);
				}
				int current = examId - startExamId + 1;
				System.out.println("已写入 " + current + "/" + total + "：考号 " + examId);
			}
			System.out.println("正在保存合并 PDF：" + outputFile.getAbsolutePath());
			document.save(outputFile);
		}
		System.out.println(outputFile.getName() + " generated");
	}

	public static void generateImages(File sourcePdf, SheetTemplate template, File outputDirectory, String filePrefix) throws IOException {
		generateImages(sourcePdf, template, outputDirectory, filePrefix, DEFAULT_START_ID, DEFAULT_END_ID);
	}

	public static void generateImages(File sourcePdf, SheetTemplate template, File outputDirectory, String filePrefix, int startExamId, int endExamId) throws IOException {
		if (sourcePdf == null || !sourcePdf.isFile()) {
			throw new IllegalArgumentException("sourcePdf must be an existing file");
		}
		if (!DocumentPageLoader.isPdfFile(sourcePdf)) {
			throw new IllegalArgumentException("sourcePdf must be a PDF file: " + sourcePdf.getAbsolutePath());
		}
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
		List<BufferedImage> templatePages = renderPdfPages(sourcePdf);
		if (templatePages.size() != 2) {
			throw new IOException("Expected a two-page answer sheet PDF, but found " + templatePages.size() + " pages: " + sourcePdf.getAbsolutePath());
		}
		GeneratedAnswers answers = GeneratedAnswers.randomFor(template);
		System.out.println("测试答案: 选择题=" + String.join("", answers.choiceAnswers())
		                   + ", 填空题=" + String.join(" / ", answers.fillBlankAnswers())
		                   + ", 作文=" + answers.compositionTitle());
		for (int examId = startExamId; examId <= endExamId; examId++) {
			List<BufferedImage> pages = copyPages(templatePages);
			fillAnswerSheet(pages, template, String.valueOf(examId), answers);
			for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
				String name = filePrefix
				              + String.format(Locale.ROOT, "%03d", examId - startExamId + 1)
				              + "_" + examId
				              + "_p" + String.format(Locale.ROOT, "%03d", pageIndex + 1)
				              + ".png";
				File outputFile = new File(outputDirectory, name);
				ImageIO.write(pages.get(pageIndex), "png", outputFile);
				System.out.println(outputFile.getName() + " generated");
			}
		}
	}

	public static SheetTemplate buildTemplate(TemplateLayoutDetector detector, File templateImage) {
		SheetLayout answerSheet = detector.buildAnswerSheet(IMAGE_WIDTH, IMAGE_HEIGHT, templateImage.getName(), new String[detector.choiceQuestionsPerCol == null ? 0 : countChoices(detector)]);
		return new SheetTemplate(templateImage.getName(), templateImage, answerSheet);
	}

	private static int countChoices(TemplateLayoutDetector detector) {
		int total = 0;
		if (detector.choiceQuestionsPerCol != null) {
			for (int count : detector.choiceQuestionsPerCol) {
				total += count;
			}
		}
		return total;
	}

	private static void fillAnswerSheet(BufferedImage image, SheetTemplate template, String examId, GeneratedAnswers answers) {
		Graphics2D graphics = image.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			drawExamId(graphics, template.answerSheet(), examId);
			drawChoices(graphics, template.answerSheet(), answers.choiceAnswers());
			drawSubjectiveAnswers(graphics, template, 0, answers);
		} finally {
			graphics.dispose();
		}
	}

	private static void fillAnswerSheet(List<BufferedImage> pages, SheetTemplate template, String examId, GeneratedAnswers answers) {
		for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
			BufferedImage image = pages.get(pageIndex);
			Graphics2D graphics = image.createGraphics();
			try {
				graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				if (pageIndex == 0) {
					drawExamId(graphics, template.answerSheet(), examId);
					drawChoices(graphics, template.answerSheet(), answers.choiceAnswers());
				}
				drawSubjectiveAnswers(graphics, template, pageIndex, answers);
			} finally {
				graphics.dispose();
			}
		}
	}

	private static void drawExamId(Graphics2D graphics, SheetLayout answerSheet, String examId) {
		if (isFixedFinalExamTemplate(answerSheet)) {
			drawFixedFinalExamId(graphics, examId);
			return;
		}
		List<SheetQuestion> questions = answerSheet.getExamIdQuestions();
		int digitCount = Math.min(examId.length(), questions.size());
		for (int i = 0; i < digitCount; i++) {
			markOption(graphics, questions.get(i), String.valueOf(examId.charAt(examId.length() - digitCount + i)));
		}
	}

	private static void drawFixedFinalExamId(Graphics2D graphics, String examId) {
		graphics.setColor(Color.BLACK);
		int digitCount = Math.min(examId.length(), FIXED_EXAM_COLUMN_XS.length);
		for (int i = 0; i < digitCount; i++) {
			int digit = Character.digit(examId.charAt(examId.length() - digitCount + i), 10);
			if (digit < 0 || digit >= FIXED_EXAM_DIGIT_YS.length) {
				continue;
			}
			graphics.fillRect(
					FIXED_EXAM_COLUMN_XS[i],
					FIXED_EXAM_DIGIT_YS[digit],
					FIXED_EXAM_MARK_W,
					FIXED_EXAM_MARK_H
			);
		}
	}

	private static void drawChoices(Graphics2D graphics, SheetLayout answerSheet, List<String> answers) {
		if (isFixedFinalExamTemplate(answerSheet)) {
			drawFixedFinalExamChoices(graphics, answers);
			return;
		}
		List<SheetQuestion> questions = answerSheet.getChoiceQuestions();
		for (int i = 0; i < Math.min(questions.size(), answers.size()); i++) {
			markOption(graphics, questions.get(i), answers.get(i));
		}
	}

	private static boolean isFixedFinalExamTemplate(SheetLayout answerSheet) {
		return answerSheet.getImageWidth() == IMAGE_WIDTH
		       && answerSheet.getImageHeight() == IMAGE_HEIGHT
		       && answerSheet.getChoiceQuestions().size() == 50;
	}

	private static void drawFixedFinalExamChoices(Graphics2D graphics, List<String> answers) {
		graphics.setColor(Color.BLACK);
		for (int i = 0; i < Math.min(50, answers.size()); i++) {
			int questionNumber = i + 1;
			int answerIndex = "ABCD".indexOf(answers.get(i));
			if (answerIndex < 0) {
				continue;
			}
			int optionCount = questionNumber <= LISTENING_QUESTION_COUNT ? LISTENING_OPTION_COUNT : 4;
			if (answerIndex >= optionCount) {
				continue;
			}
			int[] rect = FIXED_CHOICE_RECTS[questionNumber - 1][answerIndex];
			graphics.fillRect(rect[0], rect[1], rect[2], rect[3]);
		}
	}

	private static int[][][] fixedChoiceRects() {
		int[][][] rects = new int[50][][];
		addFixedChoiceGroup(rects, 1, new int[]{451, 536, 622}, new int[]{1670, 1724, 1776, 1830, 1883});
		addFixedChoiceGroup(rects, 6, new int[]{911, 996, 1082}, new int[]{1667, 1720, 1773, 1827, 1880});
		addFixedChoiceGroup(rects, 11, new int[]{1369, 1454, 1539}, new int[]{1666, 1719, 1771, 1825, 1878});
		addFixedChoiceGroup(rects, 16, new int[]{1828, 1913, 1998}, new int[]{1669, 1722, 1775, 1828, 1881});
		addFixedChoiceGroup(rects, 21, new int[]{451, 536, 622, 707}, new int[]{1994, 2047, 2100, 2153, 2206});
		addFixedChoiceGroup(rects, 26, new int[]{911, 996, 1082, 1167}, new int[]{1991, 2044, 2097, 2150, 2203});
		addFixedChoiceGroup(rects, 31, new int[]{1369, 1454, 1539, 1624}, new int[]{1989, 2042, 2095, 2149, 2202});
		addFixedChoiceGroup(rects, 36, new int[]{1828, 1913, 1998, 2084}, new int[]{1992, 2045, 2099, 2152, 2205});
		addFixedChoiceGroup(rects, 41, new int[]{451, 536, 622, 707}, new int[]{2317, 2370, 2424, 2477, 2530});
		addFixedChoiceGroup(rects, 46, new int[]{911, 996, 1082, 1167}, new int[]{2314, 2367, 2420, 2474, 2527});
		return rects;
	}

	private static void addFixedChoiceGroup(int[][][] rects, int startQuestion, int[] optionXs, int[] rowYs) {
		for (int row = 0; row < rowYs.length; row++) {
			int questionIndex = startQuestion + row - 1;
			rects[questionIndex] = new int[optionXs.length][];
			for (int opt = 0; opt < optionXs.length; opt++) {
				rects[questionIndex][opt] = new int[]{optionXs[opt], rowYs[row], FIXED_CHOICE_MARK_W, FIXED_CHOICE_MARK_H};
			}
		}
	}

	private static void markOption(Graphics2D graphics, SheetQuestion question, String answer) {
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

	private static int optionIndex(SheetQuestion question, String answer) {
		String normalized = answer == null ? "" : answer.trim().toUpperCase(Locale.ROOT);
		return switch (question.type()) {
			case EXAM_ID -> "0123456789".indexOf(normalized);
			case SINGLE_CHOICE -> "ABCD".indexOf(normalized);
			case FILL_BLANK -> -1;
		};
	}

	private static List<String> randomAnswers(SheetLayout answerSheet) {
		List<String> result = new ArrayList<>(answerSheet.getChoiceQuestions().size());
		for (SheetQuestion question : answerSheet.getChoiceQuestions()) {
			String[] choices = choiceLabelsFor(question);
			result.add(choices[QRRandomUtils.getRandomInt(choices.length)]);
		}
		return result;
	}

	private static String[] choiceLabelsFor(SheetQuestion question) {
		int optionCount = question.number() <= LISTENING_QUESTION_COUNT
				? LISTENING_OPTION_COUNT
				: question.optionRegions().size();
		optionCount = Math.max(1, Math.min(SheetLayout.CHOICE_LABELS.length, optionCount));
		return java.util.Arrays.copyOf(SheetLayout.CHOICE_LABELS, optionCount);
	}

	private static void drawSubjectiveAnswers(Graphics2D graphics, SheetTemplate template, int pageIndex, GeneratedAnswers answers) {
		for (SubjectiveAnswerRegion region : template.subjectiveRegions()) {
			if (region.pageIndex() != pageIndex) {
				continue;
			}
			if (isFillBlankRegion(region)) {
				drawFillBlankAnswers(graphics, region.region(), answers.fillBlankAnswers(), fillBlankCount(region, template));
			} else {
				drawCompositionAnswer(graphics, region.region(), answers.compositionAnswer());
			}
		}
	}

	private static boolean isFillBlankRegion(SubjectiveAnswerRegion region) {
		String regionName = region.name().toLowerCase(Locale.ROOT);
		return region.pageIndex() == 0 || regionName.contains("填空");
	}

	private static int fillBlankCount(SubjectiveAnswerRegion region, SheetTemplate template) {
		int regionCount = Math.max(1, region.endQuestion() - region.startQuestion() + 1);
		int sheetCount = template.answerSheet().getFillBlankQuestions().size();
		return Math.max(FILL_BLANK_COUNT, Math.max(regionCount, sheetCount));
	}

	private static void drawFillBlankAnswers(Graphics2D graphics, Rect region, List<String> answers, int answerCount) {
		if (answers.isEmpty() || answerCount <= 0) {
			return;
		}
		graphics.setColor(Color.BLACK);
		graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 30));
		FontMetrics metrics = graphics.getFontMetrics();
		int rows = (int) Math.ceil(answerCount / (double) FILL_BLANK_COLUMNS);
		int topGap = Math.max(60, Math.round(region.height() * 0.23f));
		int leftGap = Math.max(60, Math.round(region.width() * 0.08f));
		int colWidth = Math.max(1, (region.width() - leftGap) / FILL_BLANK_COLUMNS);
		int rowStep = rows <= 1 ? 0 : Math.max(1, Math.round((region.height() - topGap - 95) / (float) (rows - 1)));
		int textOffsetX = Math.max(70, Math.round(colWidth * 0.17f));

		for (int i = 0; i < answerCount; i++) {
			int row = i / FILL_BLANK_COLUMNS;
			int col = i % FILL_BLANK_COLUMNS;
			int x = region.x() + leftGap + col * colWidth + textOffsetX;
			int baseline = region.y() + topGap + row * rowStep;
			int maxWidth = Math.max(60, colWidth - textOffsetX - 28);
			drawSingleLineFit(graphics, answers.get(i % answers.size()), x, baseline, maxWidth, metrics);
		}
	}

	private static void drawSingleLineFit(Graphics2D graphics, String text, int x, int baseline, int maxWidth, FontMetrics metrics) {
		String line = text == null ? "" : text.trim();
		while (!line.isEmpty() && metrics.stringWidth(line) > maxWidth) {
			line = line.substring(0, line.length() - 1);
		}
		if (!line.isEmpty()) {
			graphics.drawString(line, x, baseline);
		}
	}

	private static void drawCompositionAnswer(Graphics2D graphics, Rect region, String text) {
		List<String> lines = fixedCharLines(text, COMPOSITION_CHARS_PER_LINE, COMPOSITION_LINE_COUNT);
		if (lines.isEmpty()) {
			return;
		}
		graphics.setColor(Color.BLACK);
		graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 34));
		FontMetrics metrics = graphics.getFontMetrics();
		int gridLeft = region.x() + Math.max(32, Math.round(region.width() * 0.04f));
		int gridTop = region.y() + Math.max(120, Math.round(region.height() * 0.07f));
		int cellWidth = Math.max(42, Math.round(region.width() * 0.052f));
		int lineHeight = Math.max(metrics.getHeight() + 30, Math.round(region.height() * 0.052f));
		for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
			String line = lines.get(lineIndex);
			int baseline = gridTop + lineIndex * lineHeight + Math.round(lineHeight * 0.68f);
			for (int i = 0; i < line.length(); i++) {
				String ch = String.valueOf(line.charAt(i));
				int charX = gridLeft + i * cellWidth + Math.max(0, (cellWidth - metrics.stringWidth(ch)) / 2);
				graphics.drawString(ch, charX, baseline);
			}
		}
	}

	private static List<String> fixedCharLines(String text, int charsPerLine, int maxLines) {
		List<String> lines = new ArrayList<>();
		String normalized = text == null ? "" : text.replaceAll("\\s+", "");
		for (int start = 0; start < normalized.length() && lines.size() < maxLines; start += charsPerLine) {
			lines.add(normalized.substring(start, Math.min(normalized.length(), start + charsPerLine)));
		}
		return lines;
	}

	private static void drawTextInRegion(Graphics2D graphics, Rect region, String text) {
		int padding = 24;
		int x = region.x() + padding;
		int y = region.y() + padding + 34;
		int maxWidth = Math.max(1, region.width() - padding * 2);
		int maxY = region.y() + region.height() - padding;
		graphics.setColor(Color.BLACK);
		graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 34));
		FontMetrics metrics = graphics.getFontMetrics();
		for (String paragraph : text.split("\\R")) {
			for (String line : wrapLine(paragraph, metrics, maxWidth)) {
				if (y > maxY) {
					return;
				}
				graphics.drawString(line, x, y);
				y += metrics.getHeight() + 8;
			}
			y += metrics.getHeight() / 2;
		}
	}

	private static List<String> wrapLine(String text, FontMetrics metrics, int maxWidth) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			String candidate = line.toString() + c;
			if (!line.isEmpty() && metrics.stringWidth(candidate) > maxWidth) {
				lines.add(line.toString());
				line.setLength(0);
			}
			line.append(c);
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		if (lines.isEmpty()) {
			lines.add("");
		}
		return lines;
	}

	private record GeneratedAnswers(List<String> choiceAnswers, List<String> fillBlankAnswers, String compositionTitle,
	                                String compositionAnswer) {
		private static final List<String> FILL_BLANK_POOL = List.of(
				"七時",
				"学校",
				"図書館",
				"日本語",
				"昨日",
				"天気",
				"教室",
				"三十人",
				"友達",
				"宿題"
		);
		private static final Map<String, String> COMPOSITION_POOL = Map.of(
				"私の一日", "私は毎朝六時半に起きます。学校で日本語を勉強します。",
				"好きな季節", "私は春がいちばん好きです。花がたくさん咲きます。",
				"私の友達", "私の友達は明るい人です。毎日いっしょに学校へ行きます。"
		);

		private static GeneratedAnswers randomFor(SheetTemplate template) {
			List<String> choiceAnswers = randomAnswers(template.answerSheet());
			List<String> fillBlankAnswers = new ArrayList<>(FILL_BLANK_POOL);
			List<String> titles = new ArrayList<>(COMPOSITION_POOL.keySet());
			String title = titles.get(QRRandomUtils.getRandomInt(titles.size()));
			return new GeneratedAnswers(choiceAnswers, fillBlankAnswers, title, COMPOSITION_POOL.get(title));
		}
	}

	private static List<BufferedImage> renderPdfPages(File pdfFile) throws IOException {
		List<File> pageFiles = DocumentPageLoader.documentImages(pdfFile);
		List<BufferedImage> pages = new ArrayList<>(pageFiles.size());
		for (File pageFile : pageFiles) {
			BufferedImage image = ImageIO.read(pageFile);
			if (image == null) {
				throw new IOException("Unable to read rendered PDF page: " + pageFile.getAbsolutePath());
			}
			pages.add(image);
		}
		return pages;
	}

	private static List<BufferedImage> copyPages(List<BufferedImage> sourcePages) {
		List<BufferedImage> copies = new ArrayList<>(sourcePages.size());
		for (BufferedImage source : sourcePages) {
			BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
			Graphics2D graphics = copy.createGraphics();
			try {
				graphics.drawImage(source, 0, 0, null);
			} finally {
				graphics.dispose();
			}
			copies.add(copy);
		}
		return copies;
	}

	private static void writePdf(List<BufferedImage> pages, File outputFile) throws IOException {
		try (PDDocument document = new PDDocument()) {
			for (BufferedImage image : pages) {
				addImagePage(document, image);
			}
			document.save(outputFile);
		}
	}

	private static void addImagePage(PDDocument document, BufferedImage image) throws IOException {
		PDPage page = new PDPage(PDRectangle.A4);
		document.addPage(page);
		PDImageXObject pageImage = JPEGFactory.createFromImage(document, image, PDF_JPEG_QUALITY);
		try (PDPageContentStream content = new PDPageContentStream(document, page)) {
			content.drawImage(pageImage, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
		}
	}
}