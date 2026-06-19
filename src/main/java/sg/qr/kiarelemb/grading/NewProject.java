package sg.qr.kiarelemb.grading;

import method.qr.kiarelemb.utils.QRFileUtils;
import sg.qr.kiarelemb.MainWindow;
import sg.qr.kiarelemb.grading.model.Project;
import sg.qr.kiarelemb.grading.model.Template;
import sg.qr.kiarelemb.grading.pipeline.DocumentImageLoader;
import sg.qr.kiarelemb.menu.data.EnglishScoreInput;
import swing.qr.kiarelemb.basic.*;
import swing.qr.kiarelemb.theme.QRColorsAndFonts;
import swing.qr.kiarelemb.window.basic.QRDialog;
import swing.qr.kiarelemb.window.enhance.QROpinionDialog;

import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.*;
import java.util.List;

public class NewProject extends QRDialog {
	private static final File PROJECT_DIR = new File("sgp");
	private static final String PROJECT_EXTENSION = "sgp";
	private static final int FIELD_HEIGHT = 28;

	private final Template template;
	private final File templateFile;
	private final File answerDirectory;
	private final QRTextField projectNameField = new QRTextField();
	private final QRTextPane answersTextPane = new QRTextPane();
	private final QRTextField machineSubjectiveCountField = new QRTextField();
	private final QRLabel directoryLabel = new QRLabel();
	private final QRLabel templateLabel = new QRLabel();
	private final QRLabel countLabel = new QRLabel();
	private final QRTextPane studentsTextPane = new QRTextPane();
	private final QRTextField examPrefixField = new QRTextField();
	private final QRLabel examIdTipLabel = new QRLabel();
	private final QRTextPane examIdPreviewTextPane = new QRTextPane();

	public NewProject() {
		this(null, null, null);
	}

	public NewProject(Template template, File templateFile, File answerDirectory) {
		super(MainWindow.INSTANCE);
		this.template = template;
		this.templateFile = templateFile;
		this.answerDirectory = answerDirectory;
		initView();
	}

	private void initView() {
		setTitle("新建批阅流程");
		setTitlePlace(CENTER);
		setSize(920, 680);
		setLocationRelativeTo(MainWindow.INSTANCE);
		setParentWindowNotFollowMove();
		mainPanel.setLayout(new BorderLayout());

		QRPanel formPanel = new QRPanel(false, new GridBagLayout());
		formPanel.setBorder(new LineBorder(QRColorsAndFonts.FRAME_COLOR_BACK, 12));

		projectNameField.setPreferredSize(new Dimension(360, FIELD_HEIGHT));
		projectNameField.setText(defaultProjectName());
		projectNameField.fileNameOnly();
		templateLabel.setText(templateFile == null ? "未选择模板" : templateFile.getAbsolutePath());
		directoryLabel.setText(answerDirectory == null ? "未选择答卷文件夹" : answerDirectory.getAbsolutePath());
		machineSubjectiveCountField.setPreferredSize(new Dimension(120, FIELD_HEIGHT));
		machineSubjectiveCountField.setText(String.valueOf(defaultMachineSubjectiveCount()));
		refreshAnswerCountLabel();

		answersTextPane.setLineWrap(true);
		studentsTextPane.setLineWrap(false);
		studentsTextPane.setText(defaultStudentNamesText());
		examPrefixField.setPreferredSize(new Dimension(180, FIELD_HEIGHT));
		examPrefixField.setText(defaultExamPrefix());
		examIdPreviewTextPane.setLineWrap(false);
		updateExamIdTip();

		addRow(formPanel, 0, "项目名：", projectNameField);
		addRow(formPanel, 1, "模板：", templateLabel);
		addRow(formPanel, 2, "答卷文件夹：", directoryLabel);
		addRow(formPanel, 3, "机判非选：", buildMachineSubjectivePanel());
		addRow(formPanel, 4, "正确答案：", answersTextPane.addInternalScrollPane());
		addRow(formPanel, 5, "", countLabel);
		addRow(formPanel, 6, "学生姓名：", buildStudentPanel());
		addRow(formPanel, 7, "准考证号：", buildExamIdPanel());
		addRow(formPanel, 8, "分配预览：", examIdPreviewTextPane.addInternalScrollPane());
		mainPanel.add(formPanel, BorderLayout.CENTER);

		QRPanel bottomPanel = new QRPanel(false, new FlowLayout(FlowLayout.RIGHT, 8, 0));
		bottomPanel.setBorder(new LineBorder(QRColorsAndFonts.FRAME_COLOR_BACK, 10));
		QRRoundButton saveButton = new QRRoundButton("创建项目");
		saveButton.setPreferredSize(new Dimension(100, 30));
		saveButton.addClickAction(this::saveProject);
		QRRoundButton cancelButton = new QRRoundButton("取消");
		cancelButton.setPreferredSize(new Dimension(80, 30));
		cancelButton.addClickAction(event -> dispose());
		bottomPanel.add(cancelButton);
		bottomPanel.add(saveButton);
		mainPanel.add(bottomPanel, BorderLayout.SOUTH);
	}

