package sg.qr.kiarelemb.component;

import sg.qr.kiarelemb.menu.type.SettingsItem;
import sg.qr.kiarelemb.data.Keys;
import swing.qr.kiarelemb.basic.QRLabel;

/**
 * @author Kiarelemb QR
 * @program: 智能阅卷系统
 * @description:
 * @create 2023-02-08 15:23
 **/
public class Label extends QRLabel {
	private final String key;

	public Label(String key) {
		super(Keys.strValue(key));
		this.key = key;
	}

	@Override
	public void setText(String text) {
		super.setText(text);
		if (this.key != null) {
			SettingsItem.CHANGE_MAP.put(this.key, text);
		}
	}
}