package sg.qr.kiarelemb.exam.model;

import method.qr.kiarelemb.utils.QRFileUtils;
import method.qr.kiarelemb.utils.QRLoggerUtils;
import method.qr.kiarelemb.utils.QRPropertiesUtils;
import sg.qr.kiarelemb.exam.processing.DocumentPageLoader;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Kiarelemb
 * @projectName QR_Scan_Grade
 * @className GradingProject
 * @description TODO
 * @create 2026/6/6 16:40
 */
public final class GradingProject {
	private static final Logger logger = QRLoggerUtils.getLogger(GradingProject.class);

	private static final String PROJECT_NAME = "project.name";
	private static final String TEMPLATE_FILE = "template.file";
	private static final String STANDARD_ANSWERS = "standard.answers";
	private static final String MACHINE_SUBJECTIVE_COUNT = "machine.subjective.count";
	private static final String EXAM_ID_PREFIX = "exam.id.prefix";
	private static final String ANSWER_DIRECTORY = "answer.directory";
	private static final String ANSWER_FILE_COUNT = "answer.file.count";
	private static final String ANSWER_FILE = "answer.file.";
	private static final String ANSWER_BACK_FILE = ".back";
	private static final String CONVERTED_PDF_FILE = "converted.pdf.file";
	private static final String CONVERTED_PDF_IMAGE_COUNT = "converted.pdf.image.count";
	private static final String NEXT_INDEX = "next.index";
	private static final String SUBJECTIVE_INDEX = "subjective.index";
	private static final String MANUAL_REVIEW_INDEX = "manual.review.index";
	private static final String RECOGNIZED_COUNT = "recognized.count";
	private static final String RECOGNIZED = "recognized.";
	private static final String REVIEW_COUNT = "review.count";
	private static final String REVIEW = "review.";
	private static final String REVIEW_FILE = ".file";
	private static final String REVIEW_EXAM_ID = ".examId";
	private static final String REVIEW_ANSWERS = ".answers";
	private static final String SUBJECTIVE_COUNT = "subjective.count";
	private static final String SUBJECTIVE = "subjective.";
	private static final String SUBJECTIVE_FILE = ".file";
	private static final String SUBJECTIVE_ANSWERS = ".answers";
	private static final Pattern SUBJECTIVE_NUMBER_LINE = Pattern.compile("^(\\d+)\\s*(?:[.\\uFF0E\\u3001:\\uFF1A])?\\s*(.*)$");
	private static final String STUDENT_COUNT = "student.count";
	private static final String STUDENT = "student.";
	private static final String MANUAL_SCORE_COUNT = "manual.score.count";
	private static final String MANUAL_SCORE = "manual.score.";
	private static final String MANUAL_SCORE_EXAM_ID = ".examId";
	private static final String MANUAL_SCORE_SECTION = ".section";
	private static final String MANUAL_SCORE_VALUE = ".value";
	private final String projectFilePath;
	private String templateFilePath;
	private String answerDirectoryPath;
	private String[] standardAnswers;
	private int machineSubjectiveCount;
	private String examIdPrefix = "";
	private List<File> answerFiles;
	private int index = 0;
	private int subjectiveIndex = 0;
	private int manualReviewIndex = 0;
	private int recognizedCount = 0;
	private String convertedPdfFileName = "";
	private int convertedPdfImageCount = 0;
	private Map<String, File> answerBackFilesByFrontName = new LinkedHashMap<>();
	private Map<String, String> recognizedAnswers = new LinkedHashMap<>();
	private Map<String, ReviewedAnswer> reviewedAnswersByFile = new LinkedHashMap<>();
	private Map<String, String> subjectiveAnswersByFile = new LinkedHashMap<>();
	private Map<String, String> studentNamesByExamId = new LinkedHashMap<>();
	private Map<String, Map<String, String>> manualScoresByExamId = new LinkedHashMap<>();

	public GradingProject(File projectFilePath) {
		this.projectFilePath = projectFilePath.getAbsolutePath();
	}

