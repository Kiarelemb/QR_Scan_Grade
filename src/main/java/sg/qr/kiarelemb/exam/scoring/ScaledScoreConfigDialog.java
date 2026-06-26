package sg.qr.kiarelemb.exam.scoring;

import sg.qr.kiarelemb.MainWindow;
import sg.qr.kiarelemb.data.Utils;
import sg.qr.kiarelemb.exam.results.ResultsPanel;
import sg.qr.kiarelemb.exam.results.ScorePolicy;
import swing.qr.kiarelemb.QRSwing;
import swing.qr.kiarelemb.basic.*;
import swing.qr.kiarelemb.theme.QRColorsAndFonts;
import swing.qr.kiarelemb.window.basic.QRDialog;
import swing.qr.kiarelemb.window.enhance.QROpinionDialog;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.List;

/**
 * @author Kiarelemb
 * @projectName QR_Scan_Grade
 * @className ScaledScoreConfigDialog
 * @description TODO
 * @create 2026/6/10 16:13
 */
public class ScaledScoreConfigDialog extends QRDialog {
	private final ResultsPanel projectEnd;
	private final QRComboBox weightFunctionBox = new QRComboBox("INVERSE", "NEG_LOG", "LOGIT_ABS");
	private final QRTextField logitPowerField = new QRTextField();
	private final QRTextField centerPField = new QRTextField();
	private final QRTextField minPField = new QRTextField();
	private final QRTextField maxPField = new QRTextField();
	private final QRTextField minWeightField = new QRTextField();
	private final QRTextField maxWeightField = new QRTextField();
	private final QRTextField epsilonField = new QRTextField();
	private final QRTextPane scaleHelpTextPane = new QRTextPane();
	private final DefaultTableModel previewModel = new DefaultTableModel(new String[]{"题号", "题型", "正确率", "赋分", "权重", "大题合计"}, 0) {
		@Override
		public boolean isCellEditable(int row, int column) {
			return false;
		}
	};
	private final QRTable previewTable = new QRTable(previewModel);
	private final DefaultTableModel studentPreviewModel = new DefaultTableModel() {
		@Override
		public boolean isCellEditable(int row, int column) {
			return false;
		}
	};
	private final QRTable studentPreviewTable = new QRTable(studentPreviewModel);
	private QuestionScorePolicy.ScaleScoreReport report;
	private QuestionScorePolicy.Config config;
	private boolean confirmed;

	public ScaledScoreConfigDialog(ResultsPanel projectEnd, QuestionScorePolicy.Config config) {
		super(MainWindow.INSTANCE);
		this.projectEnd = projectEnd;
		this.config = config;
		initScaleDialog();
		fillConfig(config);
		refreshPreview();
	}

	private void initScaleDialog() {
		setTitle("尺度算分参数");
		setTitlePlace(CENTER);
		setSize(1000, 700);
		setLocationRelativeTo(MainWindow.INSTANCE);
		setResizable(true);
		setParentWindowNotFollowMove();

		mainPanel.setLayout(new BorderLayout(8, 8));
		QRSplitPane splitPane = new QRSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		splitPane.setResizeWeight(0.28);
		splitPane.setDividerLocation(260);
		splitPane.setLeftComponent(buildScaleConfigPanel());
		splitPane.setRightComponent(buildScalePreviewPanel());
		mainPanel.add(splitPane, BorderLayout.CENTER);
		mainPanel.add(buildScaleButtonPanel(), BorderLayout.SOUTH);
	}

