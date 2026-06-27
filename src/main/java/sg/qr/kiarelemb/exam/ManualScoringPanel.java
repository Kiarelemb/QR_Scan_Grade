package sg.qr.kiarelemb.exam;

import org.bytedeco.opencv.opencv_core.Rect;
import sg.qr.kiarelemb.MainWindow;
import sg.qr.kiarelemb.component.ProjectStateSaver;
import sg.qr.kiarelemb.data.Utils;
import sg.qr.kiarelemb.exam.model.GradingProject;
import sg.qr.kiarelemb.exam.model.SheetTemplate;
import sg.qr.kiarelemb.exam.model.SubjectiveAnswerRegion;
import swing.qr.kiarelemb.basic.QRLabel;
import swing.qr.kiarelemb.basic.QRPanel;
import swing.qr.kiarelemb.basic.QRRoundButton;
import swing.qr.kiarelemb.basic.QRTextField;
import swing.qr.kiarelemb.inter.QRActionRegister;
import swing.qr.kiarelemb.task.QRTaskOptions;
import swing.qr.kiarelemb.task.QRTaskRunner;
import swing.qr.kiarelemb.task.QRTaskWorker;
import swing.qr.kiarelemb.theme.QRColorsAndFonts;
import swing.qr.kiarelemb.utils.PicturePanel;
import swing.qr.kiarelemb.window.enhance.QROpinionDialog;
import swing.qr.kiarelemb.window.utils.QRValueInputDialog;