	public GradingProject(String projectFilePath, String templateFilePath, String answerDirectoryPath, String[] standardAnswers, List<File> answerFiles) {
		this.projectFilePath = projectFilePath;
		this.templateFilePath = templateFilePath;
		this.answerDirectoryPath = answerDirectoryPath;
		this.standardAnswers = standardAnswers;
		this.answerFiles = answerFiles;
	}

	public GradingProject(String projectFilePath, String templateFilePath, String answerDirectoryPath, String[] standardAnswers,
	                      List<File> answerFiles, Map<String, File> answerBackFilesByFrontName) {
		this(projectFilePath, templateFilePath, answerDirectoryPath, standardAnswers, answerFiles);
		this.answerBackFilesByFrontName = answerBackFilesByFrontName == null ? new LinkedHashMap<>() : new LinkedHashMap<>(answerBackFilesByFrontName);
	}

	public void read() {
		Properties prop = new Properties();
		QRPropertiesUtils.loadProp0(prop, projectFilePath);

		this.templateFilePath = QRPropertiesUtils.getPropInString(prop, TEMPLATE_FILE, "");
		this.answerDirectoryPath = QRPropertiesUtils.getPropInString(prop, ANSWER_DIRECTORY, "");
		this.standardAnswers = splitAnswers(prop.getProperty(STANDARD_ANSWERS, ""));
		this.machineSubjectiveCount = QRPropertiesUtils.getPropInInteger(prop, MACHINE_SUBJECTIVE_COUNT, 0);
		this.examIdPrefix = QRPropertiesUtils.getPropInString(prop, EXAM_ID_PREFIX, "");
		this.convertedPdfFileName = QRPropertiesUtils.getPropInString(prop, CONVERTED_PDF_FILE, "");
		this.convertedPdfImageCount = QRPropertiesUtils.getPropInInteger(prop, CONVERTED_PDF_IMAGE_COUNT, 0);
		this.index = QRPropertiesUtils.getPropInInteger(prop, NEXT_INDEX, 0);
		this.subjectiveIndex = QRPropertiesUtils.getPropInInteger(prop, SUBJECTIVE_INDEX, 0);
		this.manualReviewIndex = QRPropertiesUtils.getPropInInteger(prop, MANUAL_REVIEW_INDEX, 0);
		this.recognizedCount = QRPropertiesUtils.getPropInInteger(prop, RECOGNIZED_COUNT, 0);
		this.recognizedAnswers = new LinkedHashMap<>();
		for (String key : prop.stringPropertyNames()) {
			if (key.startsWith(RECOGNIZED) && !RECOGNIZED_COUNT.equals(key)) {
				String examineeId = key.substring(RECOGNIZED.length()).trim();
				if (!examineeId.isEmpty()) {
					this.recognizedAnswers.put(examineeId, prop.getProperty(key, "").trim());
				}
			}
		}
		this.recognizedCount = this.recognizedAnswers.size();
		this.reviewedAnswersByFile = new LinkedHashMap<>();
		int reviewCount = QRPropertiesUtils.getPropInInteger(prop, REVIEW_COUNT, 0);
		for (int i = 0; i < reviewCount; i++) {
			String prefix = REVIEW + i;
			String fileName = prop.getProperty(prefix + REVIEW_FILE, "").trim();
			String examineeId = prop.getProperty(prefix + REVIEW_EXAM_ID, "").trim();
			String answers = prop.getProperty(prefix + REVIEW_ANSWERS, "").trim();
			if (!fileName.isEmpty() && !examineeId.isEmpty()) {
				this.reviewedAnswersByFile.put(fileName, new ReviewedAnswer(examineeId, answers));
			}
		}
		this.subjectiveAnswersByFile = new LinkedHashMap<>();
		int subjectiveCount = QRPropertiesUtils.getPropInInteger(prop, SUBJECTIVE_COUNT, 0);
		for (int i = 0; i < subjectiveCount; i++) {
			String prefix = SUBJECTIVE + i;
			String fileName = prop.getProperty(prefix + SUBJECTIVE_FILE, "").trim();
			String answers = unescapeMultiline(prop.getProperty(prefix + SUBJECTIVE_ANSWERS, "").trim());
			if (!fileName.isEmpty()) {
				this.subjectiveAnswersByFile.put(fileName, answers);
			}
		}
		this.studentNamesByExamId = new LinkedHashMap<>();
		for (String key : prop.stringPropertyNames()) {
			if (key.startsWith(STUDENT) && !STUDENT_COUNT.equals(key)) {
				String examineeId = key.substring(STUDENT.length()).trim();
				String name = prop.getProperty(key, "").trim();
				if (!examineeId.isEmpty() && !name.isEmpty()) {
					this.studentNamesByExamId.put(examineeId, name);
				}
			}
		}
		this.manualScoresByExamId = new LinkedHashMap<>();
		int manualScoreCount = QRPropertiesUtils.getPropInInteger(prop, MANUAL_SCORE_COUNT, 0);
		for (int i = 0; i < manualScoreCount; i++) {
			String prefix = MANUAL_SCORE + i;
			String examineeId = prop.getProperty(prefix + MANUAL_SCORE_EXAM_ID, "").trim();
			String section = prop.getProperty(prefix + MANUAL_SCORE_SECTION, "").trim();
			String value = prop.getProperty(prefix + MANUAL_SCORE_VALUE, "").trim();
			if (!examineeId.isEmpty() && !section.isEmpty() && !value.isEmpty()) {
				this.manualScoresByExamId
						.computeIfAbsent(examineeId, key -> new LinkedHashMap<>())
						.put(section, value);
			}
		}

		int answerFileCount = QRPropertiesUtils.getPropInInteger(prop, ANSWER_FILE_COUNT, 0);
		this.answerFiles = new LinkedList<>();
		this.answerBackFilesByFrontName = new LinkedHashMap<>();
		for (int i = 0; i < answerFileCount; i++) {
			String fileName = prop.getProperty(ANSWER_FILE + i, "").trim();
			if (fileName.isEmpty()) {
				continue;
			}
			File file = new File(fileName);
			if (!file.isAbsolute() && this.answerDirectoryPath != null) {
				file = new File(this.answerDirectoryPath, fileName);
			}
			this.answerFiles.add(file);
			String backFileName = prop.getProperty(ANSWER_FILE + i + ANSWER_BACK_FILE, "").trim();
			if (!backFileName.isEmpty()) {
				File backFile = new File(backFileName);
				if (!backFile.isAbsolute() && this.answerDirectoryPath != null) {
					backFile = new File(this.answerDirectoryPath, backFileName);
				}
				this.answerBackFilesByFrontName.put(file.getName(), backFile);
			}
		}
	}

