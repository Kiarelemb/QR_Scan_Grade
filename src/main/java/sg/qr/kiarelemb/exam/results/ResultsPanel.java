package sg.qr.kiarelemb.exam.results;

import sg.qr.kiarelemb.MainWindow;
import sg.qr.kiarelemb.data.Utils;
import sg.qr.kiarelemb.exam.ManualScoringPanel;
import sg.qr.kiarelemb.exam.model.GradingProject;
import sg.qr.kiarelemb.exam.model.SheetTemplate;
import sg.qr.kiarelemb.exam.model.SubjectiveAnswerRegion;
import sg.qr.kiarelemb.exam.processing.SheetTemplateFileStore;
import sg.qr.kiarelemb.exam.scoring.QuestionScorePolicy;
import sg.qr.kiarelemb.exam.scoring.ScaledScoreConfigDialog;
import sg.qr.kiarelemb.exam.scoring.ScoreSection;
import sg.qr.kiarelemb.menu.data.EnglishScoreInput;
import swing.qr.kiarelemb.basic.*;
import swing.qr.kiarelemb.task.QRTaskOptions;
import swing.qr.kiarelemb.task.QRTaskRunner;
import swing.qr.kiarelemb.task.QRTaskWorker;
import swing.qr.kiarelemb.theme.QRColorsAndFonts;
import swing.qr.kiarelemb.window.basic.QRDialog;
import swing.qr.kiarelemb.window.enhance.QROpinionDialog;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.List;

/**
 * Final grading and score export dialog.
 */
public class ResultsPanel extends QRPanel {
	public static ResultsPanel INSTANCE;

	private static final String MODE_NORMAL = "普通算分";
	private static final String MODE_SCALE = "尺度算分";
	public static final int FIELD_HEIGHT = 28;

	private final GradingProject project;
	public final QRTextPane ruleTextPane = new QRTextPane();
	private final QRComboBox scoreModeBox = new QRComboBox(MODE_NORMAL, MODE_SCALE);
	private final DefaultTableModel scoreTableModel = new DefaultTableModel(new String[]{"题号", "题型", "分值"}, 0) {
		@Override
		public boolean isCellEditable(int row, int column) {
			return false;
		}
	};
	private final QRTable scoreTable = new QRTable(scoreTableModel);
	private final DefaultTableModel resultTableModel = new DefaultTableModel(new String[]{"排名", "准考证号", "姓名", "入班英语成绩", "总分", "提高分数"}, 0) {
		@Override
		public boolean isCellEditable(int row, int column) {
			return false;
		}
	};
	private final QRTable resultTable = new QRTable(resultTableModel);
	private final QRLabel summaryLabel = new QRLabel();

	private final Map<String, BigDecimal> entranceEnglishScores = new LinkedHashMap<>();
	private QuestionScorePolicy.Config scaleConfig = QuestionScorePolicy.Config.defaults();
	private ScoringPlan scorePlan = ScoringPlan.empty();
	private List<ScoreOutcome> scoreResults = new ArrayList<>();
	private List<String> exportRows = new ArrayList<>();
	private QuestionScorePolicy.ScaleScoreReport lastScaleReport;
	private boolean lastResultScaleMode = false;
	private QRTaskWorker<PreviewResult> previewWorker;
	private QRTaskWorker<CalculateResult> calculateWorker;
	private int previewSerial;
	private int calculateSerial;

	public ResultsPanel(GradingProject project) {
		this.project = project;
		INSTANCE = this;
		FinishProjectButton.END_PROJECT.setEnabled(false);
		loadEntranceEnglishScores();
		initView();
		previewScores(null);
	}

	private void initView() {
		setLayout(new BorderLayout(5, 5));
		add(buildTopPanel(), BorderLayout.NORTH);
		add(buildCenterPanel(), BorderLayout.CENTER);
		add(buildBottomPanel(), BorderLayout.SOUTH);
	}

	private Component buildTopPanel() {
		QRPanel panel = new QRPanel(false, new BorderLayout(8, 0));
		panel.setBorder(new MatteBorder(0, 0, 3, 0, QRColorsAndFonts.LINE_COLOR));

		QRLabel titleLabel = new QRLabel("分数计算与成绩导出");
		titleLabel.setFont(QRColorsAndFonts.createFont(18));
		panel.add(titleLabel, BorderLayout.WEST);

		QRPanel modePanel = new QRPanel(false, new FlowLayout(FlowLayout.RIGHT, 8, 0));
		QRLabel modeLabel = new QRLabel("算分方式：");
		modeLabel.setPreferredSize(new Dimension(80, FIELD_HEIGHT));
		scoreModeBox.setPreferredSize(new Dimension(100, FIELD_HEIGHT));
		modePanel.add(modeLabel);
		modePanel.add(scoreModeBox);
		panel.add(modePanel, BorderLayout.EAST);
		return panel;
	}

	private Component buildCenterPanel() {
		QRSplitPane splitPane = new QRSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		splitPane.setResizeWeight(0.25);
		splitPane.setDividerLocation(240);
		splitPane.setLeftComponent(buildRulePanel());
		splitPane.setRightComponent(buildPreviewPanel());
		return splitPane;
	}

