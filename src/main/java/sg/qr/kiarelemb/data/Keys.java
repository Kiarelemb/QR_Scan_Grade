package sg.qr.kiarelemb.data;

import method.qr.kiarelemb.utils.QRFileUtils;
import method.qr.kiarelemb.utils.QRStringUtils;
import method.qr.kiarelemb.utils.QRTimeUtils;
import sg.qr.kiarelemb.res.Info;
import swing.qr.kiarelemb.resource.QRSwingInfo;

import java.util.Map;
import java.util.TreeMap;

/**
 * @author Kiarelemb QR
 * @program: 智能阅卷系统
 * @apiNote : 各键的存放位置
 * @create 2023-01-22 14:16
 **/
public class Keys {

	//region 读取配置的静态方法

	/**
	 * 读取配置
	 *
	 * @param key 键
	 * @return 值
	 */
	public static int intValue(String key) {
		return QRSwingInfo.intValue(DEFAULT_MAP, key);
	}

	/**
	 * 读取配置
	 *
	 * @param key 键
	 * @return 值
	 */
	public static boolean boolValue(String key) {
		return QRSwingInfo.boolValue(DEFAULT_MAP, key);
	}

	/**
	 * 读取配置
	 *
	 * @param key 键
	 * @return 值
	 */
	public static String strValue(String key) {
		return QRSwingInfo.strValue(DEFAULT_MAP, key);
	}

	/**
	 * 读取配置
	 *
	 * @param key 键
	 * @return 值
	 */
	public static float floatValue(String key) {
		return QRSwingInfo.floatValue(DEFAULT_MAP, key);
	}
	//endregion

	//region int类型
	/**
	 * 记录主窗体分割面板的分割值
	 */
	public static final String WINDOW_SPLIT_WEIGHT = "window.split.weight";
	//endregion int类型

	//region boolean 类型

	/**
	 * 全局的界面字体，{@code false} （默认）不启用，{@code true} 启用
	 */
	public static final String TEXT_FONT_NAME_GLOBAL_ENABLE = "text.font.name.global.enable";
	//endregion boolean 类型

	//region String 类型
	/**
	 * 全局的界面字体，默认为 阿里巴巴普惠体 R
	 */
	public static final String TEXT_FONT_NAME_GLOBAL = "text.font.name.global";
	/**
	 * 今日跟打字数的最后更新日期
	 */
	public static final String TYPE_WORD_TOTAL_LAST_UPDATE = "type.word.total.last.update";

	public static final String SELECTED_FILE_DIRECTORY = "selected.file.directory";
	public static final String DEEPSEEK_API_KEYS = "deepseek.api.keys";
	public static final String DEEPSEEK_CONCURRENCY = "deepseek.concurrency";
	public static final String OCR_PROVIDER = "ocr.provider";
	public static final String BAIDU_OCR_API_KEY = "baidu.ocr.api.key";
	public static final String BAIDU_OCR_SECRET_KEY = "baidu.ocr.secret.key";
	public static final String GOOGLE_OCR_API_KEY = "google.ocr.api.key";
	//region 快捷键设置
	/**
	 * 输入法码表路径，默认为 {@code null}
	 */
	public static final String INPUT_CODE_DICT_PATH = "input.code.dict.path";
	/**
	 * 新建发文快捷键，默认为 {@code F2}
	 */
	public static final String QUICK_KEY_NEW_SEND = "quick.key.new.send";
	/**
	 * 重打快捷键，默认为 {@code F3}
	 */
	public static final String QUICK_KEY_RESTART = "quick.key.restart";
	/**
	 * 暂停快捷键，默认为  {@code F9}
	 */
	public static final String QUICK_KEY_PAUSE = "quick.key.pause";
	/**
	 * 菜单快捷键之载文，默认为 {@code F4}
	 */
	public static final String QUICK_KEY_MENU_TYPE_TEXT_LOAD = "quick.key.menu.type.text.load";
	/**
	 * 换群快捷键，默认为 {@code F5}
	 */
	public static final String QUICK_KEY_GROUP = "quick.key.group";
	/**
	 * 乱序快捷键，默认为 {@code F8}
	 */
	public static final String QUICK_KEY_TEXT_MIX = "quick.key.text.mix";
	/**
	 * 继续发文快捷键，默认为 {@code F9}
	 */
	public static final String QUICK_KEY_SEND_CONTINUE = "quick.key.send.continue";
	/**
	 * 结束发文快捷键，默认为 {@code Ctrl E}
	 */
	public static final String QUICK_KEY_SEND_END = "quick.key.send.end";
	/**
	 * 下一段快捷键，默认为 {@code F10}
	 */
	public static final String QUICK_KEY_SEND_NEXT_PARA = "quick.key.send.para.next";
	/**
	 * 上一段快捷键，默认为 {@code Ctrl F}
	 */
	public static final String QUICK_KEY_SEND_PARA_FORE = "quick.key.send.para.fore";
	/**
	 * 菜单快捷键之设置，默认为 {@code Ctrl Z}
	 */
	public static final String QUICK_KEY_SETTING_WINDOW = "quick.key.menu.type.setting";
	/**
	 * 当量窗体显示快捷键，默认为 {@code Ctrl D}
	 */
	public static final String QUICK_KEY_DANG_LIANG_WINDOW = "quick.key.dang.liang.window";
	/**
	 * 内置输入框启用快捷键，默认为 {@code Ctrl I}
	 */
	public static final String QUICK_KEY_INNER_INPUT_WINDOW = "quick.key.inner.input.window";
	//endregion 快捷键设置

	//endregion String 类型

	//region float 类型
	/**
	 * 行距，默认为 {@code 0.8}
	 */
	public static final String TEXT_LINE_SPACE = "text.line.space";
	//endregion float 类型

	public static final Map<String, String> DEFAULT_MAP = new TreeMap<>() {
		{
			QRFileUtils.fileReaderWithUtf8(Info.RESOURCE_DIRECTORY + "default_settings.properties", (lineText -> {
				String[] split = QRStringUtils.stringSplit(lineText, '=', true);
				put(split[0], split[1]);
			}));
			putIfAbsent(DEEPSEEK_API_KEYS, "");
			putIfAbsent(DEEPSEEK_CONCURRENCY, "4");
			putIfAbsent(OCR_PROVIDER, "baidu");
			putIfAbsent(BAIDU_OCR_API_KEY, "");
			putIfAbsent(BAIDU_OCR_SECRET_KEY, "");
			putIfAbsent(GOOGLE_OCR_API_KEY, "");
			put(TYPE_WORD_TOTAL_LAST_UPDATE, QRTimeUtils.getDateNow());
		}
	};
}