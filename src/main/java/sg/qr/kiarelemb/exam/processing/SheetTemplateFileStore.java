package sg.qr.kiarelemb.exam.processing;

import org.bytedeco.opencv.opencv_core.Rect;
import sg.qr.kiarelemb.exam.model.SheetLayout;
import sg.qr.kiarelemb.exam.model.SheetQuestion;
import sg.qr.kiarelemb.exam.model.SubjectiveAnswerRegion;
import sg.qr.kiarelemb.exam.model.SheetTemplate;

import method.qr.kiarelemb.utils.QRLoggerUtils;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * @author Kiarelemb
 * @projectName QR_Scan_Grade
 * @className TemplateStorage
 * @description TODO
 * @create 2026/6/5 15:04
 */
public class SheetTemplateFileStore {
	private static final Logger logger = QRLoggerUtils.getLogger(SheetTemplateFileStore.class);

	public static final String TEMPLATE_EXTENSION = "sg";
	private static final String META_ENTRY = "template.bin";
	private static final String IMAGE_ENTRY_PREFIX = "image/";
	private static final int FORMAT_VERSION = 3;

	private SheetTemplateFileStore() {
	}

	public static File save(SheetTemplate template, File targetFile) throws IOException {
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
		TemplateSnapshot data = TemplateSnapshot.from(template, imageEntryNames);

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
		logger.info("模板已保存: " + sgFile.getName() + " (" + template.answerSheet().getChoiceQuestions().size() + " 道选择题, " + imageFiles.size() + " 页)");
		return sgFile;
	}

