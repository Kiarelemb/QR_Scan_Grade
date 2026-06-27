package sg.qr.kiarelemb.exam;

import method.qr.kiarelemb.utils.QRCodePack;
import method.qr.kiarelemb.utils.QRLoggerUtils;
import method.qr.kiarelemb.utils.QRStringUtils;
import org.bytedeco.opencv.opencv_core.Rect;
import sg.qr.kiarelemb.MainWindow;
import sg.qr.kiarelemb.component.ProjectStateSaver;
import sg.qr.kiarelemb.data.Keys;
import sg.qr.kiarelemb.exam.model.GradingProject;
import sg.qr.kiarelemb.exam.model.SheetQuestion;
import sg.qr.kiarelemb.exam.model.SheetTemplate;
import sg.qr.kiarelemb.exam.model.SubjectiveAnswerRegion;
import sg.qr.kiarelemb.exam.processing.SheetCalibrator;
import sg.qr.kiarelemb.exam.processing.BaiduHandwritingOcr;
import sg.qr.kiarelemb.exam.processing.GoogleVisionHandwritingOcr;
import sg.qr.kiarelemb.exam.processing.SheetImagePreprocessor;
import swing.qr.kiarelemb.QRSwing;
import swing.qr.kiarelemb.basic.*;
import swing.qr.kiarelemb.inter.QRActionRegister;
import swing.qr.kiarelemb.task.QRTaskOptions;
import swing.qr.kiarelemb.task.QRTaskRunner;
import swing.qr.kiarelemb.task.QRTaskWorker;
import swing.qr.kiarelemb.theme.QRColorsAndFonts;
import swing.qr.kiarelemb.utils.PicturePanel;
import swing.qr.kiarelemb.window.basic.QRDialog;
import swing.qr.kiarelemb.window.enhance.QROpinionDialog;
import swing.qr.kiarelemb.window.enhance.QRSmallTipShow;
import swing.qr.kiarelemb.window.utils.QRValueInputDialog;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SubjectiveOcrReviewPanel extends QRPanel implements ProjectStateSaver {
	private static final Logger logger = QRLoggerUtils.getLogger(SubjectiveOcrReviewPanel.class);
	private static final String CIPHER_PREFIX = "qrenc:";
	private static final String CIPHER_KEY = "QR_Scan_Grade_Baidu_OCR";
	private static final Pattern OCR_NUMBER_LINE = Pattern.compile("^(\\d+)\\s*[.．、:：]?\\s*(.*)$");

	private final GradingProject project;
	private final SheetTemplate template;
	private final PicturePanel picturePanel = new PicturePanel();
	private final QRTextPane textPane = new QRTextPane();
	private final QRLabel titleLabel = new QRLabel("主观题校对");
	private final QRLabel progressLabel = new QRLabel();
	private final Set<String> autoOcrAttemptedFiles = new HashSet<>();
	private BufferedImage currentSubjectiveImage;
	private QRTaskWorker<SubjectiveImageLoadResult> imageLoadWorker;
	private QRTaskWorker<OcrTaskResult> ocrWorker;
	private int imageLoadSerial;
	private int ocrSerial;
	private int index;

	public SubjectiveOcrReviewPanel(GradingProject project, SheetTemplate template) {
		this.project = project;
		this.template = template;
		this.index = project.subjectiveIndex();
		initView();
		loadCurrent();
	}

	public static boolean hasSubjectiveQuestions(GradingProject project, SheetTemplate template) {
		return project != null && project.machineSubjectiveCount() > 0 && hasOcrRegion(template);
	}

	private void initView() {
		setLayout(new BorderLayout(0, 6));
		add(buildTopPanel(), BorderLayout.NORTH);

		QRSplitPane splitPane = new QRSplitPane(JSplitPane.VERTICAL_SPLIT);
		splitPane.setResizeWeight(0.62);
		picturePanel.setZoomRange(0.25, 5.0);
		splitPane.setTopComponent(picturePanel);

		textPane.setFont(QRColorsAndFonts.createFont(28));
		splitPane.setBottomComponent(textPane.addScrollPane());
		add(splitPane, BorderLayout.CENTER);
	}

	private Component buildTopPanel() {
		QRPanel panel = new QRPanel(false, new BorderLayout(8, 0));
		panel.setBorder(new LineBorder(QRColorsAndFonts.FRAME_COLOR_BACK, 10));
		titleLabel.setFont(QRColorsAndFonts.createFont(18));
		panel.add(titleLabel, BorderLayout.WEST);

		QRPanel buttons = new QRPanel(false, new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttons.add(button("OCR设置", event -> showOcrKeyDialog()));
		buttons.add(button("OCR识别", event -> recognizeWithOcr()));
		buttons.add(button("跳转", event -> jump()));

		QRRoundButton back = new QRRoundButton("返回");
		back.setPreferredSize(110, 30);
		back.addClickAction(event -> backToChoiceReview());
		buttons.add(back);

		buttons.add(button("上一张", event -> previous()));
		QRRoundButton nextBtn = button("下一张", event -> next(event));
		buttons.add(nextBtn);
		buttons.add(button("进入算分", event -> finish()));
		panel.add(buttons, BorderLayout.EAST);

		QRSwing.registerGlobalAction("ctrl + Enter", event -> {
			if (nextBtn.isVisible()) {
				next(null);
			}
		}, true);


		QRPanel wrapper = new QRPanel(false, new BorderLayout());
		wrapper.add(panel, BorderLayout.NORTH);
		progressLabel.setTextCenter();
		wrapper.add(progressLabel, BorderLayout.SOUTH);
		return wrapper;
	}

	private QRRoundButton button(String text, QRActionRegister<ActionEvent> ar) {
		QRRoundButton button = new QRRoundButton(text);
		button.setPreferredSize(100, 30);
		button.addClickAction(ar);
		return button;
	}

	private void loadCurrent() {
		if (project.answerFiles() == null || project.answerFiles().isEmpty()) {
			return;
		}
		index = Math.max(0, Math.min(index, project.answerFiles().size() - 1));
		project.setSubjectiveIndex(index);
		project.write();
		File answerFile = project.answerFiles().get(index);
		progressLabel.setText("当前：" + answerFile.getName() + "，进度：" + (index + 1) + " / " + project.answerFiles().size());
		startImageLoadTask(answerFile, index);
	}

	private void startImageLoadTask(File answerFile, int answerIndex) {
		int serial = ++imageLoadSerial;
		if (imageLoadWorker != null && !imageLoadWorker.isDone()) {
			imageLoadWorker.cancel(true);
		}
		currentSubjectiveImage = null;
		setCursorWait();
		QRTaskOptions options = new QRTaskOptions()
				.onSuccess((SubjectiveImageLoadResult result) -> applyImageLoadResult(serial, result))
				.onError(error -> handleImageLoadError(serial, error))
				.onCancelled(() -> {
					if (serial == imageLoadSerial) {
						setCursorDefault();
					}
				});
		imageLoadWorker = QRTaskRunner.run(options, context -> {
			context.message("正在读取主观题区域...");
			SubjectiveAnswerRegion modelRegion = subjectiveRegion(template);
			File subjectiveFile = subjectiveImageFile(answerFile, modelRegion);
			BufferedImage image = ImageIO.read(subjectiveFile);
			context.checkCancelled();
			if (image == null) {
				throw new IOException("不支持的图片格式或图片已损坏。");
			}
			context.message("正在校准主观题区域...");
			BufferedImage cropped = cropSubjectiveRegion(image, calibratedSubjectiveRegion(subjectiveFile, modelRegion));
			String saved = project.subjectiveAnswerFor(answerFile);
			boolean autoOcr = saved.isBlank() && hasConfiguredOcrKey() && autoOcrAttemptedFiles.add(answerFile.getName());
			return new SubjectiveImageLoadResult(answerIndex, answerFile, cropped, saved, autoOcr);
		});
	}

	private void applyImageLoadResult(int serial, SubjectiveImageLoadResult result) {
		if (serial != imageLoadSerial || result.answerIndex() != index) {
			return;
		}
		setCursorDefault();
		currentSubjectiveImage = result.image();
		picturePanel.setImage(currentSubjectiveImage, pictureDisplaySize(currentSubjectiveImage));
		picturePanel.resetView();
		textPane.setText(result.savedText().isBlank() ? defaultSubjectiveText() : displaySubjectiveText(result.savedText()));
		if (result.autoOcr()) {
			autoRecognizeCurrent();
		}
	}

	private void handleImageLoadError(int serial, Throwable error) {
		if (serial != imageLoadSerial) {
			return;
		}
		setCursorDefault();
		QROpinionDialog.messageErrShow(MainWindow.INSTANCE, "读取主观题区域失败：\n" + error.getMessage());
	}

	private CroppedRegion calibratedSubjectiveRegion(File answerFile, SubjectiveAnswerRegion modelRegion) {
		try {
			SheetCalibrator.CalibrationResult calibration = SheetCalibrator.calibrate(SheetImagePreprocessor.preprocess(answerFile), template, template.answerSheet());
			SubjectiveAnswerRegion region = subjectiveRegion(new SheetTemplate(template.name(), template.pictureFile(), calibration.answerSheet(), calibration.examRegionRect(), calibration.choiceRegionRect(), calibration.fillBlankRegionRect(), template.defaultScoreRules(), template.pageCount(), template.subjectiveRegions(), template.pictureFiles()));
			if (region != null) {
				Rect expanded = expandSingleFillRegion(region.region(), calibration.choiceRegionRect(), calibration.answerSheet().getFillBlankQuestions().size());
				return new CroppedRegion(expanded, calibration.answerSheet().getImageWidth(), calibration.answerSheet().getImageHeight());
			}
		} catch (Exception ignored) {
		}
		if (modelRegion == null) {
			return null;
		}
		Rect expanded = expandSingleFillRegion(modelRegion.region(), template.choiceRegionRect(), template.answerSheet().getFillBlankQuestions().size());
		return new CroppedRegion(expanded, template.answerSheet().getImageWidth(), template.answerSheet().getImageHeight());
	}

	private File subjectiveImageFile(File answerFile, SubjectiveAnswerRegion region) {
		if (region == null || region.pageIndex() <= 0) {
			return answerFile;
		}
		File backFile = project.answerBackFileFor(answerFile);
		return backFile == null ? answerFile : backFile;
	}

	private Dimension pictureDisplaySize(BufferedImage image) {
		int imageW = Math.max(1, image.getWidth());
		int imageH = Math.max(1, image.getHeight());
		int windowW = Math.max(900, MainWindow.INSTANCE.getWidth());
		int windowH = Math.max(700, MainWindow.INSTANCE.getHeight());
		int maxW = Math.max(720, windowW - 140);
		int maxH = Math.max(300, (int) Math.round(windowH * 0.42));
		double scale = Math.min(1.0, Math.min((double) maxW / imageW, (double) maxH / imageH));
		return new Dimension(
				Math.max(1, (int) Math.round(imageW * scale)),
				Math.max(1, (int) Math.round(imageH * scale)));
	}

	private BufferedImage cropSubjectiveRegion(BufferedImage image, CroppedRegion region) {
		if (region == null || region.rect() == null) {
			return image;
		}
		Rect rect = scaleRegionToImage(region, image);
		int padding = Math.max(12, Math.min(image.getWidth(), image.getHeight()) / 150);
		int x = Math.max(0, Math.min(rect.x() - padding, image.getWidth() - 1));
		int y = Math.max(0, Math.min(rect.y() - padding, image.getHeight() - 1));
		int right = Math.max(x + 1, Math.min(rect.x() + rect.width() + padding, image.getWidth()));
		int bottom = Math.max(y + 1, Math.min(rect.y() + rect.height() + padding, image.getHeight()));
		return image.getSubimage(x, y, right - x, bottom - y);
	}

	private Rect scaleRegionToImage(CroppedRegion region, BufferedImage image) {
		Rect rect = region.rect();
		double sx = (double) image.getWidth() / Math.max(1, region.baseWidth());
		double sy = (double) image.getHeight() / Math.max(1, region.baseHeight());
		return new Rect((int) Math.round(rect.x() * sx), (int) Math.round(rect.y() * sy), Math.max(1, (int) Math.round(rect.width() * sx)), Math.max(1, (int) Math.round(rect.height() * sy)));
	}

	private SubjectiveAnswerRegion subjectiveRegion(SheetTemplate regionTemplate) {
		if (regionTemplate == null) {
			return null;
		}
		if (!regionTemplate.subjectiveRegions().isEmpty()) {
			for (SubjectiveAnswerRegion region : regionTemplate.subjectiveRegions()) {
				if (region.mode() == SubjectiveAnswerRegion.GradingMode.OCR
					|| region.mode() == SubjectiveAnswerRegion.GradingMode.MIXED) {
					return region;
				}
			}
			return null;
		}
		List<SheetQuestion> questions = regionTemplate.answerSheet().getFillBlankQuestions();
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (SheetQuestion question : questions) {
			for (Rect rect : question.optionRegions()) {
				minX = Math.min(minX, rect.x());
				minY = Math.min(minY, rect.y());
				maxX = Math.max(maxX, rect.x() + rect.width());
				maxY = Math.max(maxY, rect.y() + rect.height());
			}
		}
		if (minX != Integer.MAX_VALUE) {
			return new SubjectiveAnswerRegion("主观题1", 1, 1,
					new Rect(minX, minY, maxX - minX, maxY - minY),
					SubjectiveAnswerRegion.GradingMode.OCR, java.math.BigDecimal.ZERO);
		}
		Rect rect = regionTemplate.fillBlankRegionRect();
		return rect == null ? null : new SubjectiveAnswerRegion("主观题1", 1, 1,
				rect, SubjectiveAnswerRegion.GradingMode.OCR, java.math.BigDecimal.ZERO);
	}

	private Rect expandSingleFillRegion(Rect region, Rect choiceRegion, int fillQuestionCount) {
		if (region == null || fillQuestionCount != 1 || fillBlankCount() <= 1) {
			return region;
		}
		int expandedHeight = Math.max(region.height(), region.height() * fillBlankCount() / 4);
		int expandedY = Math.max(0, region.y() + region.height() - expandedHeight);
		if (choiceRegion != null) {
			expandedY = Math.max(expandedY, choiceRegion.y() + choiceRegion.height());
		}
		return new Rect(region.x(), expandedY, region.width(), region.y() + region.height() - expandedY);
	}

	private record CroppedRegion(Rect rect, int baseWidth, int baseHeight) {
	}

	private String defaultSubjectiveText() {
		StringBuilder builder = new StringBuilder();
		int startNumber = template.answerSheet().getChoiceQuestions().size() + 1;
		for (int i = 0; i < fillBlankCount(); i++) {
			if (!builder.isEmpty()) {
				builder.append(System.lineSeparator());
			}
			builder.append(startNumber + i).append('\t');
		}
		return builder.toString();
	}

	private String normalizedSubjectiveText(String ocrText) {
		return displaySubjectiveText(String.join(System.lineSeparator(), subjectiveAnswersFromText(ocrText, false)));
	}

	private String displaySubjectiveText(String storedText) {
		String[] answers = subjectiveAnswersFromText(storedText, true);
		int startNumber = template.answerSheet().getChoiceQuestions().size() + 1;
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < answers.length; i++) {
			if (!builder.isEmpty()) {
				builder.append(System.lineSeparator());
			}
			builder.append(startNumber + i).append('\t');
			String answer = answers[i];
			if (answer != null && !"Z".equalsIgnoreCase(answer.trim())) {
				builder.append(answer);
			}
		}
		return builder.toString();
	}

	private String storedSubjectiveText(String displayText) {
		return String.join(System.lineSeparator(), subjectiveAnswersFromText(displayText, true));
	}

	private String[] subjectiveAnswersFromText(String text, boolean allowSequentialAnswers) {
		int count = fillBlankCount();
		int startNumber = template.answerSheet().getChoiceQuestions().size() + 1;
		String[] answers = new String[Math.max(0, count)];
		int currentIndex = -1;
		boolean started = false;
		int sequentialIndex = 0;
		if (text != null) {
			for (String rawLine : text.split("\\R", -1)) {
				String line = rawLine.trim();
				if (line.isEmpty()) {
					if (allowSequentialAnswers && !started && sequentialIndex < answers.length) {
						answers[sequentialIndex++] = "Z";
					}
					continue;
				}
				Matcher matcher = OCR_NUMBER_LINE.matcher(line);
				if (matcher.matches()) {
					started = true;
					int number = Integer.parseInt(matcher.group(1));
					currentIndex = number - startNumber;
					if (currentIndex >= 0 && currentIndex < answers.length) {
						answers[currentIndex] = appendAnswer(answers[currentIndex], matcher.group(2).trim());
					}
				} else if (started && currentIndex >= 0 && currentIndex < answers.length) {
					answers[currentIndex] = appendAnswer(answers[currentIndex], line);
				} else if (allowSequentialAnswers && sequentialIndex < answers.length && !looksLikeTitle(line)) {
					answers[sequentialIndex++] = line;
				}
			}
		}
		for (int i = 0; i < answers.length; i++) {
			answers[i] = answers[i] == null || answers[i].isBlank() ? "Z" : answers[i].trim();
		}
		return answers;
	}

	private boolean looksLikeTitle(String line) {
		return line.contains("填空") || line.contains("主观") || line.contains("答案");
	}

	private String appendAnswer(String current, String addition) {
		if (addition == null || addition.isBlank()) {
			return current == null ? "" : current;
		}
		return current == null || current.isBlank() ? addition : current + addition;
	}

	private int fillBlankCount() {
		return machineSubjectiveCount(project, template);
	}

	private static int machineSubjectiveCount(GradingProject project, SheetTemplate template) {
		if (project == null) {
			return 0;
		}
		if (project.machineSubjectiveCount() > 0) {
			return project.machineSubjectiveCount();
		}
		if (template == null) {
			return 0;
		}
		int regionCount = template.subjectiveRegions().stream()
				.filter(SubjectiveOcrReviewPanel::isOcrRegion)
				.mapToInt(region -> region.endQuestion() - region.startQuestion() + 1)
				.sum();
		if (regionCount > 0) {
			return regionCount;
		}
		if (project.standardAnswers() != null) {
			int standardCount = project.standardAnswers().length - template.answerSheet().getChoiceQuestions().size();
			if (standardCount > 0) {
				return standardCount;
			}
		}
		return template.answerSheet().getFillBlankQuestions().size();
	}

	private static boolean hasOcrRegion(SheetTemplate template) {
		if (template == null) {
			return false;
		}
		if (template.subjectiveRegions().stream().anyMatch(SubjectiveOcrReviewPanel::isOcrRegion)) {
			return true;
		}
		return !template.answerSheet().getFillBlankQuestions().isEmpty();
	}

	private static boolean isOcrRegion(SubjectiveAnswerRegion region) {
		return region != null
			   && (region.mode() == SubjectiveAnswerRegion.GradingMode.OCR
				   || region.mode() == SubjectiveAnswerRegion.GradingMode.MIXED);
	}

	@Override
	public void saveProjectState() {
		saveCurrent();
	}

	private void saveCurrent() {
		if (project.answerFiles() == null || project.answerFiles().isEmpty()) {
			return;
		}
		project.putSubjectiveAnswer(project.answerFiles().get(index), storedSubjectiveText(textPane.getText()));
		project.setSubjectiveIndex(index);
		project.write();
	}

	private void backToChoiceReview() {
		saveCurrent();
		if (project.answerFiles() != null && !project.answerFiles().isEmpty()) {
			project.setIndex(Math.max(0, Math.min(index, project.answerFiles().size() - 1)));
			project.write();
		}
		MainWindow.INSTANCE.showProjectReview(project);
	}

	private void previous() {
		saveCurrent();
		if (index > 0) {
			index--;
			loadCurrent();
		}
	}

	private void next(ActionEvent event) {
		saveCurrent();
		if (isCtrlDown(event)) {
			if (project.allSubjectiveAnswersSaved()) {
				MainWindow.INSTANCE.showAfterChoiceReview(project);
			} else {
				QROpinionDialog.messageTellShow(MainWindow.INSTANCE, "还有答卷未完成填空题校对，不能直接进入后续。");
			}
			return;
		}
		if (project.answerFiles() != null && index < project.answerFiles().size() - 1) {
			index++;
			loadCurrent();
		}
	}

	private boolean isCtrlDown(ActionEvent event) {
		return event != null && (event.getModifiers() & (InputEvent.CTRL_DOWN_MASK | InputEvent.CTRL_MASK)) != 0;
	}

	private void jump() {
		if (project.answerFiles() == null || project.answerFiles().isEmpty()) {
			return;
		}
		QRValueInputDialog input = new QRValueInputDialog(MainWindow.INSTANCE, "1-" + project.answerFiles().size(), "请输入要跳转到的进度：");
		input.setVisible(true);
		String answer = input.getAnswer();
		if (answer == null) {
			return;
		}
		try {
			int target = Integer.parseInt(answer.trim());
			if (target < 1 || target > project.answerFiles().size()) {
				throw new NumberFormatException();
			}
			saveCurrent();
			index = target - 1;
			loadCurrent();
		} catch (NumberFormatException ex) {
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, "请输入有效进度。");
		}
	}

	private void finish() {
		saveCurrent();
		QRSwing.registerGlobalActionRemove(QRStringUtils.getKeyStroke("ctrl + Enter"), true);
		MainWindow.INSTANCE.showAfterChoiceReview(project);
	}

	private void recognizeWithOcr() {
		if (currentSubjectiveImage == null) {
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, "当前没有可识别的主观题图片。");
			return;
		}
		startOcrTask(false);
	}

	private void autoRecognizeCurrent() {
		if (currentSubjectiveImage == null || !hasConfiguredOcrKey()) {
			return;
		}
		startOcrTask(true);
	}

	private void startOcrTask(boolean automatic) {
		int serial = ++ocrSerial;
		if (ocrWorker != null && !ocrWorker.isDone()) {
			ocrWorker.cancel(true);
		}
		int answerIndex = index;
		BufferedImage image = currentSubjectiveImage;
		setCursorWait();
		QRTaskOptions options = new QRTaskOptions()
				.onSuccess((OcrTaskResult result) -> applyOcrResult(serial, result))
				.onError(error -> handleOcrError(serial, automatic, error))
				.onCancelled(() -> {
					if (serial == ocrSerial) {
						setCursorDefault();
					}
				});
		ocrWorker = QRTaskRunner.run(options, context -> {
			context.message(automatic ? "正在自动 OCR 识别..." : "正在 OCR 识别...");
			OcrResult result = recognizeBySelectedProvider(image);
			String displayText = normalizedSubjectiveText(result.text().isBlank() ? result.rawResponse() : result.text());
			return new OcrTaskResult(answerIndex, result, displayText, automatic);
		});
	}

	private void applyOcrResult(int serial, OcrTaskResult taskResult) {
		if (serial != ocrSerial || taskResult.answerIndex() != index) {
			return;
		}
		setCursorDefault();
		logOcrResult(taskResult.result());
		textPane.setText(taskResult.displayText());
		saveCurrent();
		String suffix = taskResult.automatic() ? " OCR 自动识别完成。" : " OCR 识别完成。";
		QRSmallTipShow.display(MainWindow.INSTANCE, ocrProviderName(taskResult.result().provider()) + suffix);
	}

	private void handleOcrError(int serial, boolean automatic, Throwable error) {
		if (serial != ocrSerial) {
			return;
		}
		setCursorDefault();
		if (automatic) {
			logger.warning("Auto OCR failed: " + error.getMessage());
		} else {
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, "OCR 识别失败：\n" + error.getMessage());
		}
	}

	private boolean hasConfiguredOcrKey() {
		if ("google".equals(selectedOcrProvider())) {
			return !plainConfig(Keys.GOOGLE_OCR_API_KEY).isBlank();
		}
		return !plainConfig(Keys.BAIDU_OCR_API_KEY).isBlank()
			   && !plainConfig(Keys.BAIDU_OCR_SECRET_KEY).isBlank();
	}

	private OcrResult recognizeBySelectedProvider(BufferedImage image) throws Exception {
		String provider = selectedOcrProvider();
		if ("google".equals(provider)) {
			String apiKey = plainConfig(Keys.GOOGLE_OCR_API_KEY);
			if (apiKey.isBlank()) {
				throw new IllegalStateException("请先在 OCR 设置中填写 Google Vision API Key。");
			}
			GoogleVisionHandwritingOcr.Result result = GoogleVisionHandwritingOcr.recognizeJapaneseDocument(image, apiKey);
			return new OcrResult(provider, result.rawResponse(), result.lines(), result.text());
		}
		String apiKey = plainConfig(Keys.BAIDU_OCR_API_KEY);
		String secretKey = plainConfig(Keys.BAIDU_OCR_SECRET_KEY);
		if (apiKey.isBlank() || secretKey.isBlank()) {
			throw new IllegalStateException("请先在 OCR 设置中填写百度 OCR API Key 和 Secret Key。");
		}
		BaiduHandwritingOcr.Result result = BaiduHandwritingOcr.recognizeJapaneseHandwriting(image, apiKey, secretKey);
		return new OcrResult("baidu", result.rawResponse(), result.words(), result.text());
	}

	private String selectedOcrProvider() {
		String provider = Keys.strValue(Keys.OCR_PROVIDER);
		return "google".equalsIgnoreCase(provider) ? "google" : "baidu";
	}

	private String ocrProviderName(String provider) {
		return "google".equals(provider) ? "Google Vision" : "百度";
	}

	private void logOcrResult(OcrResult result) {
		logger.info("===== " + ocrProviderName(result.provider()) + " OCR raw response =====");
		logger.info(result.rawResponse());
		// 只要输出 Response 就行
	}

	private record OcrResult(String provider, String rawResponse, List<String> items, String text) {
	}

	private record SubjectiveImageLoadResult(int answerIndex, File answerFile, BufferedImage image, String savedText,
											 boolean autoOcr) {
	}

	private record OcrTaskResult(int answerIndex, OcrResult result, String displayText, boolean automatic) {
	}

	private void showOcrKeyDialog() {
		new OcrKeyDialog().setVisible(true);
	}

	private static String plainConfig(String key) {
		String value = Keys.strValue(key);
		if (value == null || value.isBlank()) {
			return "";
		}
		if (!value.startsWith(CIPHER_PREFIX)) {
			return value;
		}
		try {
			return QRCodePack.decrypt(value.substring(CIPHER_PREFIX.length()), CIPHER_KEY);
		} catch (Exception ex) {
			return "";
		}
	}

	private static String encryptedConfig(String plainText) {
		String value = plainText == null ? "" : plainText.trim();
		return value.isEmpty() ? "" : CIPHER_PREFIX + QRCodePack.encrypt(value, CIPHER_KEY);
	}

	private static final class OcrKeyDialog extends QRDialog {
		private final QRComboBox providerBox = new QRComboBox("Baidu OCR", "Google Vision");
		private final QRTextField apiKeyField = new QRTextField(plainConfig(Keys.BAIDU_OCR_API_KEY));
		private final QRTextField secretKeyField = new QRTextField(plainConfig(Keys.BAIDU_OCR_SECRET_KEY));
		private final QRTextField googleApiKeyField = new QRTextField(plainConfig(Keys.GOOGLE_OCR_API_KEY));

		private OcrKeyDialog() {
			super(MainWindow.INSTANCE);
			setTitle("OCR Settings");
			setSize(660, 320);
			mainPanel.setLayout(new BorderLayout(8, 8));

			providerBox.setSelectedItem("google".equals(Keys.strValue(Keys.OCR_PROVIDER)) ? "Google Vision" : "Baidu OCR");
			QRPanel fields = new QRPanel(false, new GridLayout(4, 1, 0, 8));
			fields.add(fieldRow("接口选择:", providerBox));
			fields.add(fieldRow("Baidu API Key:", apiKeyField));
			fields.add(fieldRow("Baidu Secret:", secretKeyField));
			fields.add(fieldRow("Google Key:", googleApiKeyField));
			mainPanel.add(fields, BorderLayout.CENTER);

			QRPanel bottom = new QRPanel(false, new FlowLayout(FlowLayout.RIGHT, 8, 0));
			QRRoundButton saveButton = new QRRoundButton("保存");
			saveButton.setPreferredSize(90, 30);
			saveButton.addClickAction(event -> save());
			bottom.add(saveButton);
			mainPanel.add(bottom, BorderLayout.SOUTH);
			setLocationRelativeTo(MainWindow.INSTANCE);
		}

		private QRPanel fieldRow(String label, Component field) {
			QRPanel row = new QRPanel(false, new BorderLayout(8, 0));
			QRLabel qrLabel = new QRLabel(label);
			qrLabel.setPreferredSize(new Dimension(125, 30));
			field.setPreferredSize(new Dimension(430, 30));
			row.add(qrLabel, BorderLayout.WEST);
			row.add(field, BorderLayout.CENTER);
			return row;
		}

		private void save() {
			String provider = "Google Vision".equals(providerBox.getSelectedItem()) ? "google" : "baidu";
			QRSwing.setGlobalSetting(Keys.OCR_PROVIDER, provider);
			QRSwing.setGlobalSetting(Keys.BAIDU_OCR_API_KEY, encryptedConfig(apiKeyField.getText()));
			QRSwing.setGlobalSetting(Keys.BAIDU_OCR_SECRET_KEY, encryptedConfig(secretKeyField.getText()));
			QRSwing.setGlobalSetting(Keys.GOOGLE_OCR_API_KEY, encryptedConfig(googleApiKeyField.getText()));
			dispose();
		}
	}
}