	public void write() {
		LinkedList<String> list = new LinkedList<>();
		List<File> files = this.answerFiles == null ? List.of() : this.answerFiles;
		list.add("# QR Scan Grade Project");
		list.add(PROJECT_NAME + "=" + QRFileUtils.getFileName(projectFilePath));
		list.add(TEMPLATE_FILE + "=" + this.templateFilePath);
		list.add(STANDARD_ANSWERS + "=" + String.join(" ", this.standardAnswers));
		list.add(MACHINE_SUBJECTIVE_COUNT + "=" + this.machineSubjectiveCount);
		list.add(EXAM_ID_PREFIX + "=" + this.examIdPrefix);
		list.add(ANSWER_DIRECTORY + "=" + this.answerDirectoryPath);
		if (convertedPdfFileName != null && !convertedPdfFileName.isBlank()) {
			list.add(CONVERTED_PDF_FILE + "=" + convertedPdfFileName);
			list.add(CONVERTED_PDF_IMAGE_COUNT + "=" + convertedPdfImageCount);
		}
		list.add(ANSWER_FILE_COUNT + "=" + files.size());
		for (int i = 0; i < files.size(); i++) {
			File file = files.get(i);
			list.add(ANSWER_FILE + i + "=" + file.getName());
			File backFile = this.answerBackFilesByFrontName.get(file.getName());
			if (backFile != null) {
				list.add(ANSWER_FILE + i + ANSWER_BACK_FILE + "=" + backFile.getName());
			}
		}
		list.add(NEXT_INDEX + "=" + index);
		list.add(SUBJECTIVE_INDEX + "=" + subjectiveIndex);
		list.add(MANUAL_REVIEW_INDEX + "=" + manualReviewIndex);
		list.add(RECOGNIZED_COUNT + "=" + this.recognizedAnswers.size());
		list.add("# recognized.<准考证号>=识别的答案，用空格分开");
		for (Map.Entry<String, String> entry : this.recognizedAnswers.entrySet()) {
			list.add(RECOGNIZED + entry.getKey() + "=" + entry.getValue());
		}
		list.add(REVIEW_COUNT + "=" + this.reviewedAnswersByFile.size());
		int reviewIndex = 0;
		for (Map.Entry<String, ReviewedAnswer> entry : this.reviewedAnswersByFile.entrySet()) {
			String prefix = REVIEW + reviewIndex++;
			list.add(prefix + REVIEW_FILE + "=" + entry.getKey());
			list.add(prefix + REVIEW_EXAM_ID + "=" + entry.getValue().examineeId());
			list.add(prefix + REVIEW_ANSWERS + "=" + entry.getValue().answers());
		}
		list.add(SUBJECTIVE_COUNT + "=" + this.subjectiveAnswersByFile.size());
		int subjectiveIndex = 0;
		for (Map.Entry<String, String> entry : this.subjectiveAnswersByFile.entrySet()) {
			String prefix = SUBJECTIVE + subjectiveIndex++;
			list.add(prefix + SUBJECTIVE_FILE + "=" + entry.getKey());
			list.add(prefix + SUBJECTIVE_ANSWERS + "=" + escapeMultiline(entry.getValue()));
		}
		list.add(STUDENT_COUNT + "=" + this.studentNamesByExamId.size());
		list.add("# student.<准考证号>=姓名");
		for (Map.Entry<String, String> entry : this.studentNamesByExamId.entrySet()) {
			list.add(STUDENT + entry.getKey() + "=" + entry.getValue());
		}
		int manualScoreSize = this.manualScoresByExamId.values().stream().mapToInt(Map::size).sum();
		list.add(MANUAL_SCORE_COUNT + "=" + manualScoreSize);
		int manualScoreIndex = 0;
		for (Map.Entry<String, Map<String, String>> examEntry : this.manualScoresByExamId.entrySet()) {
			for (Map.Entry<String, String> scoreEntry : examEntry.getValue().entrySet()) {
				String prefix = MANUAL_SCORE + manualScoreIndex++;
				list.add(prefix + MANUAL_SCORE_EXAM_ID + "=" + examEntry.getKey());
				list.add(prefix + MANUAL_SCORE_SECTION + "=" + scoreEntry.getKey());
				list.add(prefix + MANUAL_SCORE_VALUE + "=" + scoreEntry.getValue());
			}
		}
		QRFileUtils.fileWriterWithUTF8(projectFilePath, list);
		String content = String.join("\n", list).replace("\n", "\\n").replace("\r", "\\r");
		logger.info("项目已保存: " + content);
	}

