package sg.qr.kiarelemb.grading.end;

import org.bytedeco.opencv.opencv_core.Rect;
import sg.qr.kiarelemb.MainWindow;
import sg.qr.kiarelemb.component.ManualScoreReviewDialog;
import sg.qr.kiarelemb.data.Utils;
import sg.qr.kiarelemb.grading.ProjectManualReviewPanel;
import sg.qr.kiarelemb.grading.model.Project;
import sg.qr.kiarelemb.grading.model.Template;
import sg.qr.kiarelemb.grading.pipeline.TemplateProcessor;
import sg.qr.kiarelemb.grading.qsmethod.QuestionScoring;
import sg.qr.kiarelemb.grading.qsmethod.ScaleScoreConfigDialog;
import sg.qr.kiarelemb.grading.qsmethod.Section;
import sg.qr.kiarelemb.menu.data.EnglishScoreInput;
import swing.qr.kiarelemb.basic.*;
import swing.qr.kiarelemb.theme.QRColorsAndFonts;
import swing.qr.kiarelemb.utils.PicturePanel;
import swing.qr.kiarelemb.window.basic.QRDialog;
import swing.qr.kiarelemb.window.enhance.QROpinionDialog;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.List;

/**
 * Final grading and score export dialog.
 */
public class ProjectEnd extends QRPanel {
	public static ProjectEnd INSTANCE;

	private static final String MODE_NORMAL = "普通算分";
	private static final String MODE_SCALE = "尺度算分";
	public static final int FIELD_HEIGHT = 28;

	private final Project project;
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
	private QuestionScoring.Config scaleConfig = QuestionScoring.Config.defaults();
	private ScorePlan scorePlan = ScorePlan.empty();
	private List<ScoreResult> scoreResults = new ArrayList<>();
	private List<String> exportRows = new ArrayList<>();
	private QuestionScoring.ScaleScoreReport lastScaleReport;
	private boolean lastResultScaleMode = false;

	public ProjectEnd(Project project) {
		this.project = project;
		INSTANCE = this;
		EndProject.END_PROJECT.setEnabled(false);
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
		QRRoundButton manualScoreButton = new QRRoundButton("录入人工分");
		manualScoreButton.setPreferredSize(new Dimension(110, 30));
		manualScoreButton.addClickAction(this::editManualScores);
		buttonPanel.add(manualScoreButton);
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
		summaryLabel.setText("已校对 " + project.recognizedAnswers().size() + " 份答卷。");
		panel.add(summaryLabel, BorderLayout.CENTER);

		QRPanel buttonPanel = new QRPanel(false, new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttonPanel.add(EndProject.END_PROJECT);
		buttonPanel.add(BackToReviewButton.BACK_TO_REVIEW_BUTTON);
		buttonPanel.add(ExportScoresButton.EXPORT_SCORES_BUTTON);
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
			Template template = TemplateProcessor.load(new File(project.templateFilePath()));
			return template.defaultScoreRules() == null ? "" : template.defaultScoreRules().trim();
		} catch (IOException ex) {
			return "";
		}
	}

