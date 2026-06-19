package sg.qr.kiarelemb.component;

import org.bytedeco.opencv.opencv_core.Rect;
import sg.qr.kiarelemb.grading.model.AnswerSheet;
import sg.qr.kiarelemb.grading.model.Question;
import sg.qr.kiarelemb.grading.model.Template;
import swing.qr.kiarelemb.theme.QRColorsAndFonts;
import swing.qr.kiarelemb.utils.PicturePanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;

public class AnswerSheetPreviewPanel extends PicturePanel {
	private static final Font TEXT_FONT = QRColorsAndFonts.createFont(18);

	private final String emptyText;
	private final Color examColor;
	private final Color choiceColor;
	private final Color fillColor;
	private Template template;
	private boolean colorInverted;
	private Consumer<Point> imageClickListener;

	public AnswerSheetPreviewPanel(String emptyText, Dimension preferredSize, Dimension minimumSize,
								   Color examColor, Color choiceColor, Color fillColor) {
		this.emptyText = emptyText;
		this.examColor = examColor;
		this.choiceColor = choiceColor;
		this.fillColor = fillColor;
		setPreferredSize(preferredSize);
		setMinimumSize(minimumSize);
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
	}

	public void setData(BufferedImage image, Template template) {
		setData(image, template, true);
	}

	public void setData(BufferedImage image, Template template, boolean resetView) {
		this.image = image;
		this.pictureSize = image == null ? null : new Dimension(image.getWidth(), image.getHeight());
		this.template = template;
		if (resetView) {
			resetView();
			setZoom(1.0);
		}
		repaint();
	}

	public void clearData() {
		this.image = null;
		this.pictureSize = null;
		this.template = null;
		resetView();
		setZoom(1.0);
		repaint();
	}

	@Override
	protected void mouseEnter(MouseEvent e) {
		setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
	}

	@Override
	protected void mouseRelease(MouseEvent e) {
		super.mouseRelease(e);
		setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
	}

	@Override
	protected void mouseClick(MouseEvent e) {
		Point imagePoint = toImagePoint(e.getPoint());
		if (imagePoint != null && imageClickListener != null) {
			imageClickListener.accept(imagePoint);
		}
		if (e.getClickCount() >= 2) {
			resetView();
			setZoom(1.0);
			repaint();
		}
	}

	public void setImageClickListener(Consumer<Point> imageClickListener) {
		this.imageClickListener = imageClickListener;
	}

	private Point toImagePoint(Point panelPoint) {
		if (image == null || panelPoint == null) {
			return null;
		}
		DrawBox box = pictureBox();
		if (panelPoint.x < box.x() || panelPoint.y < box.y()
			|| panelPoint.x > box.x() + box.w() || panelPoint.y > box.y() + box.h()) {
			return null;
		}
		int x = (int) Math.round((panelPoint.x - box.x()) * ((double) box.baseW() / Math.max(1, box.w())));
		int y = (int) Math.round((panelPoint.y - box.y()) * ((double) box.baseH() / Math.max(1, box.h())));
		x = Math.max(0, Math.min(box.baseW() - 1, x));
		y = Math.max(0, Math.min(box.baseH() - 1, y));
		return new Point(x, y);
	}

	@Override
	protected void mouseWheel(MouseWheelEvent e) {
		e.consume();
		double factor = e.getWheelRotation() < 0 ? 1.12 : 1 / 1.12;
		double newZoom = Math.max(0.25, Math.min(5.0, zoom() * factor));
		setZoom(newZoom, e.getPoint());
	}

	@Override
	protected boolean resizeWithZoom() {
		return false;
	}

	@Override
	protected DrawBox pictureBox() {
		if (image == null) {
			return super.pictureBox();
		}
		int panelW = Math.max(1, getWidth() - 24);
		int panelH = Math.max(1, getHeight() - 24);
		double fitScale = Math.min((double) panelW / image.getWidth(), (double) panelH / image.getHeight());
		double scale = fitScale * zoom();
		int drawW = Math.max(1, (int) Math.round(image.getWidth() * scale));
		int drawH = Math.max(1, (int) Math.round(image.getHeight() * scale));
		int x = (getWidth() - drawW) / 2 + panX();
		int y = (getHeight() - drawH) / 2 + panY();
		int baseW = image.getWidth();
		int baseH = image.getHeight();
		if (template != null) {
			AnswerSheet sheet = template.answerSheet();
			if (sheet.getImageWidth() > 0 && sheet.getImageHeight() > 0) {
				baseW = sheet.getImageWidth();
				baseH = sheet.getImageHeight();
			}
		}
		return new DrawBox(x, y, drawW, drawH, Math.max(1, baseW), Math.max(1, baseH));
	}

	@Override
	protected void paintPicture(Graphics2D g2, DrawBox box) {
		if (image == null) {
			drawCenteredText(g2, emptyText);
			return;
		}
		if (colorInverted) {
			BufferedImage inverted = invertedImage(image);
			g2.drawImage(inverted, box.x(), box.y(), box.w(), box.h(), null);
			return;
		}
		g2.drawImage(image, box.x(), box.y(), box.w(), box.h(), null);
	}