	private String[] splitAnswers(String answers) {
		String value = answers == null ? "" : answers.trim();
		return value.isEmpty() ? new String[0] : value.split("[ \\t\\r\\n]+");
	}

	public String projectFilePath() {
		return projectFilePath;
	}

	public String templateFilePath() {
		return templateFilePath;
	}

	public void setTemplateFilePath(String templateFilePath) {
		this.templateFilePath = templateFilePath;
	}

	public String answerDirectoryPath() {
		return answerDirectoryPath;
	}

	public void setAnswerDirectoryPath(String answerDirectoryPath) {
		this.answerDirectoryPath = answerDirectoryPath;
	}

	public String[] standardAnswers() {
		return standardAnswers;
	}

	public void setStandardAnswers(String[] standardAnswers) {
		this.standardAnswers = standardAnswers;
	}

	public int machineSubjectiveCount() {
		return machineSubjectiveCount;
	}

	public void setMachineSubjectiveCount(int machineSubjectiveCount) {
		this.machineSubjectiveCount = Math.max(0, machineSubjectiveCount);
	}

	public String examIdPrefix() {
		return examIdPrefix == null ? "" : examIdPrefix;
	}

	public void setExamIdPrefix(String examIdPrefix) {
		String value = examIdPrefix == null ? "" : examIdPrefix.trim();
		if (!value.matches("\\d*")) {
			throw new IllegalArgumentException("准考证号前缀只能包含数字。");
		}
		this.examIdPrefix = value;
	}