import javax.imageio.ImageIO;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class ManualScoringPanel extends QRPanel implements ProjectStateSaver {
	private final GradingProject project;
	private final SheetTemplate template;
	private final List<ManualReviewItem> items;
	private final PicturePanel picturePanel = new PicturePanel();
	private final QRLabel titleLabel = new QRLabel("人工判分");
	private final QRLabel progressLabel = new QRLabel();
	private final QRLabel questionLabel = new QRLabel();
	private final QRTextField scoreField = new QRTextField(QRTextField.TYPE.NUMBERS_AND_DECIMAL);
	private QRTaskWorker<ManualImageLoadResult> imageLoadWorker;
	private int imageLoadSerial;
	private int index;

	public ManualScoringPanel(GradingProject project, SheetTemplate template) {
		this.project = project;
		this.template = template;
		this.items = buildItems(project, template);
		this.index = Math.min(project.manualReviewIndex(), Math.max(0, items.size() - 1));
		initView();
		loadCurrent();
	}

	public static boolean hasManualQuestions(GradingProject project, SheetTemplate template) {
		return !manualRegions(project, template).isEmpty();
	}

	public static boolean allManualScoresSaved(GradingProject project, SheetTemplate template) {
		if (project == null || project.answerFiles() == null || project.answerFiles().isEmpty()) {
			return true;
		}
		List<SubjectiveAnswerRegion> regions = manualRegions(project, template);
		if (regions.isEmpty()) {
			return true;
		}
		for (File answerFile : project.answerFiles()) {
			GradingProject.ReviewedAnswer reviewedAnswer = project.reviewedAnswerFor(answerFile);
			if (reviewedAnswer == null || reviewedAnswer.examineeId() == null || reviewedAnswer.examineeId().isBlank()) {
				return false;
			}
			Map<String, String> savedScores = project.manualScoresFor(reviewedAnswer.examineeId());
			for (SubjectiveAnswerRegion region : regions) {
				if (savedScore(savedScores, region).isBlank()) {
					return false;
				}
			}
		}
		return true;
	}

	private void initView() {
		setLayout(new BorderLayout(0, 6));
		add(buildTopPanel(), BorderLayout.NORTH);
		picturePanel.setZoomRange(0.25, 5.0);
		add(picturePanel, BorderLayout.CENTER);
		add(buildScorePanel(), BorderLayout.SOUTH);
	}

	private Component buildTopPanel() {
		QRPanel panel = new QRPanel(false, new BorderLayout(8, 0));
		panel.setBorder(new LineBorder(QRColorsAndFonts.FRAME_COLOR_BACK, 10));
		titleLabel.setFont(QRColorsAndFonts.createFont(18));
		panel.add(titleLabel, BorderLayout.WEST);

		QRPanel buttons = new QRPanel(false, new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttons.add(button("跳转", event -> jump()));
		buttons.add(button("返回", event -> backToSubjectiveReview()));
		buttons.add(button("上一题", event -> previous()));
		buttons.add(button("下一题", event -> next(event)));
		buttons.add(button("进入算分", event -> finish()));
		panel.add(buttons, BorderLayout.EAST);

		QRPanel wrapper = new QRPanel(false, new BorderLayout());
		wrapper.add(panel, BorderLayout.NORTH);
		progressLabel.setTextCenter();
		wrapper.add(progressLabel, BorderLayout.SOUTH);
		return wrapper;
	}

	private Component buildScorePanel() {
		QRPanel panel = new QRPanel(false, new BorderLayout(10, 0));
		panel.setBorder(new LineBorder(QRColorsAndFonts.FRAME_COLOR_BACK, 10));
		questionLabel.setPreferredSize(new Dimension(420, 34));
		panel.add(questionLabel, BorderLayout.WEST);

		QRPanel inputPanel = new QRPanel(false, new FlowLayout(FlowLayout.LEFT, 8, 0));
		inputPanel.add(new QRLabel("得分"));
		scoreField.setPreferredSize(new Dimension(120, 32));
		scoreField.addActionListener(event -> next(null));
		inputPanel.add(scoreField);
		panel.add(inputPanel, BorderLayout.CENTER);
		return panel;
	}

	private QRRoundButton button(String text, QRActionRegister<ActionEvent> ar) {
		QRRoundButton button = new QRRoundButton(text);
		button.setPreferredSize(new Dimension(100, 30));
		button.addClickAction(ar);
		return button;
	}

	private void loadCurrent() {
		if (items.isEmpty()) {
			progressLabel.setText("没有需要人工判分的主观题。");
			BufferedImage blank = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
			picturePanel.setImage(blank, new Dimension(1, 1));
			scoreField.setText("");
			return;
		}
		index = Math.max(0, Math.min(index, items.size() - 1));
		project.setManualReviewIndex(index);
		project.write();
		ManualReviewItem item = items.get(index);
		String name = project.studentNamesByExamId().getOrDefault(item.examineeId(), "");
		progressLabel.setText("当前：" + item.answerFile().getName() + "，准考证号：" + item.examineeId()
		                      + (name.isBlank() ? "" : "，姓名：" + name)
		                      + "，进度：" + (index + 1) + " / " + items.size());
		questionLabel.setText(item.region().name() + "（题号 " + item.region().startQuestion()
		                      + "-" + item.region().endQuestion() + maxScoreText(item.region().maxScore()) + "）");
		scoreField.setText(project.manualScoresFor(item.examineeId()).getOrDefault(item.region().name(), ""));
		startImageLoadTask(item, index);
	}

	private void startImageLoadTask(ManualReviewItem item, int itemIndex) {
		int serial = ++imageLoadSerial;
		if (imageLoadWorker != null && !imageLoadWorker.isDone()) {
			imageLoadWorker.cancel(true);
		}
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		QRTaskOptions options = new QRTaskOptions()
				.onSuccess((ManualImageLoadResult result) -> applyImageLoadResult(serial, result))
				.onError(error -> handleImageLoadError(serial, error))
				.onCancelled(() -> {
					if (serial == imageLoadSerial) {
						setCursor(Cursor.getDefaultCursor());
					}
				});
		imageLoadWorker = QRTaskRunner.run(options, context -> {
			context.message("正在读取主观题答卷截图...");
			BufferedImage image = ImageIO.read(item.imageFile());
			context.checkCancelled();
			if (image == null) {
				throw new IOException("不支持的图片格式或图片已损坏。");
			}
			BufferedImage crop = crop(image, item.region());
			return new ManualImageLoadResult(itemIndex, crop);
		});
	}

	private void applyImageLoadResult(int serial, ManualImageLoadResult result) {
		if (serial != imageLoadSerial || result.itemIndex() != index) {
			return;
		}
		setCursor(Cursor.getDefaultCursor());
		picturePanel.setImage(result.image(), pictureDisplaySize(result.image()));
		picturePanel.resetView();
		scoreField.requestFocusInWindow();
	}

	private void handleImageLoadError(int serial, Throwable error) {
		if (serial != imageLoadSerial) {
			return;
		}
		setCursor(Cursor.getDefaultCursor());
		QROpinionDialog.messageErrShow(MainWindow.INSTANCE, "读取主观题答卷截图失败：\n" + error.getMessage());
	}

	private String maxScoreText(BigDecimal maxScore) {
		return maxScore == null || maxScore.compareTo(BigDecimal.ZERO) <= 0 ? "" : "，满分 " + Utils.formatScore(maxScore);
	}

	private BufferedImage crop(BufferedImage image, SubjectiveAnswerRegion region) {
		if (region == null || region.region() == null) {
			return image;
		}
		Rect rect = scaleRegion(region.region(), image);
		int padding = Math.max(12, Math.min(image.getWidth(), image.getHeight()) / 150);
		int x = Math.max(0, Math.min(rect.x() - padding, image.getWidth() - 1));
		int y = Math.max(0, Math.min(rect.y() - padding, image.getHeight() - 1));
		int right = Math.max(x + 1, Math.min(rect.x() + rect.width() + padding, image.getWidth()));
		int bottom = Math.max(y + 1, Math.min(rect.y() + rect.height() + padding, image.getHeight()));
		return image.getSubimage(x, y, right - x, bottom - y);
	}

	private Rect scaleRegion(Rect rect, BufferedImage image) {
		double sx = (double) image.getWidth() / Math.max(1, template.answerSheet().getImageWidth());
		double sy = (double) image.getHeight() / Math.max(1, template.answerSheet().getImageHeight());
		return new Rect((int) Math.round(rect.x() * sx),
				(int) Math.round(rect.y() * sy),
				Math.max(1, (int) Math.round(rect.width() * sx)),
				Math.max(1, (int) Math.round(rect.height() * sy)));
	}

	private Dimension pictureDisplaySize(BufferedImage image) {
		int imageW = Math.max(1, image.getWidth());
		int imageH = Math.max(1, image.getHeight());
		int windowW = Math.max(900, MainWindow.INSTANCE.getWidth());
		int windowH = Math.max(700, MainWindow.INSTANCE.getHeight());
		int maxW = Math.max(720, windowW - 140);
		int maxH = Math.max(420, windowH - 190);
		double scale = Math.min(1.0, Math.min((double) maxW / imageW, (double) maxH / imageH));
		return new Dimension(
				Math.max(1, (int) Math.round(imageW * scale)),
				Math.max(1, (int) Math.round(imageH * scale)));
	}

	private boolean saveCurrent() {
		if (items.isEmpty()) {
			return true;
		}
		ManualReviewItem item = items.get(index);
		String text = scoreField.getText() == null ? "" : scoreField.getText().trim();
		if (text.isBlank()) {
			project.putManualScore(item.examineeId(), item.region().name(), "");
			project.write();
			return true;
		}
		BigDecimal score;
		try {
			score = new BigDecimal(text).setScale(2, RoundingMode.HALF_UP);
		} catch (NumberFormatException ex) {
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, item.region().name() + " 分数不是有效数字。");
			return false;
		}
		BigDecimal maxScore = item.region().maxScore();
		if (score.compareTo(BigDecimal.ZERO) < 0
		    || (maxScore != null && maxScore.compareTo(BigDecimal.ZERO) > 0 && score.compareTo(maxScore) > 0)) {
			String range = maxScore != null && maxScore.compareTo(BigDecimal.ZERO) > 0 ? "0-" + Utils.formatScore(maxScore) : "大于等于 0";
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, item.region().name() + " 分数应在 " + range + " 之间。");
			return false;
		}
		project.putManualScore(item.examineeId(), item.region().name(), score.stripTrailingZeros().toPlainString());
		project.setManualReviewIndex(index);
		project.write();
		return true;
	}

	private void previous() {
		if (saveCurrent() && index > 0) {
			index--;
			loadCurrent();
		}
	}

	private void next(ActionEvent event) {
		if (!saveCurrent()) {
			return;
		}
		if (isCtrlDown(event)) {
			if (ManualScoringPanel.allManualScoresSaved(project, template)) {
				project.setManualReviewIndex(index);
				project.write();
				MainWindow.INSTANCE.showProjectEnd(project);
			} else {
				QROpinionDialog.messageTellShow(MainWindow.INSTANCE, "还有主观题未完成人工评分，不能直接进入算分。");
			}
			return;
		}
		if (index < items.size() - 1) {
			index++;
			loadCurrent();
		} else {
			QROpinionDialog.messageTellShow(MainWindow.INSTANCE, "人工判分已到最后一题。");
		}
	}

	private boolean isCtrlDown(ActionEvent event) {
		return event != null && (event.getModifiers() & (InputEvent.CTRL_DOWN_MASK | InputEvent.CTRL_MASK)) != 0;
	}

	private void jump() {
		if (items.isEmpty()) {
			return;
		}
		QRValueInputDialog input = new QRValueInputDialog(MainWindow.INSTANCE, "1-" + items.size(), "请输入要跳转到的进度：");
		input.textField().setType(QRTextField.TYPE.NUMBERS);
		input.setVisible(true);
		if (!input.isApproved()) return;
		String answer = input.getAnswer();
		int target = Integer.parseInt(answer.trim());
		if (target < 1 || target > items.size()) {
			throw new NumberFormatException();
		}
		if (saveCurrent()) {
			index = target - 1;
			loadCurrent();
		}
	}

	private void backToSubjectiveReview() {
		saveCurrent();
		if (SubjectiveOcrReviewPanel.hasSubjectiveQuestions(project, template)) {
			MainWindow.INSTANCE.setCenterComponent(new SubjectiveOcrReviewPanel(project, template));
		} else {
			MainWindow.INSTANCE.showProjectReview(project);
		}
	}

	private void finish() {
		if (saveCurrent()) {
			project.setManualReviewIndex(index);
			project.write();
			MainWindow.INSTANCE.showProjectEnd(project);
		}
	}

	@Override
	public void saveProjectState() {
		saveCurrent();
	}

	private static List<ManualReviewItem> buildItems(GradingProject project, SheetTemplate template) {
		if (project == null || project.answerFiles() == null) {
			return List.of();
		}
		List<SubjectiveAnswerRegion> regions = manualRegions(project, template);
		if (regions.isEmpty()) {
			return List.of();
		}
		List<ManualReviewItem> items = new ArrayList<>();
		for (SubjectiveAnswerRegion region : regions) {
			for (File answerFile : project.answerFiles()) {
				GradingProject.ReviewedAnswer reviewedAnswer = project.reviewedAnswerFor(answerFile);
				if (reviewedAnswer == null || reviewedAnswer.examineeId() == null || reviewedAnswer.examineeId().isBlank()) {
					continue;
				}
				File imageFile = manualImageFile(project, answerFile, region);
				items.add(new ManualReviewItem(answerFile, imageFile, reviewedAnswer.examineeId(), region));
			}
		}
		return items;
	}

	private static File manualImageFile(GradingProject project, File answerFile, SubjectiveAnswerRegion region) {
		if (region == null || region.pageIndex() <= 0) {
			return answerFile;
		}
		File backFile = project.answerBackFileFor(answerFile);
		return backFile == null ? answerFile : backFile;
	}

	private static List<SubjectiveAnswerRegion> manualRegions(GradingProject project, SheetTemplate template) {
		if (template == null || template.subjectiveRegions() == null) {
			return List.of();
		}
		boolean allSubjectiveManual = project != null && project.machineSubjectiveCount() <= 0;
		return template.subjectiveRegions().stream()
				.filter(region -> region.mode() == SubjectiveAnswerRegion.GradingMode.MANUAL
				                  || region.mode() == SubjectiveAnswerRegion.GradingMode.MIXED
				                  || (allSubjectiveManual && region.mode() == SubjectiveAnswerRegion.GradingMode.OCR))
				.toList();
	}

	private static String savedScore(Map<String, String> savedScores, SubjectiveAnswerRegion region) {
		if (savedScores == null || region == null) {
			return "";
		}
		String direct = savedScores.getOrDefault(region.name(), "");
		if (!direct.isBlank()) {
			return direct;
		}
		for (Map.Entry<String, String> entry : savedScores.entrySet()) {
			if (entry.getKey() != null && !entry.getKey().isBlank()
			    && sameManualSection(entry.getKey(), region)
			    && entry.getValue() != null && !entry.getValue().isBlank()) {
				return entry.getValue();
			}
		}
		return "";
	}

	public static String scoreForRule(Map<String, String> savedScores, String ruleName, Collection<Integer> questionNumbers, SheetTemplate template) {
		return scoreForRule(savedScores, ruleName, questionNumbers, null, template);
	}

	public static String scoreForRule(Map<String, String> savedScores, String ruleName, Collection<Integer> questionNumbers,
	                                  GradingProject project, SheetTemplate template) {
		if (savedScores == null || savedScores.isEmpty()) {
			return "";
		}
		String direct = savedScores.getOrDefault(ruleName, "");
		if (!direct.isBlank()) {
			return direct;
		}
		if (questionNumbers == null || questionNumbers.isEmpty()) {
			return "";
		}
		for (SubjectiveAnswerRegion region : manualRegions(project, template)) {
			if (covers(region, questionNumbers)) {
				String score = savedScores.getOrDefault(region.name(), "");
				if (!score.isBlank()) {
					return score;
				}
			}
		}
		return "";
	}

	private static boolean sameManualSection(String sectionName, SubjectiveAnswerRegion region) {
		return sectionName.equals(region.name());
	}

	private static boolean covers(SubjectiveAnswerRegion region, Collection<Integer> questionNumbers) {
		for (Integer questionNumber : questionNumbers) {
			if (questionNumber == null || !region.containsQuestion(questionNumber)) {
				return false;
			}
		}
		return true;
	}

	private record ManualReviewItem(File answerFile, File imageFile, String examineeId, SubjectiveAnswerRegion region) {
	}

	private record ManualImageLoadResult(int itemIndex, BufferedImage image) {
	}
}