	private void saveDefaultRulesToTemplate() {
		try {
			File templateFile = new File(project.templateFilePath());
			Template template = TemplateProcessor.load(templateFile);
			Template updated = new Template(
					template.name(),
					template.pictureFile(),
					template.answerSheet(),
					template.examRegionRect(),
					template.choiceRegionRect(),
					template.fillBlankRegionRect(),
					ruleTextPane.getText(),
					template.pageCount(),
					template.subjectiveRegions(),
					template.pictureFiles());
			TemplateProcessor.save(updated, templateFile);
		} catch (IOException ex) {
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, "保存模板计分规则失败：\n" + ex.getMessage());
		}
	}

	private void previewScores(ActionEvent event) {
		try {
			this.scorePlan = MODE_SCALE.equals(scoreModeBox.getSelectedItem())
					? buildScaleScorePlan()
					: parseNormalScorePlan(ruleTextPane.getText(), scoreRuleQuestionCount());
			saveDefaultRulesToTemplate();
			renderQuestionScores(this.scorePlan);
			renderResultColumns(this.scorePlan.sectionNames());
			summaryLabel.setText("分值预览完成，满分 " + Utils.formatScore(this.scorePlan.totalScore()) + "。");
		} catch (ScoreRuleException ex) {
			showScoreRuleError(ex);
		} catch (IllegalArgumentException ex) {
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, ex.getMessage());
		}
	}

	void calculateScores(ActionEvent event) {
		try {
			if (MODE_SCALE.equals(scoreModeBox.getSelectedItem())) {
				QuestionScoring.ScaleScoreReport report = showScaleScoreDialog();
				if (report == null) {
					return;
				}
				this.scorePlan = buildScaleScorePlan(report);
				this.scoreResults = applyManualScores(toScoreResults(report.studentScores(), this.scorePlan), this.scorePlan);
				this.lastScaleReport = report;
				this.lastResultScaleMode = true;
			} else {
				this.scorePlan = parseNormalScorePlan(ruleTextPane.getText(), scoreRuleQuestionCount());
				this.scoreResults = applyManualScores(enrichStudentInfo(Utils.calculateNormalScores(project, this.scorePlan)), this.scorePlan);
				this.lastScaleReport = null;
				this.lastResultScaleMode = false;
			}
			saveDefaultRulesToTemplate();
			this.exportRows = Utils.buildExportRows(project);
			renderQuestionScores(this.scorePlan);
			renderResultColumns(this.scorePlan.sectionNames());
			renderScoreResults(this.scoreResults, this.scorePlan.sectionNames());
			EndProject.END_PROJECT.setEnabled(true);
			summaryLabel.setText("成绩计算完成：" + scoreResults.size() + " 人，导出行 " + exportRows.size() + " 条。");
		} catch (ScoreRuleException ex) {
			showScoreRuleError(ex);
		} catch (IllegalArgumentException ex) {
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, ex.getMessage());
		}
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

	private void editManualScores(ActionEvent event) {
		try {
			ScorePlan plan = parseNormalScorePlan(ruleTextPane.getText(), scoreRuleQuestionCount());
			List<ScoreRule> manualRules = manualScoreRules(plan);
			if (manualRules.isEmpty()) {
				QROpinionDialog.messageTellShow(MainWindow.INSTANCE, "当前计分规则中没有人工评分题。\n人工评分题号应写在机判题号之后。");
				return;
			}
			new ManualScoreReviewDialog(MainWindow.INSTANCE, "录入人工分",
					manualScoreEntries(),
					manualRules.stream()
							.map(rule -> new ManualScoreReviewDialog.ScoreItem(rule.questionType(), rule.totalScore()))
							.toList(),
					manualRegion(),
					new ManualScoreReviewDialog.ScoreStore() {
						@Override
						public Map<String, String> load(ManualScoreReviewDialog.Entry entry) {
							return project.manualScoresFor(entry.examineeId());
						}

						@Override
						public void save(ManualScoreReviewDialog.Entry entry, Map<String, String> scores) {
							for (Map.Entry<String, String> score : scores.entrySet()) {
								project.putManualScore(entry.examineeId(), score.getKey(), score.getValue());
							}
							project.write();
						}
					}).setVisible(true);
		} catch (ScoreRuleException ex) {
			showScoreRuleError(ex);
		} catch (IllegalArgumentException ex) {
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, ex.getMessage());
		}
	}

	private List<ManualScoreReviewDialog.Entry> manualScoreEntries() {
		if (project.answerFiles() == null) {
			return List.of();
		}
		List<ManualScoreReviewDialog.Entry> entries = new ArrayList<>();
		Map<String, String> names = project.studentNamesByExamId();
		for (File answerFile : project.answerFiles()) {
			Project.ReviewedAnswer reviewedAnswer = project.reviewedAnswerFor(answerFile);
			if (reviewedAnswer != null && reviewedAnswer.examineeId() != null && !reviewedAnswer.examineeId().isBlank()) {
				entries.add(new ManualScoreReviewDialog.Entry(manualScoreImageFile(answerFile),
						reviewedAnswer.examineeId(),
						names.getOrDefault(reviewedAnswer.examineeId(), "")));
			}
		}
		return entries;
	}

	private File manualScoreImageFile(File answerFile) {
		if (manualRegionPageIndex() <= 0) {
			return answerFile;
		}
		File backFile = project.answerBackFileFor(answerFile);
		return backFile == null ? answerFile : backFile;
	}

	private int manualRegionPageIndex() {
		try {
			Template template = TemplateProcessor.load(new File(project.templateFilePath()));
			for (sg.qr.kiarelemb.grading.model.SubjectiveRegion region : template.subjectiveRegions()) {
				if (region.mode() == sg.qr.kiarelemb.grading.model.SubjectiveRegion.GradingMode.MANUAL
					|| region.mode() == sg.qr.kiarelemb.grading.model.SubjectiveRegion.GradingMode.MIXED) {
					return region.pageIndex();
				}
			}
		} catch (IOException ignored) {
		}
		return 0;
	}

	private Rect manualRegion() {
		try {
			Template template = TemplateProcessor.load(new File(project.templateFilePath()));
			return manualRegion(template);
		} catch (IOException ex) {
			return null;
		}
	}

	private Rect manualRegion(Template template) {
		List<Rect> regions = new ArrayList<>();
		for (sg.qr.kiarelemb.grading.model.SubjectiveRegion region : template.subjectiveRegions()) {
			if (region.mode() == sg.qr.kiarelemb.grading.model.SubjectiveRegion.GradingMode.MANUAL
				|| region.mode() == sg.qr.kiarelemb.grading.model.SubjectiveRegion.GradingMode.MIXED) {
				regions.add(region.region());
			}
		}
		if (regions.isEmpty() && template.fillBlankRegionRect() != null) {
			regions.add(template.fillBlankRegionRect());
		}
		return boundingRect(regions);
	}

	private Rect boundingRect(List<Rect> regions) {
		if (regions == null || regions.isEmpty()) {
			return null;
		}
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (Rect rect : regions) {
			minX = Math.min(minX, rect.x());
			minY = Math.min(minY, rect.y());
			maxX = Math.max(maxX, rect.x() + rect.width());
			maxY = Math.max(maxY, rect.y() + rect.height());
		}
		return new Rect(minX, minY, maxX - minX, maxY - minY);
	}

	private final class ManualScoreDialog extends QRDialog {
		private final List<ScoreRule> manualRules;
		private final DefaultTableModel model;
		private final List<ManualScoreEntry> entries = new ArrayList<>();
		private final Map<String, QRTextField> scoreFields = new LinkedHashMap<>();
		private final PicturePanel picturePanel = new PicturePanel();
		private final QRLabel progressLabel = new QRLabel();
		private int index;

		private ManualScoreDialog(List<ScoreRule> manualRules) {
			super(MainWindow.INSTANCE);
			this.manualRules = manualRules;
			setTitle("录入人工分");
			setSize(760, 560);
			setLocationRelativeTo(MainWindow.INSTANCE);
			setParentWindowNotFollowMove();
			mainPanel.setLayout(new BorderLayout(8, 8));

			Vector<String> columns = new Vector<>();
			columns.add("准考证号");
			columns.add("姓名");
			for (ScoreRule rule : manualRules) {
				columns.add(rule.questionType() + "(" + Utils.formatScore(rule.totalScore()) + ")");
			}
			model = new DefaultTableModel(columns, 0) {
				@Override
				public boolean isCellEditable(int row, int column) {
					return column >= 2;
				}
			};
			fillRows();
			QRTable table = new QRTable(model);
			table.setRowHeight(26);
			mainPanel.add(table.addScrollPane(), BorderLayout.CENTER);

			QRPanel bottom = new QRPanel(false, new FlowLayout(FlowLayout.RIGHT, 8, 0));
			QRRoundButton cancelButton = new QRRoundButton("取消");
			cancelButton.setPreferredSize(80, 30);
			cancelButton.addClickAction(e -> dispose());
			QRRoundButton saveButton = new QRRoundButton("保存");
			saveButton.setPreferredSize(80, 30);
			saveButton.addClickAction(e -> save());
			bottom.add(cancelButton);
			bottom.add(saveButton);
			mainPanel.add(bottom, BorderLayout.SOUTH);
		}

		private void fillRows() {
			Map<String, String> names = project.studentNamesByExamId();
			for (String examineeId : project.combinedAnswersByExamId().keySet()) {
				Vector<Object> row = new Vector<>();
				row.add(examineeId);
				row.add(names.getOrDefault(examineeId, ""));
				Map<String, String> scores = project.manualScoresFor(examineeId);
				for (ScoreRule rule : manualRules) {
					row.add(scores.getOrDefault(rule.questionType(), ""));
				}
				model.addRow(row);
			}
		}

		private void save() {
			for (int row = 0; row < model.getRowCount(); row++) {
				String examineeId = String.valueOf(model.getValueAt(row, 0)).trim();
				for (int i = 0; i < manualRules.size(); i++) {
					ScoreRule rule = manualRules.get(i);
					Object value = model.getValueAt(row, i + 2);
					String text = value == null ? "" : String.valueOf(value).trim();
					if (!text.isEmpty()) {
						BigDecimal score;
						try {
							score = new BigDecimal(text).setScale(2, RoundingMode.HALF_UP);
						} catch (NumberFormatException ex) {
							QROpinionDialog.messageErrShow(this, "第 " + (row + 1) + " 行 " + rule.questionType() + " 分数不是有效数字。");
							return;
						}
						if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(rule.totalScore()) > 0) {
							QROpinionDialog.messageErrShow(this, "第 " + (row + 1) + " 行 " + rule.questionType()
																 + " 分数应在 0-" + Utils.formatScore(rule.totalScore()) + " 之间。");
							return;
						}
						text = score.stripTrailingZeros().toPlainString();
					}
					project.putManualScore(examineeId, rule.questionType(), text);
				}
			}
			project.write();
			QROpinionDialog.messageTellShow(this, "人工分已保存。");
			dispose();
		}
	}

	private final class ManualScoreImageDialog extends QRDialog {
		private final List<ScoreRule> manualRules;
		private final List<ManualScoreEntry> entries = new ArrayList<>();
		private final Map<String, QRTextField> scoreFields = new LinkedHashMap<>();
		private final PicturePanel picturePanel = new PicturePanel();
		private final QRLabel progressLabel = new QRLabel();
		private int index;

		private ManualScoreImageDialog(List<ScoreRule> manualRules) {
			super(MainWindow.INSTANCE);
			this.manualRules = manualRules;
			setTitle("录入人工分");
			setSize(900, 680);
			setLocationRelativeTo(MainWindow.INSTANCE);
			setParentWindowNotFollowMove();
			mainPanel.setLayout(new BorderLayout(8, 8));

			loadEntries();
			picturePanel.setZoomRange(0.25, 5.0);
			mainPanel.add(progressLabel, BorderLayout.NORTH);
			mainPanel.add(picturePanel, BorderLayout.CENTER);
			mainPanel.add(buildScorePanel(), BorderLayout.SOUTH);
			loadCurrent();
		}

		private void loadEntries() {
			if (project.answerFiles() == null) {
				return;
			}
			for (File answerFile : project.answerFiles()) {
				Project.ReviewedAnswer reviewedAnswer = project.reviewedAnswerFor(answerFile);
				if (reviewedAnswer != null && reviewedAnswer.examineeId() != null && !reviewedAnswer.examineeId().isBlank()) {
					entries.add(new ManualScoreEntry(manualScoreImageFile(answerFile), reviewedAnswer.examineeId()));
				}
			}
		}

		private Component buildScorePanel() {
			QRPanel wrapper = new QRPanel(false, new BorderLayout(8, 8));
			wrapper.setBorder(new LineBorder(QRColorsAndFonts.FRAME_COLOR_BACK, 10));
			QRPanel fields = new QRPanel(false, new FlowLayout(FlowLayout.LEFT, 10, 0));
			for (ScoreRule rule : manualRules) {
				QRLabel label = new QRLabel(rule.questionType() + "/" + Utils.formatScore(rule.totalScore()));
				label.setPreferredSize(new Dimension(95, 30));
				QRTextField field = new QRTextField();
				field.setPreferredSize(new Dimension(90, 30));
				field.addActionListener(event -> next());
				scoreFields.put(rule.questionType(), field);
				fields.add(label);
				fields.add(field);
			}
			wrapper.add(fields, BorderLayout.CENTER);

			QRPanel buttons = new QRPanel(false, new FlowLayout(FlowLayout.RIGHT, 8, 0));
			QRRoundButton previousButton = new QRRoundButton("上一张");
			previousButton.setPreferredSize(80, 30);
			previousButton.addClickAction(event -> previous());
			QRRoundButton nextButton = new QRRoundButton("下一张");
			nextButton.setPreferredSize(80, 30);
			nextButton.addClickAction(event -> next());
			QRRoundButton closeButton = new QRRoundButton("保存退出");
			closeButton.setPreferredSize(100, 30);
			closeButton.addClickAction(event -> {
				if (saveCurrent()) {
					dispose();
				}
			});
			buttons.add(previousButton);
			buttons.add(nextButton);
			buttons.add(closeButton);
			wrapper.add(buttons, BorderLayout.EAST);
			return wrapper;
		}

		private void loadCurrent() {
			if (entries.isEmpty()) {
				progressLabel.setText("没有可录入人工分的已校对答卷。");
				return;
			}
			index = Math.max(0, Math.min(index, entries.size() - 1));
			ManualScoreEntry entry = entries.get(index);
			String name = project.studentNamesByExamId().getOrDefault(entry.examineeId(), "");
			progressLabel.setText("当前：" + entry.answerFile().getName() + "，准考证号：" + entry.examineeId()
								  + (name.isBlank() ? "" : "，姓名：" + name) + "，进度：" + (index + 1) + " / " + entries.size());
			try {
				BufferedImage image = ImageIO.read(entry.answerFile());
				BufferedImage crop = cropManualRegion(image);
				picturePanel.setImage(crop, new Dimension(crop.getWidth(), crop.getHeight()));
				picturePanel.resetView();
			} catch (IOException ex) {
				QROpinionDialog.messageErrShow(this, "读取答卷图片失败：\n" + ex.getMessage());
			}
			Map<String, String> savedScores = project.manualScoresFor(entry.examineeId());
			for (Map.Entry<String, QRTextField> fieldEntry : scoreFields.entrySet()) {
				fieldEntry.getValue().setText(savedScores.getOrDefault(fieldEntry.getKey(), ""));
			}
			if (!scoreFields.isEmpty()) {
				scoreFields.values().iterator().next().requestFocusInWindow();
			}
		}

		private BufferedImage cropManualRegion(BufferedImage image) {
			Rect rect = manualRegion();
			if (rect == null) {
				return image;
			}
			int x = Math.max(0, Math.min(rect.x(), image.getWidth() - 1));
			int y = Math.max(0, Math.min(rect.y(), image.getHeight() - 1));
			int right = Math.max(x + 1, Math.min(rect.x() + rect.width(), image.getWidth()));
			int bottom = Math.max(y + 1, Math.min(rect.y() + rect.height(), image.getHeight()));
			return image.getSubimage(x, y, right - x, bottom - y);
		}

		private Rect manualRegion() {
			try {
				Template template = TemplateProcessor.load(new File(project.templateFilePath()));
				return ProjectEnd.this.manualRegion(template);
			} catch (IOException ex) {
				return null;
			}
		}

		private void previous() {
			if (saveCurrent() && index > 0) {
				index--;
				loadCurrent();
			}
		}

		private void next() {
			if (!saveCurrent()) {
				return;
			}
			if (index < entries.size() - 1) {
				index++;
				loadCurrent();
			} else {
				project.write();
				QROpinionDialog.messageTellShow(this, "人工分已保存。");
			}
		}

		private boolean saveCurrent() {
			if (entries.isEmpty()) {
				return true;
			}
			ManualScoreEntry entry = entries.get(index);
			for (ScoreRule rule : manualRules) {
				QRTextField field = scoreFields.get(rule.questionType());
				String text = field == null || field.getText() == null ? "" : field.getText().trim();
				if (!text.isEmpty()) {
					BigDecimal score;
					try {
						score = new BigDecimal(text).setScale(2, RoundingMode.HALF_UP);
					} catch (NumberFormatException ex) {
						QROpinionDialog.messageErrShow(this, rule.questionType() + " 分数不是有效数字。");
						return false;
					}
					if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(rule.totalScore()) > 0) {
						QROpinionDialog.messageErrShow(this, rule.questionType() + " 分数应在 0-"
															 + Utils.formatScore(rule.totalScore()) + " 之间。");
						return false;
					}
					text = score.stripTrailingZeros().toPlainString();
				}
				project.putManualScore(entry.examineeId(), rule.questionType(), text);
			}
			project.write();
			return true;
		}
	}

	private record ManualScoreEntry(File answerFile, String examineeId) {
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

	private ScorePlan buildScaleScorePlan() {
		return buildScaleScorePlan(calculateScaleScoreReport());
	}

	private QuestionScoring.ScaleScoreReport calculateScaleScoreReport() {
		return calculateScaleScoreReport(scaleConfig);
	}

	public QuestionScoring.ScaleScoreReport calculateScaleScoreReport(QuestionScoring.Config config) {
		return new QuestionScoring(
				project.standardAnswers(),
				project.combinedAnswersByExamId(),
				parseScaleSections(ruleTextPane.getText(), standardAnswerCount()),
				entranceEnglishScores,
				config
		).calculateScaleScores();
	}

	private QuestionScoring.ScaleScoreReport showScaleScoreDialog() {
		ScaleScoreConfigDialog dialog = new ScaleScoreConfigDialog(this, scaleConfig);
		dialog.setVisible(true);
		if (!dialog.confirmed()) {
			return null;
		}
		scaleConfig = dialog.config();
		return dialog.report();
	}

	private ScorePlan buildScaleScorePlan(QuestionScoring.ScaleScoreReport report) {
		Map<Integer, BigDecimal> questionScores = new LinkedHashMap<>(report.questionScores());
		Map<Integer, String> sections = new LinkedHashMap<>();
		List<String> sectionNames = new ArrayList<>();
		for (ScoreRule rule : parseDisplayScoreRules(ruleTextPane.getText(), scoreRuleQuestionCount())) {
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
		return new ScorePlan(questionScores, sections, sectionNames);
	}

	private List<ScoreResult> toScoreResults(List<QuestionScoring.ScaleStudentScore> scaleScores, ScorePlan displayScorePlan) {
		List<ScoreResult> results = new ArrayList<>();
		Map<String, String> answersByExamId = project.combinedAnswersByExamId();
		String[] standardAnswers = project.standardAnswers() == null ? new String[0] : project.standardAnswers();
		for (QuestionScoring.ScaleStudentScore scaleScore : scaleScores) {
			String name = project.studentNamesByExamId().getOrDefault(scaleScore.examineeId(), scaleScore.name());
			BigDecimal entrance = scaleScore.entranceEnglishScore();
			Map<String, BigDecimal> sectionScores = calculateSectionScores(
					answersByExamId.get(scaleScore.examineeId()),
					standardAnswers,
					displayScorePlan);
			results.add(new ScoreResult(
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

	private Map<String, BigDecimal> calculateSectionScores(String answerLine, String[] standardAnswers, ScorePlan scorePlan) {
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

	private List<ScoreResult> enrichStudentInfo(List<ScoreResult> rawResults) {
		List<ScoreResult> results = new ArrayList<>();
		for (ScoreResult result : rawResults) {
			String name = project.studentNamesByExamId().getOrDefault(result.examineeId(), result.name());
			BigDecimal entrance = entranceEnglishScores.get(result.examineeId());
			results.add(new ScoreResult(
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

	private List<Section> parseScaleSections(String text, int questionCount) {
		List<Section> sections = new ArrayList<>();
		for (ScoreRule rule : parseScoreRules(text, scoreRuleQuestionCount())) {
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
			sections.add(new Section(rule.questionType(), start, end, machineTotal.doubleValue(), questionIndices));
		}
		return sections;
	}

	public int standardAnswerCount() {
		return project.standardAnswers() == null ? 0 : project.standardAnswers().length;
	}

	int scoreRuleQuestionCount() {
		int count = standardAnswerCount();
		String text = ruleTextPane.getText() == null ? "" : ruleTextPane.getText();
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

	private List<ScoreRule> manualScoreRules(ScorePlan scorePlan) {
		int machineCount = standardAnswerCount();
		List<ScoreRule> rules = new ArrayList<>();
		for (ScoreRule rule : parseDisplayScoreRules(ruleTextPane.getText(), scoreRuleQuestionCount())) {
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

	private List<ScoreResult> applyManualScores(List<ScoreResult> baseResults, ScorePlan scorePlan) {
		List<ScoreRule> manualRules = manualScoreRules(scorePlan);
		if (manualRules.isEmpty()) {
			return baseResults;
		}
		Template template = manualScoreTemplate();
		List<ScoreResult> results = new ArrayList<>();
		for (ScoreResult result : baseResults) {
			Map<String, BigDecimal> sectionScores = new LinkedHashMap<>(result.sectionScores());
			BigDecimal total = result.earnedScore();
			Map<String, String> savedScores = project.manualScoresFor(result.examineeId());
			for (ScoreRule rule : manualRules) {
				String savedScore = ProjectManualReviewPanel.scoreForRule(savedScores, rule.questionType(), rule.questionNumbers(), template);
				BigDecimal score = parseManualScore(savedScore, rule.totalScore());
				sectionScores.put(rule.questionType(), score);
				total = total.add(score);
			}
			results.add(new ScoreResult(0, result.examineeId(), result.name(), result.entranceEnglishScore(),
					sectionScores, total, result.entranceEnglishScore() == null ? null : total.subtract(result.entranceEnglishScore())));
		}
		results.sort(Comparator.comparing(ScoreResult::earnedScore).reversed().thenComparing(ScoreResult::examineeId));
		return Utils.rankedResults(results);
	}

	private Template manualScoreTemplate() {
		try {
			return TemplateProcessor.load(new File(project.templateFilePath()));
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

	private void renderQuestionScores(ScorePlan scorePlan) {
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

	private void renderScoreResults(List<ScoreResult> results, List<String> sectionNames) {
		resultTableModel.setRowCount(0);
		for (ScoreResult result : results) {
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

	public List<ScoreResult> scoreResults() {
		return Collections.unmodifiableList(scoreResults);
	}

	public List<String> exportRows() {
		return Collections.unmodifiableList(exportRows);
	}

	void exportScores(ActionEvent event) {
		ProjectEndExporter.EXPORTER.export(this);
	}

	Project project() {
		return project;
	}

	DefaultTableModel resultTableModel() {
		return resultTableModel;
	}

	ScorePlan scorePlan() {
		return scorePlan;
	}

	QuestionScoring.ScaleScoreReport lastScaleReport() {
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

	public ScorePlan parseNormalScorePlan(String text, int questionCount) {
		if (questionCount <= 0) {
			return ScorePlan.empty();
		}

		Map<Integer, BigDecimal> scores = new LinkedHashMap<>();
		Map<Integer, String> sections = new LinkedHashMap<>();
		List<String> sectionNames = new ArrayList<>();
		for (int i = 1; i <= questionCount; i++) {
			scores.put(i, BigDecimal.ZERO);
			sections.put(i, "");
		}

		for (ScoreRule rule : parseScoreRules(text, questionCount)) {
			if (!sectionNames.contains(rule.questionType())) {
				sectionNames.add(rule.questionType());
			}
			BigDecimal perQuestionScore = rule.totalScore().divide(BigDecimal.valueOf(rule.questionNumbers().size()), 8, RoundingMode.HALF_UP);
			for (Integer questionNumber : rule.questionNumbers()) {
				scores.put(questionNumber, perQuestionScore);
				sections.put(questionNumber, rule.questionType());
			}
		}
		return new ScorePlan(scores, sections, sectionNames);
	}

	public List<ScoreRule> parseScoreRules(String text, int questionCount) {
		if (questionCount <= 0) {
			return List.of();
		}
		String value = text == null ? "" : text.trim();
		if (value.isEmpty()) {
			throw new IllegalArgumentException("请填写计分规则。");
		}

		List<RawScoreRule> pending = new ArrayList<>();
		List<ScoreRule> rules = new ArrayList<>();
		String[] lines = value.split("\\R");
		for (int i = 0; i < lines.length; i++) {
			String line = Utils.stripComment(lines[i]).trim();
			if (line.isBlank()) {
				continue;
			}
			RawScoreRule rawRule = RawScoreRule.parse(line, i + 1, questionCount);
			if (rawRule.totalScore() == null) {
				pending.add(rawRule);
				continue;
			}
			if (pending.isEmpty()) {
				rules.add(rawRule.toScoreRule());
			} else {
				pending.add(rawRule);
				rules.add(ScoreRule.merge(pending));
				pending.clear();
			}
		}
		if (!pending.isEmpty()) {
			throw new ScoreRuleException(pending.get(0).lineNumber(), "第 " + pending.get(0).lineNumber() + " 行及其后续合并计分规则缺少总分。");
		}
		return rules;
	}

	List<ScoreRule> parseDisplayScoreRules(String text, int questionCount) {
		if (questionCount <= 0) {
			return List.of();
		}
		String value = text == null ? "" : text.trim();
		if (value.isEmpty()) {
			throw new IllegalArgumentException("请填写计分规则。");
		}

		List<RawScoreRule> pending = new ArrayList<>();
		List<ScoreRule> rules = new ArrayList<>();
		String[] lines = value.split("\\R");
		for (int i = 0; i < lines.length; i++) {
			String line = Utils.stripComment(lines[i]).trim();
			if (line.isBlank()) {
				continue;
			}
			RawScoreRule rawRule = RawScoreRule.parse(line, i + 1, questionCount);
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
				for (RawScoreRule rule : pending) {
					BigDecimal totalScore = perQuestionScore.multiply(BigDecimal.valueOf(rule.questionNumbers().size()));
					rules.add(new ScoreRule(rule.questionType(), rule.questionNumbers(), totalScore));
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
}
