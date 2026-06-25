package sg.qr.kiarelemb.start;

import org.bytedeco.opencv.opencv_core.Rect;
import sg.qr.kiarelemb.component.AnswerSheetPreviewPanel;
import sg.qr.kiarelemb.grading.model.AnswerSheet;
import sg.qr.kiarelemb.grading.model.Template;
import swing.qr.kiarelemb.basic.QRButton;
import swing.qr.kiarelemb.basic.QRLabel;
import swing.qr.kiarelemb.basic.QRPanel;
import swing.qr.kiarelemb.basic.QRSplitPane;
import swing.qr.kiarelemb.basic.QRTextPane;
import swing.qr.kiarelemb.theme.QRColorsAndFonts;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class TmptDataSplitPane extends QRSplitPane {
	private static final Font TEXT_FONT = QRColorsAndFonts.createFont(18);
	private static final Color EXAM_COLOR = new Color(218, 64, 64);
	private static final Color CHOICE_COLOR = new Color(52, 112, 210);
	private static final Color FILL_COLOR = new Color(46, 150, 92);

	private final QRTextPane resultPane = new QRTextPane();
	private final QRPanel previewContainer = new QRPanel(new BorderLayout(0, 6));
	private final AnswerSheetPreviewPanel previewPanel = new AnswerSheetPreviewPanel(
			"暂无图片",
			new Dimension(760, 700),
			new Dimension(420, 420),
			EXAM_COLOR,
			CHOICE_COLOR,
			FILL_COLOR
	);
	private final QRPanel pagePanel = new QRPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
	private final QRButton prevPageButton = new QRButton("<");
	private final QRButton nextPageButton = new QRButton(">");
	private final QRLabel pageLabel = new QRLabel();
	private Template template;
	private int pageIndex;

	public TmptDataSplitPane(Template template) {
		super(JSplitPane.HORIZONTAL_SPLIT);
		initView();
		setTemplate(template);
	}

	private void initView() {
		setResizeWeight(0.68);
		initPagePanel();
		previewContainer.add(previewPanel, BorderLayout.CENTER);
		previewContainer.add(pagePanel, BorderLayout.SOUTH);
		setLeftComponent(previewContainer);
		setRightComponent(resultPane.addScrollPane());
		setDividerLocation(760);

		resultPane.setFont(TEXT_FONT);
		resultPane.setEditableFalseButCursorEdit();
		resultPane.setLineWrap(true);
		resultPane.setLineSpacing(0.4f);
	}

	private void initPagePanel() {
		prevPageButton.setPreferredSize(new Dimension(42, 28));
		nextPageButton.setPreferredSize(new Dimension(42, 28));
		pageLabel.setHorizontalAlignment(QRLabel.CENTER);
		pageLabel.setPreferredSize(new Dimension(100, 28));
		prevPageButton.addClickAction(e -> showPage(pageIndex - 1));
		nextPageButton.addClickAction(e -> showPage(pageIndex + 1));
		pagePanel.add(prevPageButton);
		pagePanel.add(pageLabel);
		pagePanel.add(nextPageButton);
	}

	public void setTemplate(Template template) {
		this.template = template;
		this.pageIndex = 0;
		if (template == null) {
			resultPane.print("正在识别模板，请稍候...");
			updatePageControls();
			return;
		}
		resultPane.clear();

		if (showPage(0)) {
			buildTemplateSummary(template);
		}
	}

	private boolean showPage(int index) {
		if (template == null) {
			previewPanel.clearData();
			updatePageControls();
			return false;
		}
		List<File> imageFiles = template.pictureFiles();
		if (imageFiles.isEmpty()) {
			imageFiles = List.of(template.pictureFile());
		}
		if (index < 0 || index >= imageFiles.size()) {
			return false;
		}
		File imageFile = imageFiles.get(index);
		try {
			BufferedImage image = ImageIO.read(imageFile);
			if (image == null) {
				resultPane.print("无法预览该模板图片。\n" + imageFile.getAbsolutePath());
				return false;
			}
			this.pageIndex = index;
			previewPanel.setData(image, template);
			previewPanel.setPageIndex(index);
			updatePageControls();
			return true;
		} catch (IOException ex) {
			resultPane.print("读取模板图片失败：\n" + ex.getMessage());
			updatePageControls();
			return false;
		}
	}

	private void updatePageControls() {
		int pageCount = template == null ? 0 : Math.max(template.pageCount(), template.pictureFiles().size());
		boolean multiPage = pageCount > 1;
		pagePanel.setVisible(multiPage);
		pageLabel.setText(multiPage ? (pageIndex + 1) + " / " + pageCount : "");
		prevPageButton.setEnabled(multiPage && pageIndex > 0);
		nextPageButton.setEnabled(multiPage && pageIndex < pageCount - 1 && pageIndex + 1 < template.pictureFiles().size());
	}

	public Template getTemplate() {
		return template;
	}

	private void buildTemplateSummary(Template template) {
		AnswerSheet sheet = template.answerSheet();
		resultPane.clear();
		resultPane.println("模板名：" + template.name());
		resultPane.println("页数：" + template.pageCount());
		resultPane.println("图像尺寸：" + sheet.getImageWidth() + " x " + sheet.getImageHeight());
		resultPane.println("");
		resultPane.println("准考证号位数：" + sheet.getExamIdDigits(), QRColorsAndFonts.RED_NORMAL);
		resultPane.println("选择题数量：" + sheet.getChoiceQuestions().size(), QRColorsAndFonts.RED_NORMAL);
		if (sheet.getFillBlankQuestions().isEmpty()) {
			resultPane.println("填空题：无");
		} else {
			resultPane.println("填空题数量：" + sheet.getFillBlankQuestions().size(), QRColorsAndFonts.RED_NORMAL);
		}
		resultPane.println("");
		appendRegion("准考证号区域", template.examRegionRect());
		appendRegion("选择题区域", template.choiceRegionRect());
		for (sg.qr.kiarelemb.grading.model.SubjectiveRegion region : template.subjectiveRegions()) {
			appendRegion(region.name() + "（第 " + (region.pageIndex() + 1) + " 页）", region.region());
		}
		resultPane.println("");
		resultPane.println("红框：准考证号区域");
		resultPane.println("蓝框：选择题区域");
		resultPane.print("绿框：填空题区域");
	}

	private void appendRegion(String label, Rect rect) {
		if (rect == null) {
			resultPane.println(label + "：未保存区域大框");
			return;
		}
		resultPane.println(label + "：X=" + rect.x() + "，Y=" + rect.y()
						   + "，宽：" + rect.width() + "，高：" + rect.height());
	}

	static String buildShortSummary(AnswerSheet sheet) {
		StringBuilder summary = new StringBuilder();
		summary.append("选择题").append(sheet.getChoiceQuestions().size()).append("道");
		if (sheet.getFillBlankQuestions().isEmpty()) {
			summary.append("，无填空题");
		} else {
			summary.append("，填空题").append(sheet.getFillBlankQuestions().size()).append("道");
		}
		return summary.toString();
	}
}