	private Component buildRulePanel() {
		QRPanel panel = new QRPanel(false, new BorderLayout(5, 5));
		panel.setBorder(new LineBorder(QRColorsAndFonts.FRAME_COLOR_BACK, 5));

		QRLabel ruleLabel = new QRLabel("计分规则");
		ruleLabel.setFont(QRColorsAndFonts.createFont(16));
		panel.add(ruleLabel, BorderLayout.NORTH);

		ruleTextPane.setFont(QRColorsAndFonts.createFont(15));
		ruleTextPane.setLineWrap(true);
		ruleTextPane.addUndoManager();
		ruleTextPane.setText(defaultRulesText());
		ruleTextPane.addScrollPane().addLineNumberModelForTextPane();
		panel.add(ruleTextPane.addScrollPane(), BorderLayout.CENTER);

		QRPanel buttonPanel = new QRPanel(false, new FlowLayout(FlowLayout.RIGHT, 8, 5));
		buttonPanel.setPreferredSize(new Dimension(100, 35));
		QRRoundButton previewButton = new QRRoundButton("预览分值");
		previewButton.setPreferredSize(new Dimension(100, 30));
		previewButton.addClickAction(this::previewScores);
		buttonPanel.add(previewButton);
		panel.add(buttonPanel, BorderLayout.SOUTH);
		return panel;
	}

	private Component buildPreviewPanel() {
		QRSplitPane splitPane = new QRSplitPane(JSplitPane.VERTICAL_SPLIT);
		splitPane.setResizeWeight(0.45);

		scoreTable.setRowHeight(24);
		Font font = QRColorsAndFonts.createFont(14);
		scoreTable.getTableHeader().setFont(font);
		scoreTable.setFont(font);
		splitPane.setTopComponent(scoreTable.addScrollPane());

		resultTable.setRowHeight(24);
		resultTable.getTableHeader().setFont(font);
		resultTable.setFont(font);
		splitPane.setBottomComponent(resultTable.addScrollPane());
		return splitPane;
	}

	private Component buildBottomPanel() {
		QRPanel panel = new QRPanel(false, new BorderLayout(8, 0));
		panel.setBorder(new LineBorder(QRColorsAndFonts.FRAME_COLOR_BACK, 10));
		summaryLabel.setText("已校对 " + project.combinedAnswersByExamId().size() + " 份答卷。");
		panel.add(summaryLabel, BorderLayout.CENTER);

		QRPanel buttonPanel = new QRPanel(false, new FlowLayout(FlowLayout.RIGHT, 8, 0));
		QRRoundButton verifyExamIdButton = new QRRoundButton("校对考号");
		verifyExamIdButton.setPreferredSize(new Dimension(110, 30));
		verifyExamIdButton.addClickAction(this::verifyExamineeIds);
		buttonPanel.add(verifyExamIdButton);
		buttonPanel.add(FinishProjectButton.END_PROJECT);
		buttonPanel.add(BackToReviewButton.BACK_TO_REVIEW_BUTTON);
		buttonPanel.add(ExportResultsButton.EXPORT_SCORES_BUTTON);
		buttonPanel.add(CalculateScoresButton.CALCULATE_SCORES_BUTTON);
		panel.add(buttonPanel, BorderLayout.EAST);
		return panel;
	}

	void backToReview() {
		int lastIndex = Math.max(0, project.answerFiles() == null ? 0 : project.answerFiles().size() - 1);
		project.setIndex(lastIndex);
		project.write();
		MainWindow.INSTANCE.showProjectReview(project);
	}

	private String defaultRulesText() {
		String templateRules = templateDefaultRulesText();
		if (!templateRules.isBlank()) {
			return templateRules;
		}
		int count = standardAnswerCount();
		if (count == 0) {
			return "# 每行格式：题型 题号范围 总分\n# 示例：选择 1-40 80\n";
		}
		return "# 每行格式：题型 题号范围 总分\n"
			   + "# 示例：单词 1-10 20\n"
			   + "# 多个范围可用逗号隔开，如：用语 11-15,20 10\n\n"
			   + "选择 1-" + count + " " + count + "\n";
	}

	private String templateDefaultRulesText() {
		try {
			SheetTemplate template = SheetTemplateFileStore.load(new File(project.templateFilePath()));
			return template.defaultScoreRules() == null ? "" : template.defaultScoreRules().trim();
		} catch (IOException ex) {
			return "";
		}
	}

