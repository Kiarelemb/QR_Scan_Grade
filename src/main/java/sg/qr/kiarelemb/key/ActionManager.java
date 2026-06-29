package sg.qr.kiarelemb.key;

import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import sg.qr.kiarelemb.data.Keys;
import swing.qr.kiarelemb.QRSwing;
import swing.qr.kiarelemb.inter.QRActionRegister;

/**
 * @author Kiarelemb
 * @projectName LYTyper
 * @className ActionManager
 * @description TODO
 * @create 2024/6/27 下午10:07
 */
public class ActionManager {
	/**
	 * 快捷键事件名 {@code name} 与快捷键键值 {@code key}  对
	 */
	private static final BidiMap<String, String> NAME_KEY_MAP = new DualHashBidiMap<>();
	/**
	 * 快捷键 {@code strokeKey} 与快捷键事件 {@code action}  对。快捷键与快捷键键值对作为外键存在已有的 {@link QRSwing#GLOBAL_PROP}
	 */
	private static final BidiMap<String, QRActionRegister> QUICK_KEY_ACTION_MAP = new DualHashBidiMap<>();

	public static void putAction(String name, String strokeKey, QRActionRegister action, boolean mainWindowFocus) {
		String key = Keys.strValue(strokeKey);
		QRSwing.registerGlobalAction(key, action, mainWindowFocus);
		NAME_KEY_MAP.put(name, strokeKey);
		QUICK_KEY_ACTION_MAP.put(key, action);
	}

	public static QRActionRegister getActionByName(String name) {
		return QUICK_KEY_ACTION_MAP.get(getStrokeKeyByName(name));
	}

	public static String getStrokeKeyByName(String name) {
		return getStrokeKeyByKey(NAME_KEY_MAP.get(name));
	}

	public static String getStrokeKeyByKey(String key) {
		return Keys.strValue(key);
	}
}