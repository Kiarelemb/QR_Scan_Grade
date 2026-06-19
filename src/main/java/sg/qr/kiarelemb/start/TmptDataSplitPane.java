package sg.qr.kiarelemb.start;

import org.bytedeco.opencv.opencv_core.Rect;
import sg.qr.kiarelemb.component.AnswerSheetPreviewPanel;
import sg.qr.kiarelemb.grading.model.AnswerSheet;
import sg.qr.kiarelemb.grading.model.Template;
import swing.qr.kiarelemb.basic.QRSplitPane;
import swing.qr.kiarelemb.basic.QRTextPane;
import swing.qr.kiarelemb.theme.QRColorsAndFonts;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class TmptDataSplitPane extends QRSplitPane {
	private static final Font TEXT_FONT = QRColorsAndFonts.createFont(18);
	private static final Color EXAM_COLOR = new Color(218, 64, 64);
	private static final Color CHOICE_COLOR = new Color(52, 112, 210);
	private static final Color FILL_COLOR = new Color(46, 150, 92);

	private final QRTextPane resultPane = new QRTextPane();
	private final AnswerSheetPreviewPanel previewPanel = new AnswerSheetPreviewPanel(
			"暂无图片",
			new Dimension(760, 700),
			new Dimension(420, 420),
			EXAM_COLOR,
			CHOICE_COLOR,
			FILL_COLOR
	);
	private Template template;

	public TmptDataSplitPane(Template template) {
		super(JSplitPane.HORIZONTAL_SPLIT);
		initView();
		setTemplate(template);
	}

	private void initView() {
		setResizeWeight(0.68);
		setLeftComponent(previewPanel);
		setRightComponent(resultPane.addScrollPane());
		setDividerLocation(760);

		resultPane.setFont(TEXT_FONT);
		resultPane.setEditableFalseButCursorEdit();
		resultPane.setLineWrap(true);
		resultPane.setLineSpacing(0.4f);
	}

	public void setTemplate(Template template) {
		this.template = template;
		if (template == null) {
			resultPane.print("正在识别模板，请稍候...");
			return;
		}
		resultPane.clear();

		try {
			BufferedImage image = ImageIO.read(template.pictureFile());
			if (image == null) {
				resultPane.print("无法预览该模板图片。\n" + template.pictureFile().getAbsolutePath());
				return;
			}

			previewPanel.setData(image, template);
			buildTemplateSummary(template);
		} catch (IOException ex) {
			resultPane.print("读取模板图片失败：\n" + ex.getMessage());
		}
	}

	public Template getTemplate() {
		return template;
	}

	public void setImageClickListener(java.util.function.Consumer<Point> listener) {
		previewPanel.setImageClickListener(listener);
	}

	private void buildTemplateSummary(Template template) {
		AnswerSheet sheet = template.answerSheet();
		resultPane.clear();
		resultPane.println("模板名：" + template.name());
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
			appendRegion(region.name(), region.region());
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