	private BufferedImage invertedImage(BufferedImage source) {
		BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D imageGraphics = result.createGraphics();
		imageGraphics.drawImage(source, 0, 0, null);
		imageGraphics.dispose();
		for (int y = 0; y < result.getHeight(); y++) {
			for (int x = 0; x < result.getWidth(); x++) {
				int argb = result.getRGB(x, y);
				int alpha = argb & 0xff000000;
				int red = 255 - ((argb >> 16) & 0xff);
				int green = 255 - ((argb >> 8) & 0xff);
				int blue = 255 - (argb & 0xff);
				result.setRGB(x, y, alpha | (red << 16) | (green << 8) | blue);
			}
		}
		return result;
	}

	public void setColorInverted(boolean colorInverted) {
		this.colorInverted = colorInverted;
		repaint();
	}

	public boolean colorInverted() {
		return colorInverted;
	}

	@Override
	protected void paintPictureOverlay(Graphics2D g2, DrawBox box) {
		if (image == null || template == null) {
			return;
		}
		drawTemplateRegion(g2, box, template.examRegionRect(), examColor, "准考证号");
		drawTemplateRegion(g2, box, template.choiceRegionRect(), choiceColor, "选择题");
		for (sg.qr.kiarelemb.grading.model.SubjectiveRegion region : template.subjectiveRegions()) {
			drawTemplateRegion(g2, box, region.region(), fillColor, region.name());
		}
		paintOverlay(g2, box);
	}

	private void drawTemplateRegion(Graphics2D g2, DrawBox box, Rect region, Color color, String label) {
		Rect target = region == null ? fallbackRegion(label) : region;
		if (target == null) {
			return;
		}
		drawRect(g2, box, target.x(), target.y(), target.width(), target.height(), color, label);
	}

	protected void paintOverlay(Graphics2D g2, DrawBox box) {
	}

	protected Rectangle scaleRect(DrawBox box, Rect rect) {
		double sx = (double) box.w() / box.baseW();
		double sy = (double) box.h() / box.baseH();
		return new Rectangle(
				box.x() + (int) Math.round(rect.x() * sx),
				box.y() + (int) Math.round(rect.y() * sy),
				(int) Math.round(rect.width() * sx),
				(int) Math.round(rect.height() * sy)
		);
	}

	private Rect fallbackRegion(String label) {
		if (template == null) return null;
		if ("准考证号".equals(label)) {
			return groupBounds(template.answerSheet().getExamIdQuestions());
		}
		if ("选择题".equals(label)) {
			return groupBounds(template.answerSheet().getChoiceQuestions());
		}
		if ("填空题".equals(label)) {
			return groupBounds(template.answerSheet().getFillBlankQuestions());
		}
		return null;
	}

	protected Rect groupBounds(List<Question> questions) {
		if (questions == null || questions.isEmpty()) return null;
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (Question question : questions) {
			for (Rect rect : question.optionRegions()) {
				minX = Math.min(minX, rect.x());
				minY = Math.min(minY, rect.y());
				maxX = Math.max(maxX, rect.x() + rect.width());
				maxY = Math.max(maxY, rect.y() + rect.height());
			}
		}
		if (minX == Integer.MAX_VALUE) return null;
		return new Rect(minX, minY, maxX - minX, maxY - minY);
	}

	private void drawRect(Graphics2D g2, DrawBox box, int x, int y, int w, int h, Color color, String label) {
		double sx = (double) box.w() / box.baseW();
		double sy = (double) box.h() / box.baseH();
		int rx = box.x() + (int) Math.round(x * sx);
		int ry = box.y() + (int) Math.round(y * sy);
		int rw = (int) Math.round(w * sx);
		int rh = (int) Math.round(h * sy);

		g2.setStroke(new BasicStroke(2.4f));
		g2.setColor(color);
		g2.drawRect(rx, ry, rw, rh);

		Font oldFont = g2.getFont();
		g2.setFont(TEXT_FONT.deriveFont(Font.BOLD, 13f));
		FontMetrics fm = g2.getFontMetrics();
		int labelW = fm.stringWidth(label) + 12;
		int labelH = fm.getHeight() + 4;
		int labelY = Math.max(box.y() + 2, ry - labelH);
		g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 220));
		g2.fillRoundRect(rx, labelY, labelW, labelH, 6, 6);
		g2.setColor(Color.WHITE);
		g2.drawString(label, rx + 6, labelY + fm.getAscent() + 2);
		g2.setFont(oldFont);
	}

	private void drawCenteredText(Graphics2D g2, String text) {
		g2.setFont(TEXT_FONT);
		FontMetrics fm = g2.getFontMetrics();
		g2.setColor(Color.DARK_GRAY);
		g2.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, getHeight() / 2);
	}
}
