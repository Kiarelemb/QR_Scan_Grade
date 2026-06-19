package sg.qr.kiarelemb.start;

import swing.qr.kiarelemb.basic.QRPanel;

import java.awt.*;

/**
 * @author Kiarelemb
 * @projectName QR_Scan_Grade
 * @className StartPanel
 * @description TODO
 * @create 2026/6/4 12:19
 */
public class StartPanel extends QRPanel {

	public static final StartPanel START_PANEL = new StartPanel();

	private StartPanel() {
		setLayout(new FlowLayout(FlowLayout.CENTER, 20, 20));
		add(NewTemplateBtn.NEW_TEMPLATE_BTN);
		add(ContinueProjectBtn.CONTINUE_PROJECT_BTN);
		add(ExistTemplateBtn.EXIST_TEMPLATE_BTN);
	}
}