	private Component buildScaleConfigPanel() {
		QRPanel panel = new QRPanel(false, new BorderLayout(0, 8));
		panel.setBorder(new LineBorder(QRColorsAndFonts.FRAME_COLOR_BACK, 8));
		QRPanel fieldsPanel = new QRPanel(false, new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(4, 4, 4, 4);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 0;
		addConfigRow(fieldsPanel, gbc, "算法", weightFunctionBox);
		addConfigRow(fieldsPanel, gbc, "logit.power", logitPowerField);
		addConfigRow(fieldsPanel, gbc, "center.p", centerPField);
		addConfigRow(fieldsPanel, gbc, "min.p", minPField);
		addConfigRow(fieldsPanel, gbc, "max.p", maxPField);
		addConfigRow(fieldsPanel, gbc, "min.weight", minWeightField);
		addConfigRow(fieldsPanel, gbc, "max.weight", maxWeightField);
		addConfigRow(fieldsPanel, gbc, "epsilon", epsilonField);
		panel.add(fieldsPanel, BorderLayout.NORTH);

		scaleHelpTextPane.setEditableFalseButCursorEdit();
		scaleHelpTextPane.setFont(QRColorsAndFonts.createFont(18));
		scaleHelpTextPane.setLineWrap(true);
		scaleHelpText();
		panel.add(scaleHelpTextPane.addInternalScrollPane(), BorderLayout.CENTER);
		return panel;
	}

	private void scaleHelpText() {
		String text = """
				尺度算法说明

				用途：

				尺度算分根据每道题的全班正确率调整小题分值。它不是简单平均分配满分，而是先把每道题换算成权重，再在每个计分大题内部按权重分配该大题总分。这样可以让试卷中不同难度的题对成绩产生不同影响。

				函数/算法：

				INVERSE：正确率越低，分值越高；正确率越高，分值越低。适合强调难题。

				NEG_LOG：也是难题权重更高，但比 INVERSE 更平滑。

				LOGIT_ABS：日常推荐用此算法。它以 center.p 为最低点，正确率越远离 center.p，权重越高。适合把"过易题"和"过难题"都拉开，同时让接近中心正确率的题分值较低。

				参数：

				logit.power：控制 LOGIT_ABS 的曲线强度。越大，远离 center.p 的题权重增长越明显，题目分差越大；越小，分差越平缓。使用其他函数时，该参数不作用。

				center.p：LOGIT_ABS 的中心正确率，也是权重最低点。默认 0.5。若希望以 40% 正确率为最低点，可设为 0.4。使用其他函数时，该参数不作用。

				min.p / max.p：正确率裁剪范围。默认 0.1 到 0.9，可避免 0% 或 100% 这类极端值把权重推得过分。

				min.weight / max.weight：权重裁剪范围。用于限制最终权重的最低和最高值，避免小题赋分过小或过大。

				epsilon：极小保护值，防止对数、除法出现 0。一般保持默认即可。

				算分流程：
				1. 统计每题正确率 p。
				2. 将 p 限制在 min.p 到 max.p 之间，避免极端正确率导致权重过大或过小。
				3. 按所选函数把 p 转换为权重。
				4. 将权重限制在 min.weight 到 max.weight 之间。
				5. 在每个计分大题内按权重比例分配满分。

				调参建议

				若想让难题分越高，优先使用 NEG_LOG 或 INVERSE。

				若想让难题和简单题都能被赋予更高的分，使用 LOGIT_ABS。

				若小题分差过大，降低 logit.power 或 max.weight。

				若小题分差过小，提高 logit.power 或 max.weight。

				""";
		scaleHelpTextPane.setText(text);
		SimpleAttributeSet attrs = new SimpleAttributeSet();
		StyleConstants.setForeground(attrs, QRColorsAndFonts.DEFAULT_COLOR_LABEL);
		StyleConstants.setFontSize(attrs, 24);
		StyleConstants.setAlignment(attrs, StyleConstants.ALIGN_CENTER);
		StyleConstants.setFontFamily(attrs, QRSwing.globalFont.getFamily());
		StyleConstants.setBold(attrs, true);

		StyledDocument doc = scaleHelpTextPane.getStyledDocument();
		doc.setParagraphAttributes(0, 6, attrs, false);

		SimpleAttributeSet strong = new SimpleAttributeSet();
		StyleConstants.setFontFamily(strong, QRSwing.globalFont.getFamily());
		StyleConstants.setBold(strong, true);
		StyleConstants.setUnderline(strong, true);
		StyleConstants.setForeground(strong, QRColorsAndFonts.CARET_COLOR);
		scaleHelpTextPane.changeTextStyle("根据每道题的全班正确率", strong);
		scaleHelpTextPane.changeTextStyle("正确率越低，分值越高", strong);
		scaleHelpTextPane.changeTextStyle("难题权重更高", strong);
		scaleHelpTextPane.changeTextStyle("更平滑", strong);
		scaleHelpTextPane.changeTextStyle("日常推荐用此算法", strong);
		scaleHelpTextPane.changeTextStyle("权重最低点", strong);
		scaleHelpTextPane.changeTextStyle("限制最终权重的最低和最高值", strong);
		doc.setParagraphAttributes(scaleHelpTextPane.getText().indexOf("调参建议"), 4, attrs, false);
		scaleHelpTextPane.changeTextStyle("想让难题分越高", strong);
		scaleHelpTextPane.changeTextStyle("想让难题和简单题都能被赋予更高的分", strong);
	}

	private void addConfigRow(QRPanel panel, GridBagConstraints gbc, String label, Component field) {
		gbc.gridx = 0;
		gbc.weightx = 0;
		QRLabel qrLabel = new QRLabel(label);
		qrLabel.setPreferredSize(new Dimension(95, ResultsPanel.FIELD_HEIGHT));
		panel.add(qrLabel, gbc);
		gbc.gridx = 1;
		gbc.weightx = 1;
		field.setPreferredSize(new Dimension(120, ResultsPanel.FIELD_HEIGHT));
		panel.add(field, gbc);
		gbc.gridy++;
	}

	private Component buildScalePreviewPanel() {
		previewTable.setRowHeight(24);
		previewTable.getTableHeader().setFont(QRColorsAndFonts.createFont(14));
		previewTable.setFont(QRColorsAndFonts.createFont(14));
		studentPreviewTable.setRowHeight(24);
		studentPreviewTable.getTableHeader().setFont(QRColorsAndFonts.createFont(14));
		studentPreviewTable.setFont(QRColorsAndFonts.createFont(14));

		QRSplitPane splitPane = new QRSplitPane(JSplitPane.VERTICAL_SPLIT);
		splitPane.setResizeWeight(0.48);
		splitPane.setTopComponent(previewTable.addScrollPane());
		splitPane.setBottomComponent(studentPreviewTable.addScrollPane());
		return splitPane;
	}

	private Component buildScaleButtonPanel() {
		QRPanel panel = new QRPanel(false, new FlowLayout(FlowLayout.RIGHT, 8, 6));
		QRRoundButton previewButton = new QRRoundButton("重算");
		previewButton.setPreferredSize(new Dimension(80, 30));
		previewButton.addClickAction(event -> refreshPreview());
		QRRoundButton okButton = new QRRoundButton("确定");
		okButton.setPreferredSize(new Dimension(80, 30));
		okButton.addClickAction(event -> {
			if (refreshPreview()) {
				confirmed = true;
				dispose();
			}
		});
		QRRoundButton cancelButton = new QRRoundButton("取消");
		cancelButton.setPreferredSize(new Dimension(80, 30));
		cancelButton.addClickAction(event -> dispose());
		panel.add(previewButton);
		panel.add(cancelButton);
		panel.add(okButton);
		return panel;
	}

	private void fillConfig(QuestionScorePolicy.Config config) {
		weightFunctionBox.setSelectedItem(config.weightFunction().name());
		logitPowerField.setText(formatDouble(config.logitPower()));
		centerPField.setText(formatDouble(config.centerP()));
		minPField.setText(formatDouble(config.minP()));
		maxPField.setText(formatDouble(config.maxP()));
		minWeightField.setText(formatDouble(config.minWeight()));
		maxWeightField.setText(formatDouble(config.maxWeight()));
		epsilonField.setText(formatDouble(config.epsilon()));
	}

	private boolean refreshPreview() {
		try {
			this.config = readConfig();
			this.report = projectEnd.calculateScaleScoreReport(this.config);
			renderScalePreview(report);
			return true;
		} catch (ResultsPanel.ScoreRuleException ex) {
			projectEnd.showScoreRuleError(ex);
		} catch (IllegalArgumentException ex) {
			QROpinionDialog.messageErrShow(this, ex.getMessage());
		}
		return false;
	}

	private QuestionScorePolicy.Config readConfig() {
		return new QuestionScorePolicy.Config(
				QuestionScorePolicy.WeightFunction.valueOf(String.valueOf(weightFunctionBox.getSelectedItem())),
				parseDouble(logitPowerField.getText(), "logit.power"),
				parseDouble(centerPField.getText(), "center.p"),
				parseDouble(minPField.getText(), "min.p"),
				parseDouble(maxPField.getText(), "max.p"),
				parseDouble(minWeightField.getText(), "min.weight"),
				parseDouble(maxWeightField.getText(), "max.weight"),
				parseDouble(epsilonField.getText(), "epsilon")
		);
	}

	private double parseDouble(String text, String name) {
		try {
			return Double.parseDouble(text.trim());
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException(name + " 不是有效数字。");
		}
	}

	private void renderScalePreview(QuestionScorePolicy.ScaleScoreReport report) {
		previewModel.setRowCount(0);
		Map<Integer, String> sectionByQuestion = new LinkedHashMap<>();
		for (ScorePolicy rule : projectEnd.parseScoreRules(projectEnd.ruleTextPane.getText(), projectEnd.standardAnswerCount())) {
			for (Integer questionNumber : rule.questionNumbers()) {
				sectionByQuestion.put(questionNumber, rule.questionType());
			}
		}
		for (Map.Entry<Integer, BigDecimal> entry : report.questionScores().entrySet()) {
			Integer questionNumber = entry.getKey();
			String sectionName = sectionByQuestion.getOrDefault(questionNumber, "");
			previewModel.addRow(new Object[]{
					questionNumber,
					sectionName,
					formatPercent(report.rawRates().getOrDefault(questionNumber, BigDecimal.ZERO)),
					Utils.formatScore(entry.getValue()),
					Utils.formatScore(report.weights().getOrDefault(questionNumber, BigDecimal.ZERO)),
					Utils.formatScore(report.sectionTotals().getOrDefault(sectionName, BigDecimal.ZERO))
			});
		}
		renderStudentPreview(report);
	}

	private void renderStudentPreview(QuestionScorePolicy.ScaleScoreReport report) {
		Vector<String> columns = new Vector<>();
		columns.add("姓名");
		for (String sectionName : report.sectionNames()) {
			columns.add(sectionName);
		}
		columns.add("总分");
		studentPreviewModel.setColumnIdentifiers(columns);
		studentPreviewModel.setRowCount(0);

		List<QuestionScorePolicy.ScaleStudentScore> scores = new ArrayList<>(report.studentScores());
		scores.sort((first, second) -> {
			int byScore = second.earnedScore().compareTo(first.earnedScore());
			return byScore != 0 ? byScore : first.examineeId().compareTo(second.examineeId());
		});
		for (QuestionScorePolicy.ScaleStudentScore score : scores) {
			Vector<Object> row = new Vector<>();
			String name = projectEnd.studentNameFor(score.examineeId());
			row.add(name.isBlank() ? score.examineeId() : name);
			for (String sectionName : report.sectionNames()) {
				row.add(formatOneDecimal(score.sectionScores().getOrDefault(sectionName, BigDecimal.ZERO)));
			}
			row.add(formatOneDecimal(score.earnedScore()));
			studentPreviewModel.addRow(row);
		}
	}

	private String formatPercent(BigDecimal value) {
		return value.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
	}

	private String formatOneDecimal(BigDecimal value) {
		return value.setScale(1, RoundingMode.HALF_UP).toPlainString();
	}

	private String formatDouble(double value) {
		return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
	}

	public boolean confirmed() {
		return confirmed;
	}

	public QuestionScorePolicy.Config config() {
		return config;
	}

	public QuestionScorePolicy.ScaleScoreReport report() {
		return report;
	}
}