	@Override
	public void windowOpened(WindowEvent e) {
		answersTextPane.grabFocus();
	}

	private void addRow(QRPanel panel, int row, String labelText, Component component) {
		GridBagConstraints labelConstraints = new GridBagConstraints();
		labelConstraints.gridx = 0;
		labelConstraints.gridy = row;
		labelConstraints.anchor = GridBagConstraints.NORTHEAST;
		labelConstraints.insets = new Insets(6, 6, 6, 10);
		QRLabel label = new QRLabel(labelText);
		label.setPreferredSize(new Dimension(110, FIELD_HEIGHT));
		panel.add(label, labelConstraints);

		GridBagConstraints componentConstraints = new GridBagConstraints();
		componentConstraints.gridx = 1;
		componentConstraints.gridy = row;
		componentConstraints.weightx = 1;
		componentConstraints.fill = GridBagConstraints.HORIZONTAL;
		componentConstraints.anchor = GridBagConstraints.NORTHWEST;
		componentConstraints.insets = new Insets(6, 0, 6, 6);
		if (row == 4 || row == 6 || row == 8) {
			componentConstraints.weighty = 1;
			componentConstraints.fill = GridBagConstraints.BOTH;
		}
		panel.add(component, componentConstraints);
	}

	/**
	 * TODO 后续要支持两页 A4/A3、作文一篇或两篇，就需要把模板结构扩展为多个主观题区域，例如 填空区域、小作文区域、大作文区域，人工评分面板再按题型切换或显示对应裁图。
	 *
	 * @return 机判非选题数
	 */
	private Component buildMachineSubjectivePanel() {
		QRPanel panel = new QRPanel(false, new FlowLayout(FlowLayout.LEFT, 8, 0));
		QRLabel label = new QRLabel("非选择题中由程序判分的题数；人工打分填 0");
		label.setPreferredSize(new Dimension(380, FIELD_HEIGHT));
		panel.add(machineSubjectiveCountField);
		panel.add(label);
		machineSubjectiveCountField.addDocumentListenerAction(swing.qr.kiarelemb.listener.QRDocumentListener.TYPE.INSERT, event -> refreshAnswerCountLabel());
		machineSubjectiveCountField.addDocumentListenerAction(swing.qr.kiarelemb.listener.QRDocumentListener.TYPE.REMOVE, event -> refreshAnswerCountLabel());
		machineSubjectiveCountField.addDocumentListenerAction(swing.qr.kiarelemb.listener.QRDocumentListener.TYPE.CHANGED, event -> refreshAnswerCountLabel());
		return panel;
	}

	private Component buildStudentPanel() {
		QRPanel panel = new QRPanel(false, new BorderLayout(5, 5));
		panel.add(studentsTextPane.addInternalScrollPane(), BorderLayout.CENTER);
		QRPanel buttonPanel = new QRPanel(false, new FlowLayout(FlowLayout.RIGHT, 8, 0));
		QRRoundButton shuffleButton = new QRRoundButton("随机排序");
		shuffleButton.setPreferredSize(new Dimension(110, 30));
		shuffleButton.addClickAction(event -> shuffleStudentNames());
		buttonPanel.add(shuffleButton);
		panel.add(buttonPanel, BorderLayout.SOUTH);
		return panel;
	}

