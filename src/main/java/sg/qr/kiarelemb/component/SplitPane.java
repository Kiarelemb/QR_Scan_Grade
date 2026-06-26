package sg.qr.kiarelemb.component;

import sg.qr.kiarelemb.data.Keys;
import sg.qr.kiarelemb.exam.ObjectiveReviewTextPane;
import sg.qr.kiarelemb.start.StartPanel;
import swing.qr.kiarelemb.QRSwing;
import swing.qr.kiarelemb.basic.QRPanel;
import swing.qr.kiarelemb.combination.QRTransparentSplitPane;
import swing.qr.kiarelemb.theme.QRColorsAndFonts;

import java.awt.*;

/**
 * @author Kiarelemb QR
 * @program: 智能阅卷系统
 * @description:
 * @create 2023-01-27 22:33
 **/
public class SplitPane extends QRTransparentSplitPane {
	public static final SplitPane SPLIT_PANE = new SplitPane();
	public final QRPanel topPanel;
	public final QRPanel bottomPanel;

	private SplitPane() {
		super();

		int value;
		try {
			value = Keys.intValue(Keys.WINDOW_SPLIT_WEIGHT);
		} catch (Exception e) {
			value = 300;
			QRSwing.setGlobalSetting(Keys.WINDOW_SPLIT_WEIGHT, value);
		}
		setDividerLocation(value);

		topPanel = new QRPanel(false, new BorderLayout());
		bottomPanel = new QRPanel(false, new BorderLayout());
		topPanel.setPreferredSize(new Dimension(100, value));
		topPanel.add(StartPanel.START_PANEL, BorderLayout.SOUTH);

		setTopComponent(topPanel);
		setBottomComponent(ObjectiveReviewTextPane.CHOICE_CHECK_TEXT_PANE.addScrollPane());
	}

	public void showStartPanel() {
		topPanel.removeAll();
		topPanel.add(StartPanel.START_PANEL, BorderLayout.SOUTH);
		refreshTopPanel();
		ObjectiveReviewTextPane.CHOICE_CHECK_TEXT_PANE.setReviewAnswerChangeListener(null);
		ObjectiveReviewTextPane.CHOICE_CHECK_TEXT_PANE.clearReviewAnswers();
	}

	public void showTopComponent(Component component) {
		topPanel.removeAll();
		topPanel.add(component, BorderLayout.CENTER);
		refreshTopPanel();
	}

	private void refreshTopPanel() {
		topPanel.revalidate();
		topPanel.repaint();
	}

	@Override
	protected void paintDivider(Graphics2D g) {
		super.paintDivider(g);
		g.setColor(QRColorsAndFonts.LINE_COLOR);
		Dimension size = divider.getSize();
		g.fillRect(0, 0, size.width, size.height);
	}
}
