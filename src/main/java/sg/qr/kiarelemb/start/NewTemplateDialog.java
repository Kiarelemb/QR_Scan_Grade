package sg.qr.kiarelemb.start;

import method.qr.kiarelemb.utils.QRFileUtils;
import org.bytedeco.opencv.opencv_core.Rect;
import sg.qr.kiarelemb.MainWindow;
import sg.qr.kiarelemb.exam.template.detect.DetectedBox;
import sg.qr.kiarelemb.exam.template.detect.TemplateLayoutDetector;
import sg.qr.kiarelemb.exam.model.SheetLayout;
import sg.qr.kiarelemb.exam.model.SubjectiveAnswerRegion;
import sg.qr.kiarelemb.exam.model.SheetTemplate;
import sg.qr.kiarelemb.exam.processing.SheetTemplateFileStore;
import swing.qr.kiarelemb.basic.QRLabel;
import swing.qr.kiarelemb.basic.QRPanel;
import swing.qr.kiarelemb.basic.QRRoundButton;
import swing.qr.kiarelemb.basic.QRTextField;
import swing.qr.kiarelemb.theme.QRColorsAndFonts;
import swing.qr.kiarelemb.utils.QRFilePathTextField;
import swing.qr.kiarelemb.window.basic.QRDialog;
import swing.qr.kiarelemb.window.enhance.QROpinionDialog;

import javax.imageio.ImageIO;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Kiarelemb
 * @projectName QR_Scan_Grade
 * @className NewTmptWindow
 * @description TODO
 * @create 2026/6/4 12:53
 */
public class NewTemplateDialog extends QRDialog {
	private final File pictureFile;
	private final List<File> pictureFiles;
	private final int pageCount;
	private TemplateLayoutDetector detector;
	private final TemplateAnalysisPane dataSplitPane;
	private QRTextField nameField;

	public NewTemplateDialog(File pictureFile) {
		this(singlePictureFile(pictureFile));
	}

	public NewTemplateDialog(File pictureFile, int pageCount) {
		this(singlePictureFile(pictureFile), pageCount);
	}

	public NewTemplateDialog(List<File> pictureFiles) {
		this(pictureFiles, pictureFiles == null ? 1 : pictureFiles.size());
	}

	public NewTemplateDialog(List<File> pictureFiles, int pageCount) {
		super(MainWindow.INSTANCE);
		this.pictureFiles = normalizedPictureFiles(pictureFiles);
		this.pictureFile = this.pictureFiles.get(0);
		this.pageCount = Math.max(1, pageCount);
		this.dataSplitPane = new TemplateAnalysisPane(null);
		initView();
	}

	@Override
	public void windowOpened(WindowEvent e) {
		this.detector = detectTemplate(pictureFile);
		this.dataSplitPane.setTemplate(detector == null ? null : buildTemplate(defaultTemplateName(pictureFile)));
	}

