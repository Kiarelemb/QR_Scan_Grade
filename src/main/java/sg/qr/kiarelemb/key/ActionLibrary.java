package sg.qr.kiarelemb.key;

import swing.qr.kiarelemb.inter.QRActionRegister;

import java.awt.event.KeyEvent;

/**
 * @author Kiarelemb
 * @projectName LYTyper
 * @className ActionLibrary
 * @description 用于存放诸多操作的库
 * @create 2024/7/31 下午11:17
 */
public class ActionLibrary {
    private ActionLibrary() {
        throw new UnsupportedOperationException();
    }

    public static final QRActionRegister<KeyEvent> KEY_TYPE_ACTION = (e) -> {

        // for example
    };
}