	public List<File> answerFiles() {
		return answerFiles;
	}

	public void setAnswerFiles(List<File> answerFiles) {
		this.answerFiles = answerFiles;
	}

	public File answerBackFileFor(File answerFile) {
		if (answerFile == null) {
			return null;
		}
		return answerBackFilesByFrontName.get(answerFile.getName());
	}

	public Map<String, File> answerBackFilesByFrontName() {
		return Collections.unmodifiableMap(answerBackFilesByFrontName);
	}

	public void setConvertedPdf(File pdfFile, int imageCount) {
		this.convertedPdfFileName = pdfFile == null ? "" : pdfFile.getName();
		this.convertedPdfImageCount = Math.max(0, imageCount);
	}

	public boolean refreshAnswerFilesFromDirectory() {
		return refreshAnswerFilesFromDirectory(1);
	}

	public boolean refreshAnswerFilesFromDirectory(int pageCount) {
		if (answerDirectoryPath == null || answerDirectoryPath.isBlank()) {
			return false;
		}
		File directory = new File(answerDirectoryPath);
		List<File> scannedFiles;
		try {
			List<File> allImages = answerImagesFromDirectory(directory);
			scannedFiles = new ArrayList<>();
			Map<String, File> backFiles = new LinkedHashMap<>();
			int pagesPerSubmission = Math.max(1, pageCount);
			for (int i = 0; i < allImages.size(); i += pagesPerSubmission) {
				File front = allImages.get(i);
				scannedFiles.add(front);
				if (pagesPerSubmission >= 2 && i + 1 < allImages.size()) {
					backFiles.put(front.getName(), allImages.get(i + 1));
				}
			}
			this.answerBackFilesByFrontName = backFiles;
		} catch (Exception ex) {
			scannedFiles = new ArrayList<>();
		}
		if (sameAnswerFiles(this.answerFiles, scannedFiles)) {
			return false;
		}
		this.answerFiles = scannedFiles;
		logger.info("刷新答卷: " + scannedFiles.size() + " 份");
		if (index > scannedFiles.size()) {
			index = scannedFiles.size();
		}
		return true;
	}

	private List<File> answerImagesFromDirectory(File directory) throws java.io.IOException {
		if (convertedPdfFileName != null && !convertedPdfFileName.isBlank()) {
			File convertedPdf = new File(directory, convertedPdfFileName);
			List<File> convertedImages = DocumentPageLoader.convertedPdfImages(convertedPdf);
			if (convertedImages.size() == convertedPdfImageCount && convertedPdfImageCount > 0) {
				return convertedImages;
			}
		}
		List<File> images = DocumentPageLoader.sortedAnswerImages(directory);
		File singlePdf = DocumentPageLoader.singlePdfFile(directory);
		if (singlePdf != null && !images.isEmpty()) {
			setConvertedPdf(singlePdf, images.size());
		}
		return images;
	}