	public static SheetTemplate load(File sgFile) throws IOException {
		Objects.requireNonNull(sgFile, "sgFile");
		if (!sgFile.isFile()) {
			throw new FileNotFoundException("Template file not found: " + sgFile.getAbsolutePath());
		}

		TemplateSnapshot data = null;
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
						if (!(object instanceof TemplateSnapshot templateSnapshot)) {
							throw new IOException("Invalid template metadata: " + sgFile.getAbsolutePath());
						}
						data = templateSnapshot;
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

		SheetTemplate t = new SheetTemplate(
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
			logger.info("模板已加载: " + sgFile.getName() + " (版本 v" + data.version + ", " + t.answerSheet().getChoiceQuestions().size() + " 道选择题, " + t.subjectiveRegions().size() + " 个主观题区域, " + imageFiles.size() + " 页)");
			return t;
	}

	public static File saveTemplate(SheetTemplate template, File targetFile) throws IOException {
		return save(template, targetFile);
	}

	public static SheetTemplate readTemplate(File sgFile) throws IOException {
		return load(sgFile);
	}

	public static SheetTemplate loadTemplate(File sgFile) throws IOException {
		return load(sgFile);
	}

	public static SheetLayout removeAnswers(SheetLayout answerSheet) {
		Objects.requireNonNull(answerSheet, "answerSheet");
		List<SheetQuestion> questions = new ArrayList<>();
		for (SheetQuestion question : answerSheet.getQuestions()) {
			List<Rect> regions = new ArrayList<>();
			for (Rect rect : question.optionRegions()) {
				regions.add(new Rect(rect.x(), rect.y(), rect.width(), rect.height()));
			}
			questions.add(new SheetQuestion(question.number(), question.type(), regions, ""));
		}
		return new SheetLayout(
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

	private record TemplateSnapshot(int version, String name, String imageEntryName, List<String> imageEntryNames,
	                                SheetLayoutSnapshot answerSheet, RectSnapshot examRegionRect, RectSnapshot choiceRegionRect,
	                                RectSnapshot fillBlankRegionRect, String defaultScoreRules, int pageCount,
	                                List<SubjectiveRegionSnapshot> subjectiveRegions) implements Serializable {
			@Serial
			private static final long serialVersionUID = 1L;

			private TemplateSnapshot(int version, String name, String imageEntryName, SheetLayoutSnapshot answerSheet,
			                         RectSnapshot examRegionRect, RectSnapshot choiceRegionRect, RectSnapshot fillBlankRegionRect,
			                         String defaultScoreRules,
			                         int pageCount,
			                         List<SubjectiveRegionSnapshot> subjectiveRegions) {
				this(version, name, imageEntryName, imageEntryName == null ? List.of() : List.of(imageEntryName),
						answerSheet, examRegionRect, choiceRegionRect, fillBlankRegionRect, defaultScoreRules,
						pageCount, subjectiveRegions);
			}

			private TemplateSnapshot(int version, String name, String imageEntryName, List<String> imageEntryNames,
			                         SheetLayoutSnapshot answerSheet,
			                         RectSnapshot examRegionRect, RectSnapshot choiceRegionRect, RectSnapshot fillBlankRegionRect,
			                         String defaultScoreRules,
			                         int pageCount,
			                         List<SubjectiveRegionSnapshot> subjectiveRegions) {
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

			private static TemplateSnapshot from(SheetTemplate template, List<String> imageEntryNames) {
				return new TemplateSnapshot(
						FORMAT_VERSION,
						template.name(),
						imageEntryNames.isEmpty() ? null : imageEntryNames.get(0),
						imageEntryNames,
						SheetLayoutSnapshot.from(removeAnswers(template.answerSheet())),
						RectSnapshot.fromNullable(template.examRegionRect()),
						RectSnapshot.fromNullable(template.choiceRegionRect()),
						RectSnapshot.fromNullable(template.fillBlankRegionRect()),
						template.defaultScoreRules(),
						template.pageCount(),
						template.subjectiveRegions().stream().map(SubjectiveRegionSnapshot::from).toList()
				);
			}
		}

	private record SubjectiveRegionSnapshot(String name,
	                                        int startQuestion,
	                                        int endQuestion,
	                                        RectSnapshot region,
	                                        SubjectiveAnswerRegion.GradingMode mode,
	                                        BigDecimal maxScore,
	                                        int pageIndex) implements Serializable {
		@Serial
		private static final long serialVersionUID = 1L;

		private static SubjectiveRegionSnapshot from(SubjectiveAnswerRegion region) {
			return new SubjectiveRegionSnapshot(region.name(), region.startQuestion(), region.endQuestion(),
					RectSnapshot.from(region.region()), region.mode(), region.maxScore(), region.pageIndex());
		}

		private SubjectiveAnswerRegion toSubjectiveRegion() {
			return new SubjectiveAnswerRegion(name, startQuestion, endQuestion, region.toRect(), mode, maxScore, pageIndex);
		}
	}

	private record SheetLayoutSnapshot(String name, int imageWidth, int imageHeight, int examIdDigits,
	                                   List<SheetQuestionSnapshot> questions) implements Serializable {
		@Serial
		private static final long serialVersionUID = 1L;

		private static SheetLayoutSnapshot from(SheetLayout answerSheet) {
			List<SheetQuestionSnapshot> questions = new ArrayList<>();
			for (SheetQuestion question : answerSheet.getQuestions()) {
				questions.add(SheetQuestionSnapshot.from(question));
			}
			return new SheetLayoutSnapshot(
					answerSheet.getName(),
					answerSheet.getImageWidth(),
					answerSheet.getImageHeight(),
					answerSheet.getExamIdDigits(),
					questions
			);
		}

		private SheetLayout toAnswerSheet() {
			List<SheetQuestion> restoredQuestions = new ArrayList<>();
			for (SheetQuestionSnapshot question : questions) {
				restoredQuestions.add(question.toQuestion());
			}
			return new SheetLayout(name, imageWidth, imageHeight, examIdDigits, restoredQuestions);
		}
	}

	private record SheetQuestionSnapshot(int number, SheetQuestion.QuestionType type,
	                                     List<RectSnapshot> optionRegions) implements Serializable {
		@Serial
		private static final long serialVersionUID = 1L;

		private static SheetQuestionSnapshot from(SheetQuestion question) {
			List<RectSnapshot> regions = new ArrayList<>();
			for (Rect rect : question.optionRegions()) {
				regions.add(RectSnapshot.from(rect));
			}
			return new SheetQuestionSnapshot(question.number(), question.type(), regions);
		}

		private SheetQuestion toQuestion() {
			List<Rect> regions = new ArrayList<>();
			for (RectSnapshot rect : optionRegions) {
				regions.add(rect.toRect());
			}
			return new SheetQuestion(number, type, regions, "");
		}
	}

	private record RectSnapshot(int x, int y, int width, int height) implements Serializable {
		@Serial
		private static final long serialVersionUID = 1L;

		private static RectSnapshot from(Rect rect) {
			return new RectSnapshot(rect.x(), rect.y(), rect.width(), rect.height());
		}

		private static RectSnapshot fromNullable(Rect rect) {
			return rect == null ? null : from(rect);
		}

		private Rect toRect() {
			return new Rect(x, y, width, height);
		}
	}

	private static Rect toOpenCvRect(RectSnapshot rect) {
		return rect == null ? null : rect.toRect();
	}

	private static List<SubjectiveAnswerRegion> toSubjectiveRegions(List<SubjectiveRegionSnapshot> regions) {
		if (regions == null || regions.isEmpty()) {
			return List.of();
		}
		List<SubjectiveAnswerRegion> result = new ArrayList<>();
		for (SubjectiveRegionSnapshot region : regions) {
			if (region != null && region.region() != null) {
				result.add(region.toSubjectiveRegion());
			}
		}
		return result;
	}

	private static List<File> orderedImageFiles(TemplateSnapshot data, Map<String, File> imageFilesByEntryName) {
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