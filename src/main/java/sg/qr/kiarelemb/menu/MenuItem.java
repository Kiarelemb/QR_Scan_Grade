package sg.qr.kiarelemb.menu;

import sg.qr.kiarelemb.data.Keys;
import swing.qr.kiarelemb.basic.QRMenuItem;

/**
 * @author Kiarelemb QR
 * @program: 智能阅卷系统
 * @description:
 * @create 2023-01-25 15:16
 **/
public class MenuItem extends QRMenuItem {
    public MenuItem(String text, String key) {
        super(text, key == null ? null : Keys.strValue(key), true);
    }

    public MenuItem(String text, String key, boolean mainWindowFocus) {
        super(text, key == null ? null : Keys.strValue(key), mainWindowFocus);
    }
}