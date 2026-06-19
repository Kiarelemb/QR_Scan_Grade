package sg.qr.kiarelemb.grading;

import method.qr.kiarelemb.utils.QRStringUtils;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import sg.qr.kiarelemb.MainWindow;
import sg.qr.kiarelemb.component.AnswerSheetPreviewPanel;
import sg.qr.kiarelemb.grading.model.*;
import sg.qr.kiarelemb.grading.pipeline.*;
import swing.qr.kiarelemb.QRSwing;
import swing.qr.kiarelemb.basic.QRLabel;
import swing.qr.kiarelemb.basic.QRPanel;
import swing.qr.kiarelemb.basic.QRRoundButton;
import swing.qr.kiarelemb.basic.QRTable;
import swing.qr.kiarelemb.inter.QRActionRegister;
import swing.qr.kiarelemb.listener.QRDocumentListener;
import swing.qr.kiarelemb.theme.QRColorsAndFonts;
import swing.qr.kiarelemb.utils.QRClearableTextField;
import swing.qr.kiarelemb.window.enhance.QROpinionDialog;
import swing.qr.kiarelemb.window.utils.QRValueInputDialog;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProjectReviewPanel extends QRPanel {
	private static final Font TEXT_FONT = QRColorsAndFonts.createFont(16);
	private static final Color EXAM_COLOR = new Color(67, 120, 210);
	private static final Color CHOICE_COLOR = new Color(45, 150, 90);
	private static final Color FILL_COLOR = new Color(190, 130, 40);
	private static final String BLANK_ANSWER_PLACEHOLDER = "Z";

	private final Project project;
	private Template template;
	private AnswerSheet answerSheet;
	private AnswerSheet currentAnswerSheet;
	private final AnswerComparator comparator = new AnswerComparator(new ChoiceReader());
	private final QRLabel titleLabel = new QRLabel("批阅项目");
	private final QRLabel progressLabel = new QRLabel();
	private final QRClearableTextField examIdField = new QRClearableTextField();
	private final DefaultTableModel tableModel = new DefaultTableModel(new String[]{"题号", "识别/作答", "正确答案", "状态"}, 0) {
		@Override
		public boolean isCellEditable(int row, int column) {
			return column == 1;
		}
	};
	private final QRTable resultTable = new QRTable(tableModel);
	private final ReviewPreviewPanel previewPanel = new ReviewPreviewPanel(
			"暂无答卷图片", new Dimension(820, 700), new Dimension(500, 500),
			EXAM_COLOR, CHOICE_COLOR, FILL_COLOR);
	private GradingResult currentResult;
	private boolean syncingAnswerText;
	private boolean syncingAnswerTable;

	private QRRoundButton previousButton;
	private QRRoundButton nextButton;
	private QRRoundButton exitButton;
	private QRRoundButton alignAnswerButton;
	private QRRoundButton invertColorButton;
	private QRRoundButton jumpButton;

	public ProjectReviewPanel(Project project) {
		this.project = project;
		initView();
		loadProject();
		loadCurrentAnswer();
	}

	private void initView() {
		setLayout(new BorderLayout());

		QRPanel topPanel = new QRPanel(false, new BorderLayout(10, 0));
		topPanel.setBorder(new LineBorder(QRColorsAndFonts.FRAME_COLOR_BACK, 10));
		titleLabel.setFont(QRColorsAndFonts.createFont(18));
		topPanel.add(titleLabel, BorderLayout.WEST);

		QRPanel actionPanel = new QRPanel(false, new FlowLayout(FlowLayout.RIGHT, 8, 0));
		alignAnswerButton = createButton("对齐答案", this::toggleAnswerAlignment);
		invertColorButton = createButton("颜色反转", this::togglePreviewColor);
		jumpButton = createButton("跳转", this::jumpAnswer);
		previousButton = createButton("上一张", this::previousAnswer);
		nextButton = createButton("下一张", this::nextAnswer);
		exitButton = createButton("退出项目", event -> {
			if (!saveCurrentReview()) {
				return;
			}
			removeShortcuts();
			MainWindow.INSTANCE.showStartPanel();
		});
		alignAnswerButton.setToolTipText("切换识别结果的显示位置");
		invertColorButton.setToolTipText("切换答卷预览的黑白显示");
		jumpButton.setToolTipText("跳转到指定进度的答卷");
		previousButton.setToolTipText("快捷键：[");
		nextButton.setToolTipText("快捷键：]");
		exitButton.setToolTipText("退出的同时也将保存项目");

		actionPanel.add(alignAnswerButton);
		actionPanel.add(invertColorButton);
		actionPanel.add(jumpButton);
		actionPanel.add(previousButton);
		actionPanel.add(nextButton);
		actionPanel.add(exitButton);
		topPanel.add(actionPanel, BorderLayout.EAST);
		add(topPanel, BorderLayout.NORTH);

		QRPanel bottomPanel = new QRPanel(false, new BorderLayout(0, 8));
		QRLabel hintLabel = new QRLabel("下一张：]；上一张：[   ");
		hintLabel.setTextCenter();
		bottomPanel.add(hintLabel, BorderLayout.EAST);
		bottomPanel.add(progressLabel, BorderLayout.CENTER);
		add(bottomPanel, BorderLayout.SOUTH);
		progressLabel.setTextCenter();

		add(previewPanel, BorderLayout.CENTER);
		add(buildRightPanel(), BorderLayout.EAST);

		ChoiceCheckTextPane.CHOICE_CHECK_TEXT_PANE.setReviewAnswerChangeListener(this::syncTableFromCheckText);
		registerShortcuts();
	}

	private QRPanel buildRightPanel() {
		QRPanel rightPanel = new QRPanel(false, new BorderLayout(0, 8));
		rightPanel.setPreferredSize(new Dimension(360, 100));
		rightPanel.setBorder(new LineBorder(QRColorsAndFonts.FRAME_COLOR_BACK, 10));

		QRPanel examPanel = new QRPanel(false, new BorderLayout(8, 0));
		QRLabel examLabel = new QRLabel("准考证号：");
		examLabel.setPreferredSize(new Dimension(90, 30));
		examIdField.setPreferredSize(new Dimension(220, 30));
		examPanel.add(examLabel, BorderLayout.WEST);
		examPanel.add(examIdField, BorderLayout.CENTER);

		QRActionRegister<DocumentEvent> dear = e -> {
			updatePreviewOverlay();
			syncExamIdToCheckText();
			if (!syncingAnswerTable) {
				syncCheckTextFromTable();
			}
		};
//		examIdField.addDocumentListenerAction(QRDocumentListener.TYPE.INSERT, dear);
//		examIdField.addDocumentListenerAction(QRDocumentListener.TYPE.REMOVE, dear);
		examIdField.textField.addDocumentListenerAction(QRDocumentListener.TYPE.CHANGED, dear);

		rightPanel.add(examPanel, BorderLayout.NORTH);

		resultTable.setRowHeight(28);
		resultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		resultTable.setFont(TEXT_FONT);
		resultTable.getTableHeader().setFont(TEXT_FONT);
		resultTable.setDefaultRenderer(Object.class, new ResultCellRenderer());
		tableModel.addTableModelListener(event -> {
			if (event.getType() == TableModelEvent.UPDATE && event.getColumn() == 1) {
				refreshRowStatus(event.getFirstRow());
				if (!syncingAnswerText) {
					syncCheckTextFromTable();
				}
			}
		});
		resultTable.getColumnModel().getColumn(0).setPreferredWidth(45);
		resultTable.getColumnModel().getColumn(1).setPreferredWidth(90);
		resultTable.getColumnModel().getColumn(2).setPreferredWidth(80);
		resultTable.getColumnModel().getColumn(3).setPreferredWidth(80);
		rightPanel.add(resultTable.addScrollPane(), BorderLayout.CENTER);
		return rightPanel;
	}

	private QRRoundButton createButton(String text, java.awt.event.ActionListener action) {
		QRRoundButton button = new QRRoundButton(text);
		button.setPreferredSize(100, 30);
		button.addClickAction(action::actionPerformed);
		return button;
	}

	private void togglePreviewColor(ActionEvent event) {
		boolean inverted = !previewPanel.colorInverted();
		previewPanel.setColorInverted(inverted);
		invertColorButton.setText(inverted ? "恢复颜色" : "颜色反转");
	}

	private void toggleAnswerAlignment(ActionEvent event) {
		boolean aligned = !previewPanel.answerAligned();
		previewPanel.setAnswerAligned(aligned);
		alignAnswerButton.setText(aligned ? "贴近答案" : "对齐答案");
	}

	public void removeShortcuts() {
		QRSwing.registerGlobalActionRemove(QRStringUtils.getKeyStroke(']'), true);
		QRSwing.registerGlobalActionRemove(QRStringUtils.getKeyStroke('['), true);
		QRSwing.registerGlobalActionRemove(QRStringUtils.getKeyStroke(KeyEvent.VK_S), true);
	}

	private void registerShortcuts() {
		QRSwing.registerGlobalAction(']', e -> {
			if (nextButton.isEnabled()) {
				nextButton.click();
			}
		}, true);
		QRSwing.registerGlobalAction('[', e -> {
			if (previousButton.isEnabled()) {
				previousButton.click();
			}
		}, true);
		QRSwing.registerGlobalAction(KeyEvent.VK_S, e -> exitButton.click(), true);
	}

	private void loadProject() {
		try {
			template = TemplateProcessor.load(new File(project.templateFilePath()));
			answerSheet = buildAnswerSheetWithStandardAnswers(template.answerSheet(), project.standardAnswers());
			currentAnswerSheet = answerSheet;
			titleLabel.setText("批阅项目：" + new File(project.projectFilePath()).getName());
		} catch (IOException ex) {
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, "读取模板失败：\n" + ex.getMessage());
		}
	}

	private AnswerSheet buildAnswerSheetWithStandardAnswers(AnswerSheet source, String[] standardAnswers) {
		List<Question> questions = new ArrayList<>();
		int answerIndex = 0;
		for (Question question : source.getQuestions()) {
			String correctAnswer = "";
			if (question.type() == Question.QuestionType.SINGLE_CHOICE || question.type() == Question.QuestionType.FILL_BLANK) {
				correctAnswer = answerIndex < standardAnswers.length ? standardAnswers[answerIndex] : "";
				answerIndex++;
			}
			questions.add(new Question(question.number(), question.type(), copyRects(question.optionRegions()), correctAnswer));
		}
		return new AnswerSheet(source.getName(), source.getImageWidth(), source.getImageHeight(), source.getExamIdDigits(), questions);
	}

	private List<Rect> copyRects(List<Rect> rects) {
		List<Rect> copy = new ArrayList<>();
		for (Rect rect : rects) {
			copy.add(new Rect(rect.x(), rect.y(), rect.width(), rect.height()));
		}
		return copy;
	}

	private void loadCurrentAnswer() {
		if (template == null || answerSheet == null) {
			return;
		}
		if (project.answerFiles() == null || project.answerFiles().isEmpty()) {
			QROpinionDialog.messageTellShow(MainWindow.INSTANCE, "项目中没有答卷文件。");
			return;
		}
		if (project.index() >= project.answerFiles().size()) {
			project.setIndex(project.answerFiles().size());
			project.write();
			removeShortcuts();
			MainWindow.INSTANCE.showAfterChoiceReview(project);
			return;
		}

		File answerFile = project.answerFiles().get(project.index());
		progressLabel.setText("当前：" + answerFile.getName() + "，进度：" + (project.index() + 1) + " / " + project.answerFiles().size());
		refreshNavigationButtons();

		setCursorWait();
		try {
			BufferedImage image = ImageIO.read(answerFile);
			Mat binary = ImagePreprocessor.preprocess(answerFile);
			AnswerSheetCalibrator.CalibrationResult calibration = AnswerSheetCalibrator.calibrate(binary, template, answerSheet);
			currentAnswerSheet = calibration.answerSheet();
			Template currentTemplate = new Template(
					template.name(),
					template.pictureFile(),
					currentAnswerSheet,
					calibration.examRegionRect(),
					calibration.choiceRegionRect(),
					calibration.fillBlankRegionRect(),
					template.defaultScoreRules(),
					template.pageCount());
			previewPanel.setData(image, currentTemplate, false);

			Project.ReviewedAnswer reviewedAnswer = project.reviewedAnswerFor(answerFile);
			if (reviewedAnswer == null) {
				currentResult = comparator.grade(binary, currentAnswerSheet, currentAnswerSheet.getChoiceLabels());
				renderResult(currentResult);
				saveRecognizedBaseline(currentResult);
			} else {
				renderSavedReview(refreshInvalidSavedExamId(answerFile, binary, reviewedAnswer));
			}
		} catch (Exception ex) {
			currentResult = null;
			currentAnswerSheet = answerSheet;
			tableModel.setRowCount(0);
			examIdField.textField.clear();
			ChoiceCheckTextPane.CHOICE_CHECK_TEXT_PANE.clearReviewAnswers();
			previewPanel.setReviewData(currentAnswerSheet, "", Map.of());
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, "读取或识别答卷失败：\n"
																+ answerFile.getAbsolutePath() + "\n" + ex.getMessage());
		} finally {
			setCursorDefault();
		}
	}

	private void renderResult(GradingResult result) {
		examIdField.textField.setText(result.examineeId());
		tableModel.setRowCount(0);
		for (GradingResult.QuestionResult questionResult : result.questionResults()) {
			String status = questionResult.uncertain() ? "存疑" : questionResult.correct() ? "正确" : "错误";
			tableModel.addRow(new Object[]{
					questionResult.questionNumber(),
					questionResult.detectedAnswer(),
					questionResult.expectedAnswer(),
					status
			});
		}
		updatePreviewOverlay();
		syncCheckTextFromTable();
	}

	private void saveRecognizedBaseline(GradingResult result) {
		if (result == null || result.examineeId() == null || result.examineeId().isBlank()) {
			return;
		}
		List<String> answers = new ArrayList<>();
		for (GradingResult.QuestionResult questionResult : result.questionResults()) {
			if (questionResult.type() != Question.QuestionType.SINGLE_CHOICE) {
				continue;
			}
			String answer = questionResult.detectedAnswer() == null ? "" : questionResult.detectedAnswer().trim();
			answers.add(answer.isEmpty() || "未填".equals(answer) ? BLANK_ANSWER_PLACEHOLDER : answer);
		}
		project.putRecognizedAnswer(result.examineeId(), String.join(" ", answers));
		project.write();
	}

	private void applySavedReview(File answerFile) {
		Project.ReviewedAnswer reviewedAnswer = project.reviewedAnswerFor(answerFile);
		if (reviewedAnswer == null) {
			return;
		}
		renderSavedReview(reviewedAnswer);
	}

	private void renderSavedReview(Project.ReviewedAnswer reviewedAnswer) {
		examIdField.textField.setText(reviewedAnswer.examineeId());
		String[] answers = splitSavedAnswers(reviewedAnswer.answers());
		List<GradingResult.QuestionResult> questionResults = new ArrayList<>();
		List<Question> questions = currentAnswerSheet == null ? List.of() : currentAnswerSheet.getChoiceQuestions();
		for (int row = 0; row < questions.size(); row++) {
			Question question = questions.get(row);
			String answer = row < answers.length ? displaySavedAnswer(answers[row]) : "";
			String expected = question.correctAnswer() == null ? "" : question.correctAnswer();
			questionResults.add(new GradingResult.QuestionResult(
					question.number(),
					question.type(),
					expected,
					answer,
					answer.equals(expected),
					false
			));
		}
		currentResult = new GradingResult(reviewedAnswer.examineeId(),
				currentAnswerSheet == null ? "" : currentAnswerSheet.getName(),
				questionResults,
				questionResults.size(),
				(int) questionResults.stream().filter(GradingResult.QuestionResult::correct).count());
		tableModel.setRowCount(0);
		for (GradingResult.QuestionResult questionResult : currentResult.questionResults()) {
			String status = questionResult.correct() ? "正确" : "错误";
			tableModel.addRow(new Object[]{
					questionResult.questionNumber(),
					questionResult.detectedAnswer(),
					questionResult.expectedAnswer(),
					status
			});
		}
		int answerIndex = 0;
		for (int row = 0; row < tableModel.getRowCount(); row++) {
			if (!isChoiceRow(row)) {
				continue;
			}
			String answer = answerIndex < answers.length ? displaySavedAnswer(answers[answerIndex]) : "";
			answerIndex++;
			tableModel.setValueAt(answer, row, 1);
		}
		updatePreviewOverlay();
		syncCheckTextFromTable();
	}

	private Project.ReviewedAnswer refreshInvalidSavedExamId(File answerFile, Mat binary, Project.ReviewedAnswer reviewedAnswer) {
		if (reviewedAnswer == null || savedExamIdUsable(reviewedAnswer.examineeId())) {
			return reviewedAnswer;
		}
		GradingResult recognized = comparator.grade(binary, currentAnswerSheet, currentAnswerSheet.getChoiceLabels());
		if (!savedExamIdUsable(recognized.examineeId())) {
			currentResult = recognized;
			return reviewedAnswer;
		}
		Project.ReviewedAnswer corrected = new Project.ReviewedAnswer(recognized.examineeId(), reviewedAnswer.answers());
		project.putReviewedAnswer(answerFile, corrected.examineeId(), corrected.answers());
		project.write();
		return corrected;
	}

	private boolean savedExamIdUsable(String examineeId) {
		String value = examineeId == null ? "" : examineeId.trim();
		if (value.isEmpty()) {
			return false;
		}
		Map<String, String> names = project.studentNamesByExamId();
		return names == null || names.isEmpty() || names.containsKey(value);
	}

	private String[] splitSavedAnswers(String answers) {
		String value = answers == null ? "" : answers.trim();
		return value.isEmpty() ? new String[0] : value.split("[ \\t\\r\\n]+");
	}

	private String displaySavedAnswer(String answer) {
		return BLANK_ANSWER_PLACEHOLDER.equalsIgnoreCase(answer == null ? "" : answer.trim()) ? "" : answer;
	}

	private void refreshRowStatus(int row) {
		if (row < 0 || row >= tableModel.getRowCount()) {
			return;
		}
		Object detectedValue = tableModel.getValueAt(row, 1);
		Object expectedValue = tableModel.getValueAt(row, 2);
		String detected = displaySavedAnswer(detectedValue == null ? "" : detectedValue.toString().trim());
		String expected = expectedValue == null ? "" : expectedValue.toString().trim();
		String status = detected.equals(expected) ? "正确" : "错误";
		if (!status.equals(tableModel.getValueAt(row, 3))) {
			tableModel.setValueAt(status, row, 3);
		}
		updatePreviewOverlay();
	}

	private void updatePreviewOverlay() {
		Map<Integer, String> detectedAnswers = new HashMap<>();
		for (int row = 0; row < tableModel.getRowCount(); row++) {
			Object numberValue = tableModel.getValueAt(row, 0);
			Object answerValue = tableModel.getValueAt(row, 1);
			if (numberValue instanceof Number number) {
				String answer = answerValue == null ? "" : answerValue.toString().trim();
				detectedAnswers.put(number.intValue(), displaySavedAnswer(answer));
			}
		}
		String examId = examIdField.textField.getText() == null ? "" : examIdField.textField.getText().trim();
		previewPanel.setReviewData(currentAnswerSheet == null ? answerSheet : currentAnswerSheet, examId, detectedAnswers);
	}

	private void syncCheckTextFromTable() {
		if (syncingAnswerTable) {
			return;
		}
		syncingAnswerText = true;
		try {
			List<String> answers = new ArrayList<>();
			for (int row = 0; row < tableModel.getRowCount(); row++) {
				if (!isChoiceRow(row)) {
					continue;
				}
				Object value = tableModel.getValueAt(row, 1);
				String answer = value == null ? "" : value.toString().trim();
				answers.add(answer.isEmpty() ? BLANK_ANSWER_PLACEHOLDER : answer);
			}
			String examId = examIdField.textField.getText() == null ? "" : examIdField.textField.getText().trim();
			ChoiceCheckTextPane.CHOICE_CHECK_TEXT_PANE.setReviewData(examId, answers);
		} finally {
			syncingAnswerText = false;
		}
	}

	private void syncExamIdToCheckText() {
		if (syncingAnswerTable || syncingAnswerText) {
			return;
		}
		syncingAnswerText = true;
		try {
			String examId = examIdField.textField.getText() == null ? "" : examIdField.textField.getText().trim();
			ChoiceCheckTextPane.CHOICE_CHECK_TEXT_PANE.setExamId(examId);
		} finally {
			syncingAnswerText = false;
		}
	}

	private void syncTableFromCheckText(String examId, List<String> answers) {
		if (syncingAnswerText) {
			return;
		}
		syncingAnswerTable = true;
		try {
			String oldExamId = examIdField.textField.getText() == null ? "" : examIdField.textField.getText().trim();
			if (!oldExamId.equals(examId)) {
				examIdField.textField.setText(examId);
			}
			int answerIndex = 0;
			for (int row = 0; row < tableModel.getRowCount(); row++) {
				if (!isChoiceRow(row)) {
					continue;
				}
				String answer = answerIndex < answers.size() ? displaySavedAnswer(answers.get(answerIndex)) : "";
				answerIndex++;
				Object oldValue = tableModel.getValueAt(row, 1);
				String oldAnswer = oldValue == null ? "" : oldValue.toString().trim();
				if (!oldAnswer.equals(answer)) {
					tableModel.setValueAt(answer, row, 1);
				}
			}
		} finally {
			syncingAnswerTable = false;
		}
		updatePreviewOverlay();
	}

	private void nextAnswer(ActionEvent event) {
		if (!saveCurrentReview()) {
			return;
		}
		if (isLastAnswer()) {
			project.setIndex(project.answerFiles().size());
			project.write();
			removeShortcuts();
			MainWindow.INSTANCE.showAfterChoiceReview(project);
			return;
		}
		project.setIndex(Math.min(project.index() + 1, project.answerFiles().size()));
		project.write();
		loadCurrentAnswer();
	}

	private void jumpAnswer(ActionEvent event) {
		if (project.answerFiles() == null || project.answerFiles().isEmpty() || !saveCurrentReview()) {
			return;
		}
		int max = project.answerFiles().size();
		QRValueInputDialog input = new QRValueInputDialog(MainWindow.INSTANCE, "1-" + max, "请输入要跳转到的进度：");
		input.setVisible(true);
		String answer = input.getAnswer();
		if (answer == null) {
			return;
		}
		try {
			int target = Integer.parseInt(answer.trim());
			if (target < 1 || target > max) {
				throw new NumberFormatException();
			}
			project.setIndex(target - 1);
			project.write();
			loadCurrentAnswer();
		} catch (NumberFormatException ex) {
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, "请输入 1 到 " + max + " 之间的数字。");
		}
	}

	private void previousAnswer(ActionEvent event) {
		if (!saveCurrentReview() || project.index() <= 0) {
			return;
		}
		project.setIndex(project.index() - 1);
		project.write();
		loadCurrentAnswer();
	}

	private void refreshNavigationButtons() {
		previousButton.setEnabled(project.index() > 0);
		nextButton.setText(isLastAnswer() ? "进入后续" : "下一张");
	}

	private boolean isLastAnswer() {
		return project.answerFiles() != null
			   && !project.answerFiles().isEmpty()
			   && project.index() >= project.answerFiles().size() - 1;
	}

	private boolean saveCurrentReview() {
		if (currentResult == null) {
			return false;
		}
		String examineeId = examIdField.textField.getText() == null ? "" : examIdField.textField.getText().trim();
		if (examineeId.isEmpty()) {
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, "请先确认准考证号。");
			return false;
		}

		List<String> answers = new ArrayList<>();
		for (int row = 0; row < tableModel.getRowCount(); row++) {
			if (!isChoiceRow(row)) {
				continue;
			}
			Object value = tableModel.getValueAt(row, 1);
			String answer = value == null ? "" : value.toString().trim();
			answers.add(answer.isEmpty() ? BLANK_ANSWER_PLACEHOLDER : answer);
		}
		File answerFile = project.answerFiles() == null || project.index() >= project.answerFiles().size()
				? null
				: project.answerFiles().get(project.index());
		project.putReviewedAnswerIfDifferent(answerFile, examineeId, String.join(" ", answers));
		project.write();
		return true;
	}

	private boolean isChoiceRow(int row) {
		if (row < 0 || row >= tableModel.getRowCount()) {
			return false;
		}
		Object value = tableModel.getValueAt(row, 0);
		if (!(value instanceof Number number)) {
			return false;
		}
		AnswerSheet sheet = currentAnswerSheet == null ? answerSheet : currentAnswerSheet;
		return sheet != null && sheet.getChoiceQuestions().stream().anyMatch(question -> question.number() == number.intValue());
	}

	private static final class ResultCellRenderer extends DefaultTableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
													   int row, int column) {
			Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			if (!isSelected) {
				String status = String.valueOf(table.getValueAt(row, 3));
				if ("存疑".equals(status)) {
					component.setBackground(QRColorsAndFonts.BORDER_COLOR);
				} else if ("错误".equals(status)) {
					component.setBackground(QRColorsAndFonts.RED_NORMAL);
				} else {
					component.setBackground(QRColorsAndFonts.TEXT_COLOR_BACK);
				}
			}
			setHorizontalAlignment(SwingConstants.CENTER);
			return component;
		}
	}

	private static final class ReviewPreviewPanel extends AnswerSheetPreviewPanel {
		private AnswerSheet answerSheet;
		private String examId = "";
		private Map<Integer, String> detectedAnswers = Map.of();
		private boolean answerAligned;

		private ReviewPreviewPanel(String emptyText, Dimension preferredSize, Dimension minimumSize,
								   Color examColor, Color choiceColor, Color fillColor) {
			super(emptyText, preferredSize, minimumSize, examColor, choiceColor, fillColor);
		}

		private void setReviewData(AnswerSheet answerSheet, String examId, Map<Integer, String> detectedAnswers) {
			this.answerSheet = answerSheet;
			this.examId = examId == null ? "" : examId;
			this.detectedAnswers = detectedAnswers == null ? Map.of() : new HashMap<>(detectedAnswers);
			repaint();
		}

		@Override
		protected void paintOverlay(Graphics2D g2, DrawBox box) {
			if (answerSheet == null) {
				return;
			}
			drawExamId(g2, box);
			for (Question question : answerSheet.getChoiceQuestions()) {
				String detected = detectedAnswers.get(question.number());
				if (detected != null && !detected.isBlank()) {
					drawQuestionAnswer(g2, box, question, detected);
				}
			}
		}

		private void drawExamId(Graphics2D g2, DrawBox box) {
			if (examId.isBlank()) {
				return;
			}
			Rect rect = groupBounds(answerSheet.getExamIdQuestions());
			if (rect == null) {
				return;
			}
			Rectangle bounds = scaleRect(box, rect);
			drawBadge(g2, "准考证 " + examId, bounds.x + bounds.width + 8, bounds.y, new Color(67, 120, 210, 230));
		}

		private void drawQuestionAnswer(Graphics2D g2, DrawBox box, Question question, String detected) {
			Rect optionRect = answerAligned ? null : detectedOptionRect(question, detected);
			if (optionRect != null) {
				Rectangle optionBounds = scaleRect(box, optionRect);
				drawBadgeCentered(g2, detected, optionBounds.x + optionBounds.width / 2,
						optionBounds.y + optionBounds.height / 2, new Color(35, 135, 78, 230));
				return;
			}

			Rect rect = groupBounds(List.of(question));
			if (rect == null) {
				return;
			}
			Rectangle bounds = scaleRect(box, rect);
			Color color = ("未填".equals(detected) || "?".equals(detected) || detected.contains("?"))
					? new Color(210, 95, 55, 230)
					: new Color(35, 135, 78, 230);
			drawBadge(g2, detected, bounds.x + bounds.width + 4, bounds.y + bounds.height / 2 - 10, color);
		}

		private void setAnswerAligned(boolean answerAligned) {
			this.answerAligned = answerAligned;
			repaint();
		}

		private boolean answerAligned() {
			return answerAligned;
		}

		private Rect detectedOptionRect(Question question, String detected) {
			if (detected == null || detected.isBlank() || question.optionRegions() == null) {
				return null;
			}
			int index = switch (detected.trim().toUpperCase()) {
				case "A" -> 0;
				case "B" -> 1;
				case "C" -> 2;
				case "D" -> 3;
				default -> -1;
			};
			return index < 0 || index >= question.optionRegions().size() ? null : question.optionRegions().get(index);
		}

		private void drawBadgeCentered(Graphics2D g2, String text, int centerX, int y, Color color) {
			Font oldFont = g2.getFont();
			g2.setFont(TEXT_FONT.deriveFont(Font.BOLD, 12f));
			FontMetrics fm = g2.getFontMetrics();
			int width = fm.stringWidth(text) + 6;
			int height = fm.getHeight() + 4;
			int drawX = centerX - width / 2;
			int drawY = Math.max(4, Math.min(y - height / 2, getHeight() - height - 4));
			g2.setColor(color);
			g2.fillRoundRect(drawX, drawY, width, height, 6, 6);
			g2.setColor(Color.WHITE);
			g2.drawString(text, drawX + 3, drawY + fm.getAscent() + 2);
			g2.setFont(oldFont);
		}

		private void drawBadge(Graphics2D g2, String text, int x, int y, Color color) {
			Font oldFont = g2.getFont();
			g2.setFont(TEXT_FONT.deriveFont(Font.BOLD, 12f));
			FontMetrics fm = g2.getFontMetrics();
			int width = fm.stringWidth(text) + 10;
			int height = fm.getHeight() + 4;
			int drawX = x;
			if (drawX + width > getWidth() - 4) {
				drawX = Math.max(4, x - width - 12);
			}
			int drawY = Math.max(4, Math.min(y, getHeight() - height - 4));
			g2.setColor(color);
			g2.fillRoundRect(drawX, drawY, width, height, 8, 8);
			g2.setColor(Color.WHITE);
			g2.drawString(text, drawX + 5, drawY + fm.getAscent() + 2);
			g2.setFont(oldFont);
		}
	}
}