	private boolean sameAnswerFiles(List<File> first, List<File> second) {
		List<File> left = first == null ? List.of() : first;
		if (left.size() != second.size()) {
			return false;
		}
		for (int i = 0; i < left.size(); i++) {
			if (!left.get(i).getName().equals(second.get(i).getName())) {
				return false;
			}
		}
		return true;
	}

	public int index() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public int subjectiveIndex() {
		return subjectiveIndex;
	}

	public void setSubjectiveIndex(int subjectiveIndex) {
		this.subjectiveIndex = subjectiveIndex;
	}

	public int manualReviewIndex() {
		return manualReviewIndex;
	}

	public void setManualReviewIndex(int manualReviewIndex) {
		this.manualReviewIndex = Math.max(0, manualReviewIndex);
	}

	public int recognizedCount() {
		return recognizedCount;
	}

	public void setRecognizedCount(int recognizedCount) {
		this.recognizedCount = recognizedCount;
	}

	public Map<String, String> recognizedAnswers() {
		return recognizedAnswers;
	}

	public Map<String, String> combinedAnswersByExamId() {
		Map<String, String> combined = reviewedAnswersComplete() ? new LinkedHashMap<>() : new LinkedHashMap<>(this.recognizedAnswers);
		for (Map.Entry<String, ReviewedAnswer> entry : this.reviewedAnswersByFile.entrySet()) {
			String fileName = entry.getKey();
			ReviewedAnswer reviewed = entry.getValue();
			if (reviewed == null || reviewed.examineeId() == null || reviewed.examineeId().isBlank()) {
				continue;
			}
			String choiceAnswers = reviewed.answers() == null ? "" : reviewed.answers().trim();
			if (choiceAnswers.isEmpty()) {
				choiceAnswers = combined.getOrDefault(reviewed.examineeId(), "").trim();
			}
			String subjective = this.subjectiveAnswersByFile.get(fileName);
			List<String> subjectiveTokens = subjectiveAnswerTokens(subjective);
			if (subjectiveTokens.isEmpty()) {
				combined.put(reviewed.examineeId(), choiceAnswers);
				continue;
			}
			String allAnswers = choiceAnswers.isEmpty()
					? String.join(" ", subjectiveTokens)
					: choiceAnswers + " " + String.join(" ", subjectiveTokens);
			combined.put(reviewed.examineeId(), allAnswers.trim());
		}
		return combined;
	}

	private boolean reviewedAnswersComplete() {
		if (this.answerFiles == null || this.answerFiles.isEmpty()) {
			return false;
		}
		for (File answerFile : this.answerFiles) {
			if (answerFile == null || !this.reviewedAnswersByFile.containsKey(answerFile.getName())) {
				return false;
			}
		}
		return true;
	}

	public Map<String, String> subjectiveAnswersByExamId() {
		Map<String, String> answers = new LinkedHashMap<>();
		for (Map.Entry<String, ReviewedAnswer> entry : this.reviewedAnswersByFile.entrySet()) {
			ReviewedAnswer reviewed = entry.getValue();
			if (reviewed == null || reviewed.examineeId() == null || reviewed.examineeId().isBlank()) {
				continue;
			}
			String subjective = this.subjectiveAnswersByFile.get(entry.getKey());
			if (subjective != null && !subjective.isBlank()) {
				answers.put(reviewed.examineeId(), subjective);
			}
		}
		return answers;
	}

	public void putRecognizedAnswer(String examineeId, String answers) {
		if (examineeId == null || examineeId.isBlank()) {
			return;
		}
		this.recognizedAnswers.put(examineeId.trim(), normalizeAnswers(answers));
		this.recognizedCount = this.recognizedAnswers.size();
		logger.fine("识别: 考号=" + examineeId.trim() + " 答案=[" + normalizeAnswers(answers) + "]");
	}

	public ReviewedAnswer reviewedAnswerFor(File answerFile) {
		return answerFile == null ? null : this.reviewedAnswersByFile.get(answerFile.getName());
	}