	private Component buildExamIdPanel() {
		QRPanel panel = new QRPanel(false, new FlowLayout(FlowLayout.LEFT, 8, 0));
		QRLabel prefixLabel = new QRLabel("前缀：");
		prefixLabel.setPreferredSize(new Dimension(60, FIELD_HEIGHT));
		panel.add(prefixLabel);
		panel.add(examPrefixField);
		QRRoundButton generateButton = new QRRoundButton("生成预览");
		generateButton.setPreferredSize(new Dimension(110, 30));
		generateButton.addClickAction(event -> previewExamIds());
		panel.add(generateButton);
		examIdTipLabel.setPreferredSize(new Dimension(360, FIELD_HEIGHT));
		panel.add(examIdTipLabel);
		return panel;
	}

	private void saveProject(ActionEvent event) {
		String projectName = normalizeProjectName(projectNameField.getText());
		if (projectName == null) {
			return;
		}
		if (template == null || templateFile == null) {
			QROpinionDialog.messageErrShow(this, "未选择模板，不能创建批阅流程。");
			return;
		}
		if (answerDirectory == null || !answerDirectory.isDirectory()) {
			QROpinionDialog.messageErrShow(this, "答卷文件夹不存在。");
			return;
		}
		String[] standardAnswers = splitAnswers(answersTextPane.getText());
		int machineSubjectiveCount;
		int expectedCount;
		try {
			machineSubjectiveCount = machineSubjectiveCount();
			expectedCount = expectedAnswerCount();
		} catch (IllegalArgumentException ex) {
			QROpinionDialog.messageErrShow(this, ex.getMessage());
			return;
		}
		if (expectedCount > 0 && standardAnswers.length == 0) {
			QROpinionDialog.messageErrShow(this, "请填写正确答案。");
			return;
		}
		if (standardAnswers.length != expectedCount) {
			QROpinionDialog.messageErrShow(this, "正确答案数量不匹配。\n需要 " + expectedCount + " 个，当前填写 " + standardAnswers.length + " 个。");
			return;
		}

		AnswerFilePlan answerFilePlan;
		try {
			answerFilePlan = answerFilePlan();
		} catch (Exception ex) {
			QROpinionDialog.messageErrShow(this, "读取答卷文件失败：\n" + ex.getMessage());
			return;
		}
		List<File> answerFiles = answerFilePlan.frontFiles();
		File projectFile = new File(PROJECT_DIR, projectName + "." + PROJECT_EXTENSION);
		if (projectFile.exists()) {
			int choice = QROpinionDialog.messageInfoShow(this, "批阅项目已存在，是否覆盖：\n" + projectFile.getAbsolutePath());
			if (choice != QROpinionDialog.OK) {
				return;
			}
		}

		setCursorWait();
		try {
			QRFileUtils.dirCreate(PROJECT_DIR);
			Project project = new Project(projectFile.getAbsolutePath(), templateFile.getAbsolutePath(),
					answerDirectory.getAbsolutePath(), standardAnswers, answerFiles, answerFilePlan.backFilesByFrontName());
			if (answerFilePlan.convertedPdf() != null) {
				project.setConvertedPdf(answerFilePlan.convertedPdf(), answerFilePlan.convertedImageCount());
			}
			project.setMachineSubjectiveCount(machineSubjectiveCount);
			project.setStudentNamesByExamId(buildStudentExamIdMap());
			project.write();
			String message = "批阅项目创建成功！";
			if (answerFiles.isEmpty()) {
				message += "\n答卷扫描件请稍后放入“" + answerDirectory.getName() + "”文件夹";
			}
			QROpinionDialog.messageTellShow(this, message);
			dispose();
			if (answerFiles.isEmpty()) {
				MainWindow.INSTANCE.showStartPanel();
			} else {
				MainWindow.INSTANCE.startProject(project);
			}
		} catch (Exception ex) {
			QROpinionDialog.messageErrShow(this, "批阅项目保存失败：\n" + ex.getMessage());
		} finally {
			setCursorDefault();
		}
	}

