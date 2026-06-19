package sg.qr.kiarelemb.menu.type;

import sg.qr.kiarelemb.MainWindow;
import sg.qr.kiarelemb.data.Keys;
import sg.qr.kiarelemb.menu.MenuItem;
import sg.qr.kiarelemb.setting.SettingWindow;
import swing.qr.kiarelemb.QRSwing;
import swing.qr.kiarelemb.inter.QRActionRegister;

import java.awt.event.ActionEvent;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Kiarelemb QR
 * @program: 智能阅卷系统
 * @description:
 * @create 2023-01-21 20:51
 **/
public class SettingsItem extends MenuItem {

	public static final Map<String, String> CHANGE_MAP = new TreeMap<>();
	public static final Map<String, QRActionRegister<Object>> SAVE_ACTIONS = new TreeMap<>();
	public static final Map<String, QRActionRegister<Object>> CANCEL_ACTIONS = new TreeMap<>();
	public static final SettingsItem SETTINGS_ITEM = new SettingsItem();

	private SettingsItem() {
		super("设置", Keys.QUICK_KEY_SETTING_WINDOW);
	}

	@Override
	protected void actionEvent(ActionEvent o) {
		CHANGE_MAP.clear();
		SAVE_ACTIONS.clear();
		CANCEL_ACTIONS.clear();
        SettingWindow window = SettingWindow.INSTANCE;
		window.setLocationRelativeTo(MainWindow.INSTANCE);
		window.setVisible(true);
		if (window.save()) {
			CHANGE_MAP.forEach(QRSwing::setGlobalSetting);
			SAVE_ACTIONS.forEach((s, e) -> e.action(null));
			return;
		}
		CANCEL_ACTIONS.forEach((s, e) -> e.action(null));
	}
}