	private TemplateLayoutDetector detectTemplate(File pictureFile) {
		if (pictureFile == null) {
			return null;
		}
		try {
			return TemplateLayoutDetector.detectFromTemplate(pictureFile);
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
		mainPanel.add(dataSplitPane, BorderLayout.CENTER);

		QRPanel bottomPanel = new QRPanel();
		bottomPanel.setBorder(new LineBorder(QRColorsAndFonts.FRAME_COLOR_BACK, 10));
		bottomPanel.setLayout(new BorderLayout(10, 10));

		QRLabel nameLabel = new QRLabel("模板名：");
		nameLabel.setPreferredSize(new Dimension(80, 25));

		nameField = new QRTextField();
		nameField.setTextLeft();
		nameField.setPreferredSize(new Dimension(220, 25));
		if (pictureFile != null) {
			nameField.setText(defaultTemplateName(pictureFile));
		}

		QRPanel namePanel = new QRPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		namePanel.add(nameLabel);
		namePanel.add(nameField);
		bottomPanel.add(namePanel, BorderLayout.WEST);

		QRRoundButton saveButton = new QRRoundButton("启用模板");
		saveButton.setPreferredSize(new Dimension(100, 25));
		saveButton.addClickAction(this::saveTemplate);
		bottomPanel.add(saveButton, BorderLayout.EAST);

		mainPanel.add(bottomPanel, BorderLayout.SOUTH);
	}

	private void saveTemplate(ActionEvent e) {
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
		File sgFile = SheetTemplateFileStore.withSgExtension(targetFile);
		if (sgFile.exists()) {
			int choice = QROpinionDialog.messageInfoShow(this, "模板已存在，是否覆盖？\n" + sgFile.getAbsolutePath());
			if (choice != QROpinionDialog.OK) {
				return;
			}
		}

		setCursorWait();
		try {
			QRFileUtils.dirCreate(templateDir);
			SheetTemplate template = buildTemplate(name);
			File savedFile = SheetTemplateFileStore.save(template, sgFile);
			QROpinionDialog.messageTellShow(this, "模板 " + savedFile.getName() + " 保存成功。");
			dispose();
		} catch (IOException ex) {
			QROpinionDialog.messageErrShow(this, "模板保存失败：\n" + ex.getMessage());
		} finally {
			setCursorDefault();
		}
	}

	private SheetTemplate buildTemplate(String name) {
		SheetLayout answerSheet = detector.buildAnswerSheet(
				detector.detectedImageW,
				detector.detectedImageH,
				name,
				new String[0]
		);
		return new SheetTemplate(
				name,
				pictureFile,
				answerSheet,
				toRect(detector.examRegionRect),
				copyRect(detector.choiceRegionRect),
				fillBlankRegionRect(),
				"",
				pageCount,
				defaultSubjectiveRegions(answerSheet),
				pictureFiles
		);
	}

	private static List<File> normalizedPictureFiles(List<File> files) {
		if (files == null || files.isEmpty() || files.get(0) == null) {
			throw new IllegalArgumentException("模板图片不能为空");
		}
		return List.copyOf(files);
	}

	private static List<File> singlePictureFile(File file) {
		return file == null ? List.of() : List.of(file);
	}

	private List<SubjectiveAnswerRegion> defaultSubjectiveRegions(SheetLayout answerSheet) {
		List<SubjectiveAnswerRegion> regions = new ArrayList<>();
		Rect firstPageRect = fillBlankRegionRect();
		int start = answerSheet.getChoiceQuestions().size() + 1;
		int count = Math.max(1, answerSheet.getFillBlankQuestions().size());
		if (firstPageRect != null) {
			regions.add(new SubjectiveAnswerRegion("填空题", start, start + count - 1, firstPageRect,
					SubjectiveAnswerRegion.GradingMode.OCR, BigDecimal.ZERO, 0));
		}
		if (pictureFiles.size() >= 2) {
			int secondPageQuestion = regions.isEmpty() ? start : start + count;
			regions.add(new SubjectiveAnswerRegion("主观题2", secondPageQuestion, secondPageQuestion,
					secondPageSubjectiveRect(answerSheet),
					SubjectiveAnswerRegion.GradingMode.MANUAL, BigDecimal.ZERO, 1));
		}
		return List.copyOf(regions);
	}

	private Rect secondPageSubjectiveRect(SheetLayout answerSheet) {
		try {
			BufferedImage image = ImageIO.read(pictureFiles.get(1));
			if (image != null) {
				Rect frame = detectLargeWritingFrame(image);
				if (frame != null) {
					return scaleRect(frame, image.getWidth(), image.getHeight(),
							answerSheet.getImageWidth(), answerSheet.getImageHeight());
				}
			}
		} catch (IOException ignored) {
		}
		int marginX = Math.max(1, answerSheet.getImageWidth() / 10);
		int marginY = Math.max(1, answerSheet.getImageHeight() / 12);
		return new Rect(marginX, marginY,
				Math.max(1, answerSheet.getImageWidth() - marginX * 2),
				Math.max(1, answerSheet.getImageHeight() - marginY * 2));
	}

	private Rect detectLargeWritingFrame(BufferedImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		int horizontalThreshold = Math.max(120, width / 3);
		int verticalThreshold = Math.max(120, height / 3);
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (int y = 0; y < height; y++) {
			int count = 0;
			for (int x = 0; x < width; x++) {
				if (isDark(image.getRGB(x, y))) {
					count++;
				}
			}
			if (count >= horizontalThreshold) {
				minY = Math.min(minY, y);
				maxY = Math.max(maxY, y);
			}
		}
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		for (int x = 0; x < width; x++) {
			int count = 0;
			for (int y = 0; y < height; y++) {
				if (isDark(image.getRGB(x, y))) {
					count++;
				}
			}
			if (count >= verticalThreshold) {
				minX = Math.min(minX, x);
				maxX = Math.max(maxX, x);
			}
		}
		if (minX == Integer.MAX_VALUE || minY == Integer.MAX_VALUE) {
			return null;
		}
		int frameWidth = maxX - minX + 1;
		int frameHeight = maxY - minY + 1;
		if (frameWidth < width / 2 || frameHeight < height / 2) {
			return null;
		}
		return new Rect(minX, minY, frameWidth, frameHeight);
	}

	private boolean isDark(int rgb) {
		int red = (rgb >> 16) & 0xff;
		int green = (rgb >> 8) & 0xff;
		int blue = rgb & 0xff;
		return (red + green + blue) / 3 < 180;
	}

	private Rect scaleRect(Rect rect, int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
		double sx = (double) targetWidth / Math.max(1, sourceWidth);
		double sy = (double) targetHeight / Math.max(1, sourceHeight);
		return new Rect(
				(int) Math.round(rect.x() * sx),
				(int) Math.round(rect.y() * sy),
				Math.max(1, (int) Math.round(rect.width() * sx)),
				Math.max(1, (int) Math.round(rect.height() * sy))
		);
	}

	private Rect fillBlankRegionRect() {
		if (detector.fillBlankCount <= 0 || detector.fillBoxW <= 0 || detector.fillBoxH <= 0) {
			return null;
		}
		return new Rect(detector.fillStartX, detector.fillStartY, detector.fillBoxW, detector.fillBoxH);
	}

	private static Rect toRect(DetectedBox rect) {
		return rect == null ? null : new Rect(rect.x(), rect.y(), rect.w(), rect.h());
	}

	private static Rect copyRect(Rect rect) {
		return rect == null ? null : new Rect(rect.x(), rect.y(), rect.width(), rect.height());
	}

	private String normalizeTemplateName(String rawName) {
		String name = rawName == null ? "" : rawName.trim();
		if (name.toLowerCase().endsWith("." + SheetTemplateFileStore.TEMPLATE_EXTENSION)) {
			name = name.substring(0, name.length() - SheetTemplateFileStore.TEMPLATE_EXTENSION.length() - 1).trim();
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