	private Map<String, String> buildStudentExamIdMap() {
		Map<String, String> imported = importedStudentExamIdMap();
		if (imported != null) {
			return imported;
		}
		List<String> names = studentNames();
		Map<String, String> result = new LinkedHashMap<>();
		if (names.isEmpty()) {
			return result;
		}
		int digits = examIdDigits();
		int suffixDigits = suffixDigits(names.size());
		String prefix = examPrefixField.getText() == null ? "" : examPrefixField.getText().trim();
		if (!prefix.matches("\\d*")) {
			throw new IllegalArgumentException("准考证号前缀只能包含数字。");
		}
		if (prefix.length() + suffixDigits != digits) {
			throw new IllegalArgumentException("准考证号位数不匹配：模板要求 " + digits + " 位，当前前缀 "
											   + prefix.length() + " 位，后缀 " + suffixDigits + " 位。");
		}
		for (int i = 0; i < names.size(); i++) {
			result.put(prefix + String.format(Locale.ROOT, "%0" + suffixDigits + "d", i + 1), names.get(i));
		}
		return result;
	}

	private Map<String, String> importedStudentExamIdMap() {
		String text = studentsTextPane.getText() == null ? "" : studentsTextPane.getText();
		String[] lines = text.split("\\R");
		Map<String, String> result = new LinkedHashMap<>();
		boolean hasImportedLine = false;
		boolean hasPlainLine = false;
		int digits = examIdDigits();
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i].trim();
			if (line.isEmpty()) {
				continue;
			}
			if (isStudentHeader(line)) {
				continue;
			}
			String[] parts = splitStudentLine(line);
			boolean importedLine = parts.length == 2 && parts[0].trim().matches("\\d+");
			if (!importedLine) {
				hasPlainLine = true;
				continue;
			}
			hasImportedLine = true;
			String examId = parts[0].trim();
			String name = parts[1].trim();
			if (digits > 0 && examId.length() != digits) {
				throw new IllegalArgumentException("学生名单第 " + (i + 1) + " 行准考证号位数不匹配：模板要求 "
												   + digits + " 位，当前为 " + examId.length() + " 位。");
			}
			if (name.isEmpty()) {
				throw new IllegalArgumentException("学生名单第 " + (i + 1) + " 行姓名为空。");
			}
			if (result.containsKey(examId)) {
				throw new IllegalArgumentException("学生名单中准考证号重复：" + examId);
			}
			result.put(examId, name);
		}
		if (hasImportedLine && hasPlainLine) {
			throw new IllegalArgumentException("学生名单格式不一致。\n"
											   + "如果导入已有准考证号，请每行使用：准考证号<Tab>姓名");
		}
		return hasImportedLine ? result : null;
	}

	private void previewExamIds() {
		try {
			Map<String, String> map = buildStudentExamIdMap();
			StringBuilder builder = new StringBuilder();
			for (Map.Entry<String, String> entry : map.entrySet()) {
				builder.append(entry.getKey()).append('\t').append(entry.getValue()).append(System.lineSeparator());
			}
			examIdPreviewTextPane.setText(builder.toString());
			updateExamIdTip();
		} catch (IllegalArgumentException ex) {
			QROpinionDialog.messageErrShow(this, ex.getMessage());
		}
	}

	private void shuffleStudentNames() {
		List<String> names = new ArrayList<>(studentNames());
		Collections.shuffle(names);
		studentsTextPane.setText(String.join(System.lineSeparator(), names));
		previewExamIds();
	}

	private List<String> studentNames() {
		return Arrays.stream((studentsTextPane.getText() == null ? "" : studentsTextPane.getText()).split("\\R"))
				.map(String::trim)
				.filter(name -> !name.isEmpty())
				.map(this::studentNameFromLine)
				.distinct()
				.toList();
	}

	private String studentNameFromLine(String line) {
		String[] parts = splitStudentLine(line);
		if (parts.length == 2 && parts[0].trim().matches("\\d+")) {
			return parts[1].trim();
		}
		if (parts.length == 2 && isDecimal(parts[1].trim())) {
			return parts[0].trim();
		}
		return line.trim();
	}

	private String[] splitStudentLine(String line) {
		String[] parts = line.split("[\\t,，]", 2);
		if (parts.length == 2) {
			return parts;
		}
		return line.trim().split("\\s+", 2);
	}

	private boolean isStudentHeader(String line) {
		String normalized = line.replace(" ", "").replace("\t", "");
		return normalized.contains("准考证") && normalized.contains("姓名");
	}

	private boolean isDecimal(String text) {
		try {
			new java.math.BigDecimal(text);
			return true;
		} catch (NumberFormatException ex) {
			return false;
		}
	}

	private String defaultStudentNamesText() {
		return String.join(System.lineSeparator(), EnglishScoreInput.readScores().keySet());
	}

	private String defaultExamPrefix() {
		int count = Math.max(1, EnglishScoreInput.readScores().size());
		int prefixDigits = Math.max(0, examIdDigits() - suffixDigits(count));
		return "0".repeat(prefixDigits);
	}

	private void updateExamIdTip() {
		int count = studentNames().size();
		int suffixDigits = suffixDigits(Math.max(1, count));
		examIdTipLabel.setText("模板 " + examIdDigits() + " 位；当前 " + count + " 人，后缀 " + suffixDigits + " 位。");
	}

	private int examIdDigits() {
		return template == null ? 0 : template.answerSheet().getExamIdDigits();
	}

	private int suffixDigits(int studentCount) {
		return Math.max(1, String.valueOf(Math.max(1, studentCount)).length() + 1);
	}

	private String normalizeProjectName(String rawName) {
		String name = rawName == null ? "" : rawName.trim();
		if (name.toLowerCase(Locale.ROOT).endsWith("." + PROJECT_EXTENSION)) {
			name = name.substring(0, name.length() - PROJECT_EXTENSION.length() - 1).trim();
		}
		if (name.isEmpty()) {
			QROpinionDialog.messageErrShow(this, "请输入项目名。");
			return null;
		}
		if (name.matches(".*[\\\\/:*?\"<>|].*")) {
			QROpinionDialog.messageErrShow(this, "项目名不能包含 \\ / : * ? \" < > | 这些字符。");
			return null;
		}
		return name;
	}

	private String[] splitAnswers(String text) {
		String answersText = text == null ? "" : text.trim();
		if (answersText.isEmpty()) {
			return new String[0];
		}
		return Arrays.stream(answersText.split("[ \\t\\r\\n]+"))
				.map(String::trim)
				.filter(answer -> !answer.isEmpty())
				.toArray(String[]::new);
	}

	private int expectedAnswerCount() {
		if (template == null) {
			return 0;
		}
		return template.answerSheet().getChoiceQuestions().size() + machineSubjectiveCount();
	}

	private int defaultMachineSubjectiveCount() {
		return template == null ? 0 : template.answerSheet().getFillBlankQuestions().size();
	}

	private int machineSubjectiveCount() {
		String text = machineSubjectiveCountField.getText() == null ? "" : machineSubjectiveCountField.getText().trim();
		if (text.isEmpty()) {
			return 0;
		}
		try {
			int count = Integer.parseInt(text);
			if (count < 0) {
				throw new NumberFormatException();
			}
			return count;
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException("机判非选题数必须是非负整数。");
		}
	}

	private void refreshAnswerCountLabel() {
		try {
			int choiceCount = template == null ? 0 : template.answerSheet().getChoiceQuestions().size();
			int machineCount = machineSubjectiveCount();
			countLabel.setText("需要填写 " + (choiceCount + machineCount) + " 个答案（选择题 "
							   + choiceCount + "，机判非选 " + machineCount + "）。");
		} catch (IllegalArgumentException ex) {
			countLabel.setText(ex.getMessage());
		}
	}

	private AnswerFilePlan answerFilePlan() throws java.io.IOException {
		List<File> allImages = DocumentImageLoader.sortedAnswerImages(answerDirectory);
		List<File> frontFiles = new ArrayList<>();
		Map<String, File> backFilesByFrontName = new LinkedHashMap<>();
		File convertedPdf = DocumentImageLoader.singlePdfFile(answerDirectory);
		int pageCount = template == null ? 1 : Math.max(1, template.pageCount());
		for (int i = 0; i < allImages.size(); i += pageCount) {
			File front = allImages.get(i);
			frontFiles.add(front);
			if (pageCount >= 2 && i + 1 < allImages.size()) {
				backFilesByFrontName.put(front.getName(), allImages.get(i + 1));
			}
		}
		return new AnswerFilePlan(frontFiles, backFilesByFrontName, convertedPdf, convertedPdf == null ? 0 : allImages.size());
	}

	private record AnswerFilePlan(List<File> frontFiles, Map<String, File> backFilesByFrontName,
								  File convertedPdf, int convertedImageCount) {
	}

	private String defaultProjectName() {
		if (template == null) {
			return "";
		}
		return template.name() + "_批阅项目";
	}
}
