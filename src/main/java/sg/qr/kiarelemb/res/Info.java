package sg.qr.kiarelemb.res;

import method.qr.kiarelemb.utils.QRFileUtils;
import method.qr.kiarelemb.utils.QRStringUtils;
import method.qr.kiarelemb.utils.QRSystemUtils;

import javax.swing.*;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;

import static java.io.File.separator;

/**
 * @author Kiarelemb QR
 * @program: 智能阅卷系统
 * @description:
 * @create 2023-01-12 23:04
 **/
public class Info {
	public static final String RESOURCE_DIRECTORY = "res" + separator;
	public static final String ARTICLES_DIRECTORY = RESOURCE_DIRECTORY + "articles" + separator;
	public static final String PICK_DIRECTORY = RESOURCE_DIRECTORY + "ico" + separator;
	public static final String DICT_DIRECTORY = RESOURCE_DIRECTORY + "dict" + separator;
	public static final String TYPE_DIRECTORY = RESOURCE_DIRECTORY + "type" + separator;
	public static final String THEME_DIRECTORY = "theme" + separator;
	public static final String TMP_DIRECTORY = "tmp" + separator;
	/**
	 * 存放当量的文件夹
	 */
	public static final String DL_DIRECTORY = "dl" + separator;
	public static final String SOFTWARE_VERSION = "v26.06";
	public static final String SYSTEM_NAME = QRSystemUtils.getSystemName();
	public static final boolean IS_WINDOWS = QRSystemUtils.IS_WINDOWS;
	public static final boolean IS_WINDOWS8Up = QRSystemUtils.IS_WINDOWS && QRStringUtils.pickNumber(SYSTEM_NAME) > 7;
	public static final boolean IS_MACOS = QRSystemUtils.IS_OSX;
	public static final boolean IS_LINUX = QRSystemUtils.IS_LINUX;
	public static final String FLASH_PATH = PICK_DIRECTORY + "flash.png";
	public static final String SINGLES_PATH = "singles.txt";
	public static final String ICON_PNG_PATH = PICK_DIRECTORY + "check_ico.png";
	public static final String ICON_TRAY_PATH = "tray.png";
	public static final String SPEED_ICON = "speed.png";
	public static final String KEYSTROKE_PNG = "keystroke.png";
	public static final String CODE_LEN_PNG = "codeLen.png";
	public static final String STANDARD_LEN_PNG = "standardLen.png";
	public static final String TIME_PNG = "time.png";

	public static final String LCD_FONT_NAME = "DigitalNumbers.ttf";
	public static final String ALIBABA_FONT_PATH = RESOURCE_DIRECTORY + "alibaba.ttf";

	/**
	 * 加载资源
	 *
	 * @param fileName 文件名
	 * @return URI
	 */
	public static URI loadURI(String fileName) {
		try {
			URL url = Objects.requireNonNull(Info.class.getResource(fileName));
			return url.toURI();
		} catch (URISyntaxException e) {
			return URI.create(".");
		}
	}
}