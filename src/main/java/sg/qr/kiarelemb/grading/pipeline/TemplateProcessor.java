package sg.qr.kiarelemb.grading.pipeline;

import org.bytedeco.opencv.opencv_core.Rect;
import sg.qr.kiarelemb.grading.model.AnswerSheet;
import sg.qr.kiarelemb.grading.model.Question;
import sg.qr.kiarelemb.grading.model.SubjectiveRegion;
import sg.qr.kiarelemb.grading.model.Template;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * @author Kiarelemb
 * @projectName QR_Scan_Grade
 * @className TemplateProcessor
 * @description TODO
 * @create 2026/6/5 15:04
 */
public class TemplateProcessor {
	public static final String TEMPLATE_EXTENSION = "sg";
	private static final String META_ENTRY = "template.bin";
	private static final String IMAGE_ENTRY_PREFIX = "image/";
	private static final int FORMAT_VERSION = 3;

	private TemplateProcessor() {
	}

	public static File save(Template template, File targetFile) throws IOException {
		Objects.requireNonNull(template, "template");
		Objects.requireNonNull(targetFile, "targetFile");

		List<File> imageFiles = template.pictureFiles().isEmpty() ? List.of(template.pictureFile()) : template.pictureFiles();
		for (File imageFile : imageFiles) {
			if (!imageFile.isFile()) {
				throw new FileNotFoundException("Template image not found: " + imageFile.getAbsolutePath());
			}
		}

		File sgFile = withSgExtension(targetFile);
		File parent = sgFile.getParentFile();
		if (parent != null) {
			Files.createDirectories(parent.toPath());
		}

		List<String> imageEntryNames = new ArrayList<>();
		for (int i = 0; i < imageFiles.size(); i++) {
			imageEntryNames.add(IMAGE_ENTRY_PREFIX + String.format("%03d-", i + 1) + safeImageFileName(imageFiles.get(i)));
		}
		TemplateData data = TemplateData.from(template, imageEntryNames);

		try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(sgFile)))) {
			zip.putNextEntry(new ZipEntry(META_ENTRY));
			ObjectOutputStream objectOut = new ObjectOutputStream(zip);
			objectOut.writeObject(data);
			objectOut.flush();
			zip.closeEntry();

			for (int i = 0; i < imageFiles.size(); i++) {
				zip.putNextEntry(new ZipEntry(imageEntryNames.get(i)));
				Files.copy(imageFiles.get(i).toPath(), zip);
				zip.closeEntry();
			}
		}
		return sgFile;
	}

	public static Template load(File sgFile) throws IOException {
		Objects.requireNonNull(sgFile, "sgFile");
		if (!sgFile.isFile()) {
			throw new FileNotFoundException("Template file not found: " + sgFile.getAbsolutePath());
		}

		TemplateData data = null;
		Map<String, File> imageFilesByEntryName = new LinkedHashMap<>();

		try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(sgFile)))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (entry.isDirectory()) {
					zip.closeEntry();
					continue;
				}
				if (META_ENTRY.equals(entry.getName())) {
					ObjectInputStream objectIn = new ObjectInputStream(zip);
					try {
						Object object = objectIn.readObject();
						if (!(object instanceof TemplateData templateData)) {
							throw new IOException("Invalid template metadata: " + sgFile.getAbsolutePath());
						}
						data = templateData;
					} catch (ClassNotFoundException e) {
						throw new IOException("Cannot read template metadata: " + sgFile.getAbsolutePath(), e);
					}
				} else if (entry.getName().startsWith(IMAGE_ENTRY_PREFIX)) {
					imageFilesByEntryName.put(entry.getName(), extractImageToTempFile(entry.getName(), zip));
				}
				zip.closeEntry();
			}
		}

		if (data == null) {
			throw new IOException("Missing template metadata: " + sgFile.getAbsolutePath());
		}
		if (data.version < 1 || data.version > FORMAT_VERSION) {
			throw new IOException("Unsupported template version: " + data.version);
		}
		List<File> imageFiles = orderedImageFiles(data, imageFilesByEntryName);
		if (imageFiles.isEmpty()) {
			throw new IOException("Missing template image: " + sgFile.getAbsolutePath());
		}

		return new Template(
				data.name,
				imageFiles.get(0),
				data.answerSheet.toAnswerSheet(),
				toOpenCvRect(data.examRegionRect),
				toOpenCvRect(data.choiceRegionRect),
				toOpenCvRect(data.fillBlankRegionRect),
				data.defaultScoreRules,
				Math.max(1, data.pageCount),
				toSubjectiveRegions(data.subjectiveRegions),
				imageFiles
		);
	}

	public static File saveTemplate(Template template, File targetFile) throws IOException {
		return save(template, targetFile);
	}

	public static Template readTemplate(File sgFile) throws IOException {
		return load(sgFile);
	}

	public static Template loadTemplate(File sgFile) throws IOException {
		return load(sgFile);
	}

	public static AnswerSheet removeAnswers(AnswerSheet answerSheet) {
		Objects.requireNonNull(answerSheet, "answerSheet");
		List<Question> questions = new ArrayList<>();
		for (Question question : answerSheet.getQuestions()) {
			List<Rect> regions = new ArrayList<>();
			for (Rect rect : question.optionRegions()) {
				regions.add(new Rect(rect.x(), rect.y(), rect.width(), rect.height()));
			}
			questions.add(new Question(question.number(), question.type(), regions, ""));
		}
		return new AnswerSheet(
				answerSheet.getName(),
				answerSheet.getImageWidth(),
				answerSheet.getImageHeight(),
				answerSheet.getExamIdDigits(),
				questions
		);
	}

	public static File withSgExtension(File file) {
		String path = file.getAbsolutePath();
		if (path.toLowerCase().endsWith("." + TEMPLATE_EXTENSION)) {
			return file;
		}
		return new File(path + "." + TEMPLATE_EXTENSION);
	}

	private static String safeImageFileName(File file) {
		String name = file.getName();
		if (name.isBlank()) {
			return "template-image";
		}
		return name.replace('\\', '_').replace('/', '_');
	}

	private static File extractImageToTempFile(String entryName, InputStream input) throws IOException {
		String imageName = new File(entryName).getName();
		String suffix = imageSuffix(imageName);
		File tempFile = File.createTempFile("qr-template-", suffix);
		tempFile.deleteOnExit();
		Files.copy(input, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		return tempFile;
	}

	private static String imageSuffix(String imageName) {
		int dot = imageName.lastIndexOf('.');
		if (dot < 0 || dot == imageName.length() - 1) {
			return ".img";
		}
		return imageName.substring(dot);
	}

	private static final class TemplateData implements Serializable {
		@Serial
		private static final long serialVersionUID = 1L;

		private final int version;
		private final String name;
		private final String imageEntryName;
		private final List<String> imageEntryNames;
		private final AnswerSheetData answerSheet;
		private final RectData examRegionRect;
		private final RectData choiceRegionRect;
		private final RectData fillBlankRegionRect;
		private final String defaultScoreRules;
		private final int pageCount;
		private final List<SubjectiveRegionData> subjectiveRegions;

		private TemplateData(int version, String name, String imageEntryName, AnswerSheetData answerSheet,
							 RectData examRegionRect, RectData choiceRegionRect, RectData fillBlankRegionRect,
							 String defaultScoreRules,
							 int pageCount,
							 List<SubjectiveRegionData> subjectiveRegions) {
			this(version, name, imageEntryName, imageEntryName == null ? List.of() : List.of(imageEntryName),
					answerSheet, examRegionRect, choiceRegionRect, fillBlankRegionRect, defaultScoreRules,
					pageCount, subjectiveRegions);
		}

		private TemplateData(int version, String name, String imageEntryName, List<String> imageEntryNames,
							 AnswerSheetData answerSheet,
							 RectData examRegionRect, RectData choiceRegionRect, RectData fillBlankRegionRect,
							 String defaultScoreRules,
							 int pageCount,
							 List<SubjectiveRegionData> subjectiveRegions) {
			this.version = version;
			this.name = name;
			this.imageEntryName = imageEntryName;
			this.imageEntryNames = imageEntryNames == null ? List.of() : List.copyOf(imageEntryNames);
			this.answerSheet = answerSheet;
			this.examRegionRect = examRegionRect;
			this.choiceRegionRect = choiceRegionRect;
			this.fillBlankRegionRect = fillBlankRegionRect;
			this.defaultScoreRules = defaultScoreRules == null ? "" : defaultScoreRules;
			this.pageCount = Math.max(1, pageCount);
			this.subjectiveRegions = subjectiveRegions == null ? List.of() : List.copyOf(subjectiveRegions);
		}

		private static TemplateData from(Template template, List<String> imageEntryNames) {
			return new TemplateData(
					FORMAT_VERSION,
					template.name(),
					imageEntryNames.isEmpty() ? null : imageEntryNames.get(0),
					imageEntryNames,
					AnswerSheetData.from(removeAnswers(template.answerSheet())),
					RectData.fromNullable(template.examRegionRect()),
					RectData.fromNullable(template.choiceRegionRect()),
					RectData.fromNullable(template.fillBlankRegionRect()),
					template.defaultScoreRules(),
					template.pageCount(),
					template.subjectiveRegions().stream().map(SubjectiveRegionData::from).toList()
			);
		}
	}

	private record SubjectiveRegionData(String name,
										int startQuestion,
										int endQuestion,
										RectData region,
										SubjectiveRegion.GradingMode mode,
										BigDecimal maxScore,
										int pageIndex) implements Serializable {
		@Serial
		private static final long serialVersionUID = 1L;

		private static SubjectiveRegionData from(SubjectiveRegion region) {
			return new SubjectiveRegionData(region.name(), region.startQuestion(), region.endQuestion(),
					RectData.from(region.region()), region.mode(), region.maxScore(), region.pageIndex());
		}

		private SubjectiveRegion toSubjectiveRegion() {
			return new SubjectiveRegion(name, startQuestion, endQuestion, region.toRect(), mode, maxScore, pageIndex);
		}
	}

	private record AnswerSheetData(String name, int imageWidth, int imageHeight, int examIdDigits,
								   List<QuestionData> questions) implements Serializable {
		@Serial
		private static final long serialVersionUID = 1L;

		private static AnswerSheetData from(AnswerSheet answerSheet) {
			List<QuestionData> questions = new ArrayList<>();
			for (Question question : answerSheet.getQuestions()) {
				questions.add(QuestionData.from(question));
			}
			return new AnswerSheetData(
					answerSheet.getName(),
					answerSheet.getImageWidth(),
					answerSheet.getImageHeight(),
					answerSheet.getExamIdDigits(),
					questions
			);
		}

		private AnswerSheet toAnswerSheet() {
			List<Question> restoredQuestions = new ArrayList<>();
			for (QuestionData question : questions) {
				restoredQuestions.add(question.toQuestion());
			}
			return new AnswerSheet(name, imageWidth, imageHeight, examIdDigits, restoredQuestions);
		}
	}

	private record QuestionData(int number, Question.QuestionType type,
								List<RectData> optionRegions) implements Serializable {
		@Serial
		private static final long serialVersionUID = 1L;

		private static QuestionData from(Question question) {
			List<RectData> regions = new ArrayList<>();
			for (Rect rect : question.optionRegions()) {
				regions.add(RectData.from(rect));
			}
			return new QuestionData(question.number(), question.type(), regions);
		}

		private Question toQuestion() {
			List<Rect> regions = new ArrayList<>();
			for (RectData rect : optionRegions) {
				regions.add(rect.toRect());
			}
			return new Question(number, type, regions, "");
		}
	}

	private record RectData(int x, int y, int width, int height) implements Serializable {
		@Serial
		private static final long serialVersionUID = 1L;

		private static RectData from(Rect rect) {
			return new RectData(rect.x(), rect.y(), rect.width(), rect.height());
		}

		private static RectData fromNullable(Rect rect) {
			return rect == null ? null : from(rect);
		}

		private Rect toRect() {
			return new Rect(x, y, width, height);
		}
	}

	private static Rect toOpenCvRect(RectData rect) {
		return rect == null ? null : rect.toRect();
	}

	private static List<SubjectiveRegion> toSubjectiveRegions(List<SubjectiveRegionData> regions) {
		if (regions == null || regions.isEmpty()) {
			return List.of();
		}
		List<SubjectiveRegion> result = new ArrayList<>();
		for (SubjectiveRegionData region : regions) {
			if (region != null && region.region() != null) {
				result.add(region.toSubjectiveRegion());
			}
		}
		return result;
	}

	private static List<File> orderedImageFiles(TemplateData data, Map<String, File> imageFilesByEntryName) {
		List<String> entryNames = data.imageEntryNames;
		if ((entryNames == null || entryNames.isEmpty()) && data.imageEntryName != null) {
			entryNames = List.of(data.imageEntryName);
		}
		List<File> imageFiles = new ArrayList<>();
		if (entryNames != null) {
			for (String entryName : entryNames) {
				File file = imageFilesByEntryName.get(entryName);
				if (file != null) {
					imageFiles.add(file);
				}
			}
		}
		if (imageFiles.isEmpty()) {
			imageFiles.addAll(imageFilesByEntryName.values());
		}
		return imageFiles;
	}
}
