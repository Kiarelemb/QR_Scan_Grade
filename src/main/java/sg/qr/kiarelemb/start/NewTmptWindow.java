package sg.qr.kiarelemb.start;

import method.qr.kiarelemb.utils.QRFileUtils;
import org.bytedeco.opencv.opencv_core.Rect;
import sg.qr.kiarelemb.MainWindow;
import sg.qr.kiarelemb.grading.layout.DetectedRect;
import sg.qr.kiarelemb.grading.layout.LayoutDetector;
import sg.qr.kiarelemb.grading.model.AnswerSheet;
import sg.qr.kiarelemb.grading.model.SubjectiveRegion;
import sg.qr.kiarelemb.grading.model.Template;
import sg.qr.kiarelemb.grading.pipeline.TemplateProcessor;
import swing.qr.kiarelemb.basic.QRLabel;
import swing.qr.kiarelemb.basic.QRPanel;
import swing.qr.kiarelemb.basic.QRRoundButton;
import swing.qr.kiarelemb.basic.QRTextField;
import swing.qr.kiarelemb.theme.QRColorsAndFonts;
import swing.qr.kiarelemb.window.basic.QRDialog;
import swing.qr.kiarelemb.window.enhance.QROpinionDialog;

import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * @author Kiarelemb
 * @projectName QR_Scan_Grade
 * @className NewTmptWindow
 * @description TODO
 * @create 2026/6/4 12:53
 */
public class NewTmptWindow extends QRDialog {
	private final File pictureFile;
	private final int pageCount;
	private LayoutDetector detector;
	private final TmptDataSplitPane dataSplitPane;
	private QRTextField nameField;
	private QRTextField subjectiveTopLeftField;
	private QRTextField subjectiveBottomRightField;

	public NewTmptWindow(File pictureFile) {
		super(MainWindow.INSTANCE);
		this.pictureFile = pictureFile;
		this.pageCount = 1;
		this.dataSplitPane = new TmptDataSplitPane(null);
		initView();
	}

	public NewTmptWindow(File pictureFile, int pageCount) {
		super(MainWindow.INSTANCE);
		this.pictureFile = pictureFile;
		this.pageCount = Math.max(1, pageCount);
		this.dataSplitPane = new TmptDataSplitPane(null);
		initView();
	}

	@Override
	public void windowOpened(WindowEvent e) {
		this.detector = detectTemplate(pictureFile);
		this.dataSplitPane.setTemplate(detector == null ? null : buildTemplate(defaultTemplateName(pictureFile)));
	}

	private LayoutDetector detectTemplate(File pictureFile) {
		if (pictureFile == null) {
			return null;
		}
		try {
			return LayoutDetector.detectFromTemplate(pictureFile);
		} catch (RuntimeException ex) {
			ex.printStackTrace();
			QROpinionDialog.messageErrShow(this, "模板识别失败：\n" + ex.getMessage());
			return null;
		}
	}

	private void initView() {
		setTitle("新建答题卡模板");
		setTitlePlace(CENTER);
		setSize(1180, 760);
		setLocationRelativeTo(MainWindow.INSTANCE);
		setParentWindowNotFollowMove();

		mainPanel.setLayout(new BorderLayout());
		dataSplitPane.setImageClickListener(this::fillFocusedSubjectivePoint);
		mainPanel.add(dataSplitPane, BorderLayout.CENTER);

		QRPanel bottomPanel = new QRPanel();
		bottomPanel.setBorder(new LineBorder(QRColorsAndFonts.FRAME_COLOR_BACK, 10));
		bottomPanel.setLayout(new BorderLayout(10, 10));

		QRLabel nameLabel = new QRLabel("模板名：");
		nameLabel.setPreferredSize(new Dimension(80, 25));

		nameField = new QRTextField();
		nameField.setPreferredSize(new Dimension(220, 25));
		if (pictureFile != null) {
			nameField.setText(defaultTemplateName(pictureFile));
		}

		QRPanel namePanel = new QRPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		namePanel.add(nameLabel);
		namePanel.add(nameField);
		namePanel.add(new QRLabel("主观题左上："));
		subjectiveTopLeftField = new QRTextField();
		subjectiveTopLeftField.setPreferredSize(new Dimension(110, 25));
		namePanel.add(subjectiveTopLeftField);
		namePanel.add(new QRLabel("右下："));
		subjectiveBottomRightField = new QRTextField();
		subjectiveBottomRightField.setPreferredSize(new Dimension(110, 25));
		namePanel.add(subjectiveBottomRightField);
		bottomPanel.add(namePanel, BorderLayout.WEST);

		QRRoundButton saveButton = new QRRoundButton("启用模板");
		saveButton.setPreferredSize(new Dimension(100, 25));
		saveButton.addClickAction(this::saveTemplate);
		bottomPanel.add(saveButton, BorderLayout.EAST);

		mainPanel.add(bottomPanel, BorderLayout.SOUTH);
	}

	private void fillFocusedSubjectivePoint(Point point) {
		if (point == null) {
			return;
		}
		String text = point.x + "," + point.y;
		if (subjectiveBottomRightField != null && subjectiveBottomRightField.hasFocus()) {
			subjectiveBottomRightField.setText(text);
			refreshTemplatePreview();
		} else if (subjectiveTopLeftField != null) {
			subjectiveTopLeftField.setText(text);
			if (subjectiveBottomRightField != null) {
				subjectiveBottomRightField.requestFocusInWindow();
			}
		}
	}