	private void saveDefaultRulesToTemplate(String rulesText) {
		try {
			File templateFile = new File(project.templateFilePath());
			SheetTemplate template = SheetTemplateFileStore.load(templateFile);
			SheetTemplate updated = new SheetTemplate(
					template.name(),
					template.pictureFile(),
					template.answerSheet(),
					template.examRegionRect(),
					template.choiceRegionRect(),
					template.fillBlankRegionRect(),
					rulesText,
					template.pageCount(),
					template.subjectiveRegions(),
					template.pictureFiles());
			SheetTemplateFileStore.save(updated, templateFile);
		} catch (IOException ex) {
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, "保存模板计分规则失败：\n" + ex.getMessage());
		}
	}

	private void previewScores(ActionEvent event) {
		startPreviewTask();
	}

	private void startPreviewTask() {
		int serial = ++previewSerial;
		if (previewWorker != null && !previewWorker.isDone()) {
			previewWorker.cancel(true);
		}
		String mode = String.valueOf(scoreModeBox.getSelectedItem());
		String rulesText = ruleTextPane.getText();
		QuestionScorePolicy.Config config = scaleConfig;
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		summaryLabel.setText("正在预览分值...");
		QRTaskOptions options = new QRTaskOptions()
				.onSuccess((PreviewResult result) -> applyPreviewResult(serial, result))
				.onError(error -> handleScoreTaskError(serial, true, error))
				.onCancelled(() -> {
					if (serial == previewSerial) {
						setCursor(Cursor.getDefaultCursor());
					}
				});
		previewWorker = QRTaskRunner.run(options, context -> {
			context.message("正在解析计分规则...");
			ScoringPlan plan = MODE_SCALE.equals(mode)
					? buildScaleScorePlan(calculateScaleScoreReport(config, rulesText), rulesText)
					: parseNormalScorePlan(rulesText, scoreRuleQuestionCount(rulesText));
			saveDefaultRulesToTemplate(rulesText);
			return new PreviewResult(plan);
		});
	}

	private void applyPreviewResult(int serial, PreviewResult result) {
		if (serial != previewSerial) {
			return;
		}
		setCursor(Cursor.getDefaultCursor());
		this.scorePlan = result.scorePlan();
		renderQuestionScores(this.scorePlan);
		renderResultColumns(this.scorePlan.sectionNames());
		summaryLabel.setText("分值预览完成，满分 " + Utils.formatScore(this.scorePlan.totalScore()) + "。");
	}

	private void handleScoreTaskError(int serial, boolean previewTask, Throwable error) {
		if ((previewTask && serial != previewSerial) || (!previewTask && serial != calculateSerial)) {
			return;
		}
		setCursor(Cursor.getDefaultCursor());
		if (error instanceof ScoreRuleException ex) {
			showScoreRuleError(ex);
		} else if (error instanceof IllegalArgumentException ex) {
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, ex.getMessage());
		} else {
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, "成绩处理失败：\n" + error.getMessage());
		}
	}

	private void verifyExamineeIds(ActionEvent event) {
		new ExamineeIdVerifyDialog().setVisible(true);
	}

	void calculateScores(ActionEvent event) {
		String mode = String.valueOf(scoreModeBox.getSelectedItem());
		QuestionScorePolicy.ScaleScoreReport scaleReport = null;
		if (MODE_SCALE.equals(mode)) {
			scaleReport = showScaleScoreDialog();
			if (scaleReport == null) {
				return;
			}
		}
		startCalculateTask(mode, ruleTextPane.getText(), scaleReport);
	}

	private void startCalculateTask(String mode, String rulesText, QuestionScorePolicy.ScaleScoreReport scaleReport) {
		int serial = ++calculateSerial;
		if (calculateWorker != null && !calculateWorker.isDone()) {
			calculateWorker.cancel(true);
		}
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		summaryLabel.setText("正在计算成绩...");
		QRTaskOptions options = new QRTaskOptions()
				.onSuccess((CalculateResult result) -> applyCalculateResult(serial, result))
				.onError(error -> handleScoreTaskError(serial, false, error))
				.onCancelled(() -> {
					if (serial == calculateSerial) {
						setCursor(Cursor.getDefaultCursor());
					}
				});
		calculateWorker = QRTaskRunner.run(options, context -> {
			context.message("正在计算成绩...");
			ScoringPlan plan;
			List<ScoreOutcome> results;
			QuestionScorePolicy.ScaleScoreReport report = null;
			boolean scaleMode = MODE_SCALE.equals(mode);
			if (scaleMode) {
				report = scaleReport;
				plan = buildScaleScorePlan(report, rulesText);
				results = applyManualScores(toScoreResults(report.studentScores(), plan), plan, rulesText);
			} else {
				plan = parseNormalScorePlan(rulesText, scoreRuleQuestionCount(rulesText));
				results = applyManualScores(enrichStudentInfo(Utils.calculateNormalScores(project, plan)), plan, rulesText);
			}
			saveDefaultRulesToTemplate(rulesText);
			List<String> rows = Utils.buildExportRows(project);
			return new CalculateResult(plan, results, rows, report, scaleMode);
		});
	}

	private void applyCalculateResult(int serial, CalculateResult result) {
		if (serial != calculateSerial) {
			return;
		}
		setCursor(Cursor.getDefaultCursor());
		this.scorePlan = result.scorePlan();
		this.scoreResults = result.scoreResults();
		this.exportRows = result.exportRows();
		this.lastScaleReport = result.scaleReport();
		this.lastResultScaleMode = result.scaleMode();
		renderQuestionScores(this.scorePlan);
		renderResultColumns(this.scorePlan.sectionNames());
		renderScoreResults(this.scoreResults, this.scorePlan.sectionNames());
		FinishProjectButton.END_PROJECT.setEnabled(true);
		summaryLabel.setText("成绩计算完成：" + scoreResults.size() + " 人，导出行 " + exportRows.size() + " 条。");
	}

	private void loadEntranceEnglishScores() {
		entranceEnglishScores.clear();
		Map<String, BigDecimal> scoresByName = EnglishScoreInput.readScores();
		for (Map.Entry<String, String> entry : project.studentNamesByExamId().entrySet()) {
			BigDecimal score = scoresByName.get(entry.getValue());
			if (score != null) {
				entranceEnglishScores.put(entry.getKey(), score);
			}
		}
	}

	public void showScoreRuleError(ScoreRuleException ex) {
		locateRuleLine(ex.lineNumber());
		QROpinionDialog.messageErrShow(MainWindow.INSTANCE, ex.getMessage());
	}

	private void locateRuleLine(int lineNumber) {
		if (lineNumber <= 0) {
			return;
		}
		String text = ruleTextPane.getText();
		int start = 0;
		for (int line = 1; line < lineNumber && start < text.length(); line++) {
			int next = text.indexOf('\n', start);
			if (next < 0) {
				return;
			}
			start = next + 1;
		}
		int end = text.indexOf('\n', start);
		if (end < 0) {
			end = text.length();
		}
		ruleTextPane.requestFocusInWindow();
		ruleTextPane.select(start, end);
	}

	private ScoringPlan buildScaleScorePlan() {
		return buildScaleScorePlan(calculateScaleScoreReport());
	}

	private QuestionScorePolicy.ScaleScoreReport calculateScaleScoreReport() {
		return calculateScaleScoreReport(scaleConfig);
	}

	public QuestionScorePolicy.ScaleScoreReport calculateScaleScoreReport(QuestionScorePolicy.Config config) {
		return calculateScaleScoreReport(config, ruleTextPane.getText());
	}

	private QuestionScorePolicy.ScaleScoreReport calculateScaleScoreReport(QuestionScorePolicy.Config config, String rulesText) {
		return new QuestionScorePolicy(
				project.standardAnswers(),
				project.combinedAnswersByExamId(),
				parseScaleSections(rulesText, standardAnswerCount()),
				entranceEnglishScores,
				config
		).calculateScaleScores();
	}

	private QuestionScorePolicy.ScaleScoreReport showScaleScoreDialog() {
		ScaledScoreConfigDialog dialog = new ScaledScoreConfigDialog(this, scaleConfig);
		dialog.setVisible(true);
		if (!dialog.confirmed()) {
			return null;
		}
		scaleConfig = dialog.config();
		return dialog.report();
	}

	private ScoringPlan buildScaleScorePlan(QuestionScorePolicy.ScaleScoreReport report) {
		return buildScaleScorePlan(report, ruleTextPane.getText());
	}

	private ScoringPlan buildScaleScorePlan(QuestionScorePolicy.ScaleScoreReport report, String rulesText) {
		Map<Integer, BigDecimal> questionScores = new LinkedHashMap<>(report.questionScores());
		Map<Integer, String> sections = new LinkedHashMap<>();
		List<String> sectionNames = new ArrayList<>();
		for (ScorePolicy rule : parseDisplayScoreRules(rulesText, scoreRuleQuestionCount(rulesText))) {
			if (!sectionNames.contains(rule.questionType())) {
				sectionNames.add(rule.questionType());
			}
			BigDecimal perQuestionScore = rule.totalScore().divide(BigDecimal.valueOf(rule.questionNumbers().size()), 8, RoundingMode.HALF_UP);
			for (Integer questionNumber : rule.questionNumbers()) {
				sections.put(questionNumber, rule.questionType());
				if (questionNumber > standardAnswerCount()) {
					questionScores.put(questionNumber, perQuestionScore);
				}
			}
		}
		return new ScoringPlan(questionScores, sections, sectionNames);
	}

	private List<ScoreOutcome> toScoreResults(List<QuestionScorePolicy.ScaleStudentScore> scaleScores, ScoringPlan displayScorePlan) {
		List<ScoreOutcome> results = new ArrayList<>();
		Map<String, String> answersByExamId = project.combinedAnswersByExamId();
		String[] standardAnswers = project.standardAnswers() == null ? new String[0] : project.standardAnswers();
		for (QuestionScorePolicy.ScaleStudentScore scaleScore : scaleScores) {
			String name = project.studentNamesByExamId().getOrDefault(scaleScore.examineeId(), scaleScore.name());
			BigDecimal entrance = scaleScore.entranceEnglishScore();
			Map<String, BigDecimal> sectionScores = calculateSectionScores(
					answersByExamId.get(scaleScore.examineeId()),
					standardAnswers,
					displayScorePlan);
			results.add(new ScoreOutcome(
					scaleScore.rank(),
					scaleScore.examineeId(),
					name,
					entrance,
					sectionScores,
					scaleScore.earnedScore(),
					entrance == null ? null : scaleScore.earnedScore().subtract(entrance)
			));
		}
		return results;
	}

	private Map<String, BigDecimal> calculateSectionScores(String answerLine, String[] standardAnswers, ScoringPlan scorePlan) {
		String[] answers = Utils.splitAnswers(answerLine);
		Map<String, BigDecimal> sectionScores = Utils.emptySectionScores(scorePlan.sectionNames());
		for (int i = 0; i < standardAnswers.length; i++) {
			String expected = Utils.normalizeAnswer(standardAnswers[i]);
			String actual = i < answers.length ? Utils.normalizeAnswer(answers[i]) : "";
			if (expected.isEmpty() || !expected.equals(actual)) {
				continue;
			}
			int questionNumber = i + 1;
			String sectionName = scorePlan.questionSections().getOrDefault(questionNumber, "");
			if (!sectionName.isEmpty()) {
				BigDecimal questionScore = scorePlan.questionScores().getOrDefault(questionNumber, BigDecimal.ZERO);
				sectionScores.put(sectionName, sectionScores.getOrDefault(sectionName, BigDecimal.ZERO).add(questionScore));
			}
		}
		return sectionScores;
	}

	private List<ScoreOutcome> enrichStudentInfo(List<ScoreOutcome> rawResults) {
		List<ScoreOutcome> results = new ArrayList<>();
		for (ScoreOutcome result : rawResults) {
			String name = project.studentNamesByExamId().getOrDefault(result.examineeId(), result.name());
			BigDecimal entrance = entranceEnglishScores.get(result.examineeId());
			results.add(new ScoreOutcome(
					result.rank(),
					result.examineeId(),
					name,
					entrance,
					result.sectionScores(),
					result.earnedScore(),
					entrance == null ? null : result.earnedScore().subtract(entrance)
			));
		}
		return results;
	}

	private List<ScoreSection> parseScaleSections(String text, int questionCount) {
		List<ScoreSection> sections = new ArrayList<>();
		for (ScorePolicy rule : parseScoreRules(text, scoreRuleQuestionCount(text))) {
			List<Integer> machineQuestionNumbers = rule.questionNumbers().stream()
					.filter(questionNumber -> questionNumber <= questionCount)
					.toList();
			if (machineQuestionNumbers.isEmpty()) {
				continue;
			}
			List<Integer> questionIndices = machineQuestionNumbers.stream()
					.map(questionNumber -> questionNumber - 1)
					.toList();
			int start = Collections.min(machineQuestionNumbers);
			int end = Collections.max(machineQuestionNumbers);
			BigDecimal machineTotal = rule.totalScore()
					.multiply(BigDecimal.valueOf(machineQuestionNumbers.size()))
					.divide(BigDecimal.valueOf(rule.questionNumbers().size()), 8, RoundingMode.HALF_UP);
			sections.add(new ScoreSection(rule.questionType(), start, end, machineTotal.doubleValue(), questionIndices));
		}
		return sections;
	}

	public int standardAnswerCount() {
		return project.standardAnswers() == null ? 0 : project.standardAnswers().length;
	}

	int scoreRuleQuestionCount() {
		return scoreRuleQuestionCount(ruleTextPane.getText());
	}

	private int scoreRuleQuestionCount(String rulesText) {
		int count = standardAnswerCount();
		String text = rulesText == null ? "" : rulesText;
		for (String rawLine : text.split("\\R")) {
			String line = Utils.stripComment(rawLine).trim();
			if (line.isBlank()) {
				continue;
			}
			String[] parts = line.split("[ \\t]+");
			if (parts.length < 2) {
				continue;
			}
			for (String range : parts[1].split("[,，]")) {
				String part = range.trim();
				if (part.isEmpty()) {
					continue;
				}
				String[] bounds = part.split("-", -1);
				for (String bound : bounds) {
					try {
						count = Math.max(count, Integer.parseInt(bound.trim()));
					} catch (NumberFormatException ignored) {
					}
				}
			}
		}
		return count;
	}

	private List<ScorePolicy> manualScoreRules(ScoringPlan scorePlan) {
		return manualScoreRules(scorePlan, ruleTextPane.getText());
	}

	private List<ScorePolicy> manualScoreRules(ScoringPlan scorePlan, String rulesText) {
		int machineCount = standardAnswerCount();
		List<ScorePolicy> rules = new ArrayList<>();
		for (ScorePolicy rule : parseDisplayScoreRules(rulesText, scoreRuleQuestionCount(rulesText))) {
			boolean manual = rule.questionNumbers().stream().allMatch(questionNumber -> questionNumber > machineCount);
			if (manual && !scorePlan.sectionNames().contains(rule.questionType())) {
				continue;
			}
			if (manual) {
				rules.add(rule);
			}
		}
		return rules;
	}

	private List<ScoreOutcome> applyManualScores(List<ScoreOutcome> baseResults, ScoringPlan scorePlan) {
		return applyManualScores(baseResults, scorePlan, ruleTextPane.getText());
	}

	private List<ScoreOutcome> applyManualScores(List<ScoreOutcome> baseResults, ScoringPlan scorePlan, String rulesText) {
		List<ScorePolicy> manualRules = manualScoreRules(scorePlan, rulesText);
		if (manualRules.isEmpty()) {
			return baseResults;
		}
		SheetTemplate template = manualScoreTemplate();
		List<ScoreOutcome> results = new ArrayList<>();
		for (ScoreOutcome result : baseResults) {
			Map<String, BigDecimal> sectionScores = new LinkedHashMap<>(result.sectionScores());
			BigDecimal total = result.earnedScore();
			Map<String, String> savedScores = project.manualScoresFor(result.examineeId());
			for (ScorePolicy rule : manualRules) {
				String savedScore = ManualScoringPanel.scoreForRule(savedScores, rule.questionType(), rule.questionNumbers(), project, template);
				BigDecimal score = parseManualScore(savedScore, rule.totalScore());
				sectionScores.put(rule.questionType(), score);
				total = total.add(score);
			}
			results.add(new ScoreOutcome(0, result.examineeId(), result.name(), result.entranceEnglishScore(),
					sectionScores, total, result.entranceEnglishScore() == null ? null : total.subtract(result.entranceEnglishScore())));
		}
		results.sort(Comparator.comparing(ScoreOutcome::earnedScore).reversed().thenComparing(ScoreOutcome::examineeId));
		return Utils.rankedResults(results);
	}

	private SheetTemplate manualScoreTemplate() {
		try {
			return SheetTemplateFileStore.load(new File(project.templateFilePath()));
		} catch (IOException ex) {
			return null;
		}
	}

	private BigDecimal parseManualScore(String text, BigDecimal maxScore) {
		if (text == null || text.isBlank()) {
			return BigDecimal.ZERO;
		}
		try {
			BigDecimal score = new BigDecimal(text.trim()).setScale(2, RoundingMode.HALF_UP);
			if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(maxScore) > 0) {
				return BigDecimal.ZERO;
			}
			return score;
		} catch (NumberFormatException ex) {
			return BigDecimal.ZERO;
		}
	}

	private void renderQuestionScores(ScoringPlan scorePlan) {
		scoreTableModel.setRowCount(0);
		for (Map.Entry<Integer, BigDecimal> entry : scorePlan.questionScores().entrySet()) {
			scoreTableModel.addRow(new Object[]{
					entry.getKey(),
					scorePlan.questionSections().getOrDefault(entry.getKey(), ""),
					Utils.formatScore(entry.getValue())
			});
		}
	}

	private void renderResultColumns(List<String> sectionNames) {
		Vector<String> columns = new Vector<>();
		columns.add("排名");
		columns.add("准考证号");
		columns.add("姓名");
		columns.add("入班英语成绩");
		columns.addAll(sectionNames);
		columns.add("总分");
		columns.add("提高分数");
		resultTableModel.setColumnIdentifiers(columns);
		adjustResultTableColumnWidths();
	}

	private void adjustResultTableColumnWidths() {
		FontMetrics metrics = resultTable.getTableHeader().getFontMetrics(resultTable.getTableHeader().getFont());
		for (int i = 0; i < resultTable.getColumnModel().getColumnCount(); i++) {
			TableColumn column = resultTable.getColumnModel().getColumn(i);
			String header = String.valueOf(column.getHeaderValue());
			int width = metrics.stringWidth(header) + 28;
			width = Math.max(width, minResultColumnWidth(header));
			column.setPreferredWidth(width);
		}
	}

	private int minResultColumnWidth(String header) {
		return switch (header) {
			case "排名" -> 55;
			case "准考证号" -> 120;
			case "姓名" -> 80;
			case "入班英语成绩" -> 115;
			case "总分" -> 70;
			case "提高分数" -> 90;
			default -> 72;
		};
	}

	private void renderScoreResults(List<ScoreOutcome> results, List<String> sectionNames) {
		resultTableModel.setRowCount(0);
		for (ScoreOutcome result : results) {
			Vector<Object> row = new Vector<>();
			row.add(result.rank());
			row.add(result.examineeId());
			row.add(result.name());
			row.add(result.entranceEnglishScore() == null ? "" : formatOneDecimal(result.entranceEnglishScore()));
			for (String sectionName : sectionNames) {
				row.add(formatOneDecimal(result.sectionScores().getOrDefault(sectionName, BigDecimal.ZERO)));
			}
			row.add(formatOneDecimal(result.earnedScore()));
			row.add(result.improvementScore() == null ? "" : formatOneDecimal(result.improvementScore()));
			resultTableModel.addRow(row);
		}
		appendAverageRow();
	}

	private void appendAverageRow() {
		if (resultTableModel.getRowCount() == 0) {
			return;
		}
		Vector<Object> row = new Vector<>();
		for (int col = 0; col < resultTableModel.getColumnCount(); col++) {
			if (col == 0) {
				row.add("均分");
			} else if (col < 3) {
				row.add("");
			} else {
				BigDecimal sum = BigDecimal.ZERO;
				int count = 0;
				for (int r = 0; r < resultTableModel.getRowCount(); r++) {
					BigDecimal value = parseDecimal(resultTableModel.getValueAt(r, col));
					if (value != null) {
						sum = sum.add(value);
						count++;
					}
				}
				row.add(count == 0 ? "" : formatOneDecimal(sum.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP)));
			}
		}
		resultTableModel.addRow(row);
	}

	public List<ScoreOutcome> scoreResults() {
		return Collections.unmodifiableList(scoreResults);
	}

	public List<String> exportRows() {
		return Collections.unmodifiableList(exportRows);
	}

	void exportScores(ActionEvent event) {
		ResultsExporter.EXPORTER.export(this);
	}

	GradingProject project() {
		return project;
	}

	DefaultTableModel resultTableModel() {
		return resultTableModel;
	}

	ScoringPlan scorePlan() {
		return scorePlan;
	}

	QuestionScorePolicy.ScaleScoreReport lastScaleReport() {
		return lastScaleReport;
	}

	boolean lastResultScaleMode() {
		return lastResultScaleMode;
	}

	public String studentNameFor(String examineeId) {
		return project.studentNamesByExamId().getOrDefault(examineeId, "");
	}

	public Map<Integer, BigDecimal> parseNormalScoreRules(String text, int questionCount) {
		return parseNormalScorePlan(text, questionCount).questionScores();
	}

	private BigDecimal parseDecimal(Object value) {
		if (value == null) {
			return null;
		}
		String text = String.valueOf(value).trim();
		if (text.isEmpty()) {
			return null;
		}
		try {
			return new BigDecimal(text);
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private String formatOneDecimal(BigDecimal score) {
		return score.setScale(1, RoundingMode.HALF_UP).toPlainString();
	}

	public ScoringPlan parseNormalScorePlan(String text, int questionCount) {
		if (questionCount <= 0) {
			return ScoringPlan.empty();
		}

		Map<Integer, BigDecimal> scores = new LinkedHashMap<>();
		Map<Integer, String> sections = new LinkedHashMap<>();
		List<String> sectionNames = new ArrayList<>();
		for (int i = 1; i <= questionCount; i++) {
			scores.put(i, BigDecimal.ZERO);
			sections.put(i, "");
		}

		for (ScorePolicy rule : parseScoreRules(text, questionCount)) {
			if (!sectionNames.contains(rule.questionType())) {
				sectionNames.add(rule.questionType());
			}
			BigDecimal perQuestionScore = rule.totalScore().divide(BigDecimal.valueOf(rule.questionNumbers().size()), 8, RoundingMode.HALF_UP);
			for (Integer questionNumber : rule.questionNumbers()) {
				scores.put(questionNumber, perQuestionScore);
				sections.put(questionNumber, rule.questionType());
			}
		}
		return new ScoringPlan(scores, sections, sectionNames);
	}

	public List<ScorePolicy> parseScoreRules(String text, int questionCount) {
		if (questionCount <= 0) {
			return List.of();
		}
		String value = text == null ? "" : text.trim();
		if (value.isEmpty()) {
			throw new IllegalArgumentException("请填写计分规则。");
		}

		List<RawScorePolicy> pending = new ArrayList<>();
		List<ScorePolicy> rules = new ArrayList<>();
		String[] lines = value.split("\\R");
		for (int i = 0; i < lines.length; i++) {
			String line = Utils.stripComment(lines[i]).trim();
			if (line.isBlank()) {
				continue;
			}
			RawScorePolicy rawRule = RawScorePolicy.parse(line, i + 1, questionCount);
			if (rawRule.totalScore() == null) {
				pending.add(rawRule);
				continue;
			}
			if (pending.isEmpty()) {
				rules.add(rawRule.toScoreRule());
			} else {
				pending.add(rawRule);
				rules.add(ScorePolicy.merge(pending));
				pending.clear();
			}
		}
		if (!pending.isEmpty()) {
			throw new ScoreRuleException(pending.get(0).lineNumber(), "第 " + pending.get(0).lineNumber() + " 行及其后续合并计分规则缺少总分。");
		}
		return rules;
	}

	List<ScorePolicy> parseDisplayScoreRules(String text, int questionCount) {
		if (questionCount <= 0) {
			return List.of();
		}
		String value = text == null ? "" : text.trim();
		if (value.isEmpty()) {
			throw new IllegalArgumentException("请填写计分规则。");
		}

		List<RawScorePolicy> pending = new ArrayList<>();
		List<ScorePolicy> rules = new ArrayList<>();
		String[] lines = value.split("\\R");
		for (int i = 0; i < lines.length; i++) {
			String line = Utils.stripComment(lines[i]).trim();
			if (line.isBlank()) {
				continue;
			}
			RawScorePolicy rawRule = RawScorePolicy.parse(line, i + 1, questionCount);
			if (rawRule.totalScore() == null) {
				pending.add(rawRule);
				continue;
			}
			if (pending.isEmpty()) {
				rules.add(rawRule.toScoreRule());
			} else {
				pending.add(rawRule);
				BigDecimal perQuestionScore = rawRule.totalScore()
						.divide(BigDecimal.valueOf(pending.stream().mapToInt(rule -> rule.questionNumbers().size()).sum()),
								8, RoundingMode.HALF_UP);
				for (RawScorePolicy rule : pending) {
					BigDecimal totalScore = perQuestionScore.multiply(BigDecimal.valueOf(rule.questionNumbers().size()));
					rules.add(new ScorePolicy(rule.questionType(), rule.questionNumbers(), totalScore));
				}
				pending.clear();
			}
		}
		if (!pending.isEmpty()) {
			throw new ScoreRuleException(pending.get(0).lineNumber(), "第 " + pending.get(0).lineNumber() + " 行及其后续合并计分规则缺少总分。");
		}
		return rules;
	}

	public static final class ScoreRuleException extends IllegalArgumentException {
		private final int lineNumber;

		public ScoreRuleException(int lineNumber, String message) {
			super(message);
			this.lineNumber = lineNumber;
		}

		private int lineNumber() {
			return lineNumber;
		}
	}

	private record PreviewResult(ScoringPlan scorePlan) {
	}

	private record CalculateResult(ScoringPlan scorePlan, List<ScoreOutcome> scoreResults, List<String> exportRows,
								   QuestionScorePolicy.ScaleScoreReport scaleReport, boolean scaleMode) {
	}

	private final class ExamineeIdVerifyDialog extends QRDialog {
		private final QRTextPane textPane = new QRTextPane();

		private ExamineeIdVerifyDialog() {
			super(MainWindow.INSTANCE);
			setTitle("校对考生准考证号");
			setTitlePlace(CENTER);
			setSize(680, 560);
			setLocationRelativeTo(MainWindow.INSTANCE);
			setParentWindowNotFollowMove();

			mainPanel.setLayout(new BorderLayout(5, 5));
			QRPanel center = new QRPanel(false, new BorderLayout());
			center.setBorder(new LineBorder(QRColorsAndFonts.LINE_COLOR, 3));
			textPane.addUndoManager();
			textPane.setLineWrap(false);
			textPane.setText(defaultMappingText());
			center.add(textPane.addScrollPane(), BorderLayout.CENTER);
			mainPanel.add(center, BorderLayout.CENTER);

			QRPanel bottom = new QRPanel(false, new FlowLayout(FlowLayout.RIGHT, 8, 0));
			bottom.setBorder(new LineBorder(QRColorsAndFonts.FRAME_COLOR_BACK, 10));
			QRRoundButton cancelButton = new QRRoundButton("取消");
			cancelButton.setPreferredSize(new Dimension(80, 30));
			cancelButton.addClickAction(event -> dispose());
			QRRoundButton saveButton = new QRRoundButton("保存");
			saveButton.setPreferredSize(new Dimension(80, 30));
			saveButton.addClickAction(this::save);
			bottom.add(cancelButton);
			bottom.add(saveButton);
			mainPanel.add(bottom, BorderLayout.SOUTH);
		}

		private String defaultMappingText() {
			StringBuilder builder = new StringBuilder("原准考证号\t新准考证号\t姓名").append(System.lineSeparator());
			Map<String, String> names = project.studentNamesByExamId();
			Set<String> added = new LinkedHashSet<>();
			for (File answerFile : project.answerFiles() == null ? List.<File>of() : project.answerFiles()) {
				GradingProject.ReviewedAnswer reviewedAnswer = project.reviewedAnswerFor(answerFile);
				if (reviewedAnswer == null || reviewedAnswer.examineeId() == null || reviewedAnswer.examineeId().isBlank()) {
					continue;
				}
				String examineeId = reviewedAnswer.examineeId().trim();
				if (added.add(examineeId)) {
					builder.append(examineeId).append('\t')
							.append(examineeId).append('\t')
							.append(names.getOrDefault(examineeId, ""))
							.append(System.lineSeparator());
				}
			}
			for (String examineeId : project.combinedAnswersByExamId().keySet()) {
				if (added.add(examineeId)) {
					builder.append(examineeId).append('\t')
							.append(examineeId).append('\t')
							.append(names.getOrDefault(examineeId, ""))
							.append(System.lineSeparator());
				}
			}
			return builder.toString();
		}

		private void save(ActionEvent event) {
			Map<String, String> mapping = parseMapping();
			if (mapping == null) {
				return;
			}
			if (mapping.isEmpty()) {
				QROpinionDialog.messageTellShow(this, "准考证号没有变化。");
				dispose();
				return;
			}
			project.remapExamineeIds(mapping);
			project.write();
			loadEntranceEnglishScores();
			scoreResults = new ArrayList<>();
			exportRows = new ArrayList<>();
			lastScaleReport = null;
			lastResultScaleMode = false;
			FinishProjectButton.END_PROJECT.setEnabled(false);
			previewScores((ActionEvent) null);
			summaryLabel.setText("已保存准考证号校对结果，请重新计算成绩。");
			QROpinionDialog.messageTellShow(this, "准考证号校对结果已保存。");
			dispose();
		}

		private Map<String, String> parseMapping() {
			Map<String, String> mapping = new LinkedHashMap<>();
			Set<String> oldIds = new LinkedHashSet<>();
			Set<String> newIds = new LinkedHashSet<>();
			Set<String> knownIds = knownExamineeIds();
			String[] lines = (textPane.getText() == null ? "" : textPane.getText()).split("\\R", -1);
			for (int i = 0; i < lines.length; i++) {
				String line = lines[i].trim();
				if (line.isEmpty() || isHeaderLine(line)) {
					continue;
				}
				String[] parts = line.split("\\t", -1);
				if (parts.length < 2 || parts[0].trim().isEmpty() || parts[1].trim().isEmpty()) {
					QROpinionDialog.messageErrShow(this, "第 " + (i + 1) + " 行格式错误，应为：原准考证号\\t新准考证号\\t姓名");
					return null;
				}
				String oldId = parts[0].trim();
				String newId = parts[1].trim();
				if (!knownIds.contains(oldId)) {
					QROpinionDialog.messageErrShow(this, "第 " + (i + 1) + " 行原准考证号不存在：" + oldId);
					return null;
				}
				if (!oldIds.add(oldId)) {
					QROpinionDialog.messageErrShow(this, "第 " + (i + 1) + " 行原准考证号重复：" + oldId);
					return null;
				}
				if (!newIds.add(newId)) {
					QROpinionDialog.messageErrShow(this, "第 " + (i + 1) + " 行新准考证号重复：" + newId);
					return null;
				}
				if (!oldId.equals(newId)) {
					mapping.put(oldId, newId);
				}
			}
			for (String newId : newIds) {
				if (knownIds.contains(newId) && !oldIds.contains(newId)) {
					QROpinionDialog.messageErrShow(this, "新准考证号已存在，不能覆盖未参与校对的考号：" + newId);
					return null;
				}
			}
			return mapping;
		}

		private boolean isHeaderLine(String line) {
			return line.contains("原准考证号") && line.contains("新准考证号");
		}

		private Set<String> knownExamineeIds() {
			Set<String> ids = new LinkedHashSet<>();
			ids.addAll(project.combinedAnswersByExamId().keySet());
			ids.addAll(project.studentNamesByExamId().keySet());
			ids.addAll(project.manualScoresByExamId().keySet());
			return ids;
		}
	}
}