	public void putReviewedAnswer(File answerFile, String examineeId, String answers) {
		if (answerFile == null || examineeId == null || examineeId.isBlank()) {
			return;
		}
		this.reviewedAnswersByFile.put(answerFile.getName(),
				new ReviewedAnswer(examineeId.trim(), normalizeAnswers(answers)));
		this.recognizedCount = this.recognizedAnswers.size();
	}

	public void putReviewedAnswerIfDifferent(File answerFile, String examineeId, String answers) {
		if (answerFile == null || examineeId == null || examineeId.isBlank()) {
			return;
		}
		String id = examineeId.trim();
		String normalizedAnswers = normalizeAnswers(answers);
		// 始终保存审阅结果，即使与识别结果一致。
		// 若删除匹配的条目，下次加载该文件时 reviewedAnswer 为 null，
		// 会触发重新识别产生不同考号的多余条目。
		this.reviewedAnswersByFile.put(answerFile.getName(), new ReviewedAnswer(id, normalizedAnswers));
		this.recognizedCount = this.recognizedAnswers.size();
	}

	public void remapExamineeIds(Map<String, String> examineeIdMapping) {
		if (examineeIdMapping == null || examineeIdMapping.isEmpty()) {
			return;
		}
		Map<String, String> normalized = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : examineeIdMapping.entrySet()) {
			String oldId = entry.getKey() == null ? "" : entry.getKey().trim();
			String newId = entry.getValue() == null ? "" : entry.getValue().trim();
			if (!oldId.isEmpty() && !newId.isEmpty() && !oldId.equals(newId)) {
				normalized.put(oldId, newId);
			}
		}
		if (normalized.isEmpty()) {
			return;
		}
		Map<String, String> updatedRecognizedAnswers = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : this.recognizedAnswers.entrySet()) {
			updatedRecognizedAnswers.put(normalized.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue());
		}
		this.recognizedAnswers = updatedRecognizedAnswers;
		this.recognizedCount = this.recognizedAnswers.size();

		Map<String, ReviewedAnswer> updatedReviewedAnswers = new LinkedHashMap<>();
		for (Map.Entry<String, ReviewedAnswer> entry : this.reviewedAnswersByFile.entrySet()) {
			ReviewedAnswer reviewed = entry.getValue();
			if (reviewed == null) {
				updatedReviewedAnswers.put(entry.getKey(), null);
			} else {
				String examineeId = normalized.getOrDefault(reviewed.examineeId(), reviewed.examineeId());
				updatedReviewedAnswers.put(entry.getKey(), new ReviewedAnswer(examineeId, reviewed.answers()));
			}
		}
		this.reviewedAnswersByFile = updatedReviewedAnswers;

		Map<String, String> updatedStudentNames = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : this.studentNamesByExamId.entrySet()) {
			updatedStudentNames.put(normalized.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue());
		}
		this.studentNamesByExamId = updatedStudentNames;

		Map<String, Map<String, String>> updatedManualScores = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, String>> entry : this.manualScoresByExamId.entrySet()) {
			updatedManualScores.put(normalized.getOrDefault(entry.getKey(), entry.getKey()), new LinkedHashMap<>(entry.getValue()));
		}
		this.manualScoresByExamId = updatedManualScores;
	}

	private static String normalizeAnswers(String answers) {
		String value = answers == null ? "" : answers.trim();
		return value.isEmpty() ? "" : String.join(" ", value.split("[ \\t\\r\\n]+"));
	}

	private static boolean answersEqual(String first, String second) {
		return normalizeAnswers(first).equals(normalizeAnswers(second));
	}

	public record ReviewedAnswer(String examineeId, String answers) {
	}

	public String subjectiveAnswerFor(File answerFile) {
		return answerFile == null ? "" : this.subjectiveAnswersByFile.getOrDefault(answerFile.getName(), "");
	}

	public void putSubjectiveAnswer(File answerFile, String answers) {
		if (answerFile == null) {
			return;
		}
		this.subjectiveAnswersByFile.put(answerFile.getName(), answers == null ? "" : answers);
	}

	public boolean allSubjectiveAnswersSaved() {
		if (this.answerFiles == null || this.answerFiles.isEmpty()) {
			return true;
		}
		for (File answerFile : this.answerFiles) {
			String answers = answerFile == null ? "" : this.subjectiveAnswersByFile.get(answerFile.getName());
			if (answers == null || answers.isBlank()) {
				return false;
			}
		}
		return true;
	}

	public int savedSubjectiveAnswerCount() {
		if (this.answerFiles == null || this.answerFiles.isEmpty()) {
			return 0;
		}
		int count = 0;
		for (File answerFile : this.answerFiles) {
			String answers = answerFile == null ? "" : this.subjectiveAnswersByFile.get(answerFile.getName());
			if (answers != null && !answers.isBlank()) {
				count++;
			}
		}
		return count;
	}

	private static List<String> subjectiveAnswerTokens(String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		List<String> tokens = new ArrayList<>();
		String current = null;
		boolean started = false;
		for (String rawLine : text.split("\\R")) {
			String line = rawLine.trim();
			if (line.isEmpty()) {
				if (!started) {
					tokens.add("Z");
				}
				continue;
			}
			Matcher matcher = SUBJECTIVE_NUMBER_LINE.matcher(line);
			if (matcher.matches()) {
				started = true;
				if (current != null) {
					tokens.add(current.isBlank() ? "Z" : current);
				}
				current = matcher.group(2).trim();
			} else if (started && current != null) {
				current = current.isBlank() ? line : current + line;
			} else if (!looksLikeSubjectiveTitle(line)) {
				tokens.add(line);
			}
		}
		if (current != null) {
			tokens.add(current.isBlank() ? "Z" : current);
		}
		return tokens;
	}

	private static boolean looksLikeSubjectiveTitle(String line) {
		return line.contains("填空") || line.contains("主观") || line.contains("答案");
	}

	private static String escapeMultiline(String text) {
		return text == null ? "" : text.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n");
	}

	private static String unescapeMultiline(String text) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		StringBuilder builder = new StringBuilder();
		boolean escaping = false;
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			if (escaping) {
				builder.append(switch (ch) {
					case 'n' -> '\n';
					case 'r' -> '\r';
					default -> ch;
				});
				escaping = false;
			} else if (ch == '\\') {
				escaping = true;
			} else {
				builder.append(ch);
			}
		}
		if (escaping) {
			builder.append('\\');
		}
		return builder.toString();
	}

	public Map<String, String> studentNamesByExamId() {
		return studentNamesByExamId;
	}

	public void setStudentNamesByExamId(Map<String, String> studentNamesByExamId) {
		this.studentNamesByExamId = studentNamesByExamId == null ? new LinkedHashMap<>() : new LinkedHashMap<>(studentNamesByExamId);
	}

	public Map<String, String> manualScoresFor(String examineeId) {
		Map<String, String> scores = this.manualScoresByExamId.get(examineeId);
		return scores == null ? Map.of() : Collections.unmodifiableMap(scores);
	}

	public Map<String, Map<String, String>> manualScoresByExamId() {
		Map<String, Map<String, String>> copy = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, String>> entry : this.manualScoresByExamId.entrySet()) {
			copy.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
		}
		return Collections.unmodifiableMap(copy);
	}

	public int savedManualScoreCount() {
		return this.manualScoresByExamId.values().stream().mapToInt(Map::size).sum();
	}

	public void putManualScore(String examineeId, String sectionName, String score) {
		if (examineeId == null || examineeId.isBlank() || sectionName == null || sectionName.isBlank()) {
			return;
		}
		String value = score == null ? "" : score.trim();
		Map<String, String> scores = this.manualScoresByExamId.computeIfAbsent(examineeId, key -> new LinkedHashMap<>());
		if (value.isEmpty()) {
			scores.remove(sectionName);
		} else {
			scores.put(sectionName, value);
		}
		if (scores.isEmpty()) {
			this.manualScoresByExamId.remove(examineeId);
		}
	}
}