	private void refreshTemplatePreview() {
		if (detector != null) {
			dataSplitPane.setTemplate(buildTemplate(defaultTemplateName(pictureFile)));
		}
	}

	private void saveTemplate(java.awt.event.ActionEvent e) {
		if (detector == null) {
			QROpinionDialog.messageErrShow(this, "模板尚未识别完成，暂时不能保存。");
			return;
		}

		String name = normalizeTemplateName(nameField.getText());
		if (name == null) {
			return;
		}

		File templateDir = new File("sg");
		File targetFile = new File(templateDir, name);
		File sgFile = TemplateProcessor.withSgExtension(targetFile);
		if (sgFile.exists()) {
			int choice = QROpinionDialog.messageInfoShow(this, "模板已存在，是否覆盖？\n" + sgFile.getAbsolutePath());
			if (choice != QROpinionDialog.OK) {
				return;
			}
		}

		setCursorWait();
		try {
			QRFileUtils.dirCreate(templateDir);
			Template template = buildTemplate(name);
			File savedFile = TemplateProcessor.save(template, sgFile);
			QROpinionDialog.messageTellShow(this, "模板 " + savedFile.getName() + " 保存成功。");
			dispose();
		} catch (IOException ex) {
			QROpinionDialog.messageErrShow(this, "模板保存失败：\n" + ex.getMessage());
		} finally {
			setCursorDefault();
		}
	}

	private Template buildTemplate(String name) {
		AnswerSheet answerSheet = detector.buildAnswerSheet(
				detector.detectedImageW,
				detector.detectedImageH,
				name,
				new String[0]
		);
		return new Template(
				name,
				pictureFile,
				answerSheet,
				toRect(detector.examRegionRect),
				copyRect(detector.choiceRegionRect),
				fillBlankRegionRect(),
				"",
				pageCount,
				defaultSubjectiveRegions(answerSheet)
		);
	}

	private List<SubjectiveRegion> defaultSubjectiveRegions(AnswerSheet answerSheet) {
		Rect rect = fillBlankRegionRect();
		if (rect == null) {
			return List.of();
		}
		int start = answerSheet.getChoiceQuestions().size() + 1;
		int count = Math.max(1, answerSheet.getFillBlankQuestions().size());
		return List.of(new SubjectiveRegion("主观题1", start, start + count - 1, rect,
				SubjectiveRegion.GradingMode.OCR, BigDecimal.ZERO));
	}

	private Rect fillBlankRegionRect() {
		Rect manual = manualSubjectiveRegion();
		if (manual != null) {
			return manual;
		}
		if (detector.fillBlankCount <= 0 || detector.fillBoxW <= 0 || detector.fillBoxH <= 0) {
			return null;
		}
		return new Rect(detector.fillStartX, detector.fillStartY, detector.fillBoxW, detector.fillBoxH);
	}

	private Rect manualSubjectiveRegion() {
		Point topLeft = parsePoint(subjectiveTopLeftField == null ? "" : subjectiveTopLeftField.getText());
		Point bottomRight = parsePoint(subjectiveBottomRightField == null ? "" : subjectiveBottomRightField.getText());
		if (topLeft == null || bottomRight == null) {
			return null;
		}
		int x1 = Math.min(topLeft.x, bottomRight.x);
		int y1 = Math.min(topLeft.y, bottomRight.y);
		int x2 = Math.max(topLeft.x, bottomRight.x);
		int y2 = Math.max(topLeft.y, bottomRight.y);
		if (x2 <= x1 || y2 <= y1) {
			return null;
		}
		return new Rect(x1, y1, x2 - x1, y2 - y1);
	}

	private Point parsePoint(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String[] parts = text.trim().split("[,，\\s]+");
		if (parts.length != 2) {
			return null;
		}
		try {
			return new Point(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private static Rect toRect(DetectedRect rect) {
		return rect == null ? null : new Rect(rect.x(), rect.y(), rect.w(), rect.h());
	}

	private static Rect copyRect(Rect rect) {
		return rect == null ? null : new Rect(rect.x(), rect.y(), rect.width(), rect.height());
	}

	private String normalizeTemplateName(String rawName) {
		String name = rawName == null ? "" : rawName.trim();
		if (name.toLowerCase().endsWith("." + TemplateProcessor.TEMPLATE_EXTENSION)) {
			name = name.substring(0, name.length() - TemplateProcessor.TEMPLATE_EXTENSION.length() - 1).trim();
		}
		if (name.isEmpty()) {
			QROpinionDialog.messageErrShow(this, "请输入模板名。");
			return null;
		}
		if (name.matches(".*[\\\\/:*?\"<>|].*")) {
			QROpinionDialog.messageErrShow(this, "模板名不能包含 \\ / : * ? \" < > | 这些字符。");
			return null;
		}
		return name;
	}

	private static String defaultTemplateName(File pictureFile) {
		if (pictureFile == null) {
			return "";
		}
		String name = pictureFile.getName();
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}
}
