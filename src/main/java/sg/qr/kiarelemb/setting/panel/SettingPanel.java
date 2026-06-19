package sg.qr.kiarelemb.setting.panel;

import sg.qr.kiarelemb.setting.SettingWindow;
import swing.qr.kiarelemb.basic.QRPanel;

/**
 * @author Kiarelemb QR
 * @program: 智能阅卷系统
 * @description:
 * @create 2023-01-31 15:11
 **/
public abstract class SettingPanel extends QRPanel {
	public final SettingWindow window;

	public SettingPanel(SettingWindow window, String name) {
		super(null);
		this.window = window;
		setName(name);
	}
}