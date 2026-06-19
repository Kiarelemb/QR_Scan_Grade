package sg.qr.kiarelemb;

import method.qr.kiarelemb.utils.*;
import sg.qr.kiarelemb.data.Keys;
import sg.qr.kiarelemb.res.Info;
import sg.qr.kiarelemb.setting.SettingWindow;
import swing.qr.kiarelemb.QRSwing;

import javax.swing.*;
import java.awt.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Kiarelemb QR
 * @program: 智能阅卷系统
 * @apiNote: 智能阅卷系统主方法类
 * @create 2023-01-12 22:51
 **/
public class Enter {

	private static Logger logger;

	public static void main(String[] args) {
		// 初始化 JDK 自带的 Logger
		QRLoggerUtils.prefix = "sg";
		QRLoggerUtils.initLogger(Level.INFO, Level.CONFIG);
		QRLoggerUtils.classMsgMaxLength = 120;
		logger = QRLoggerUtils.getLogger(Enter.class);
		QRTimeCountUtil qcu = new QRTimeCountUtil();
		QRSwing.start("res/settings/setting.properties", "res/settings/window.properties");
//		LogTextPane.LOG_TEXT_PANE.init();
		logger.info("************************************** 项目开始启动 **************************************");
		logger.info("QRSwing 框架加载完毕，" + qcu.endAndGet());
		// 设置窗口图标
		QRSwing.windowIcon = new ImageIcon(Info.ICON_PNG_PATH);
		QRSwing.setWindowTitleMenu(true);

//		FlashLoadingWindow flw = new FlashLoadingWindow();
//		flw.setVisible(true);

		variousLoad();

//		flw.setVisible(false);

		logger.info("-------------------------------------- 配置加载完毕 --------------------------------------");
		QRSystemUtils.setWindowShowSlowly(MainWindow.INSTANCE, QRSwing.windowTransparency);
	}


	private static final String INPUT_PATH = "ans/AnswerSheet_test_001.png";
	private static final String OUTPUT_DIR = "ans/output/";


	private static void variousLoad() {
		logger.info("当前系统：" + QRSystemUtils.getSystemName());

		//region 全局界面字体
		Font font = null;
		boolean fontEnable = Keys.boolValue(Keys.TEXT_FONT_NAME_GLOBAL_ENABLE);
		if (fontEnable) {
			String fontNameOrPath = Keys.strValue(Keys.TEXT_FONT_NAME_GLOBAL);
			if (QRFileUtils.fileExists(fontNameOrPath)) {
				logger.config("加载字体：" + fontNameOrPath);
				font = QRFontUtils.loadFontFromFile(10, fontNameOrPath);
			} else {
				String[] names = QRFontUtils.getSystemFontNames();
				if (QRArrayUtils.objectIndexOf(names, fontNameOrPath) != -1) {
					font = QRFontUtils.getFont(fontNameOrPath, 10);
					logger.config("加载字体：" + fontNameOrPath);
				}
			}
		}
		if (font == null) {
			//全局默认字体即为阿里巴巴普惠体
			font = QRFontUtils.loadFontFromFile(18, Info.ALIBABA_FONT_PATH);
			logger.config("加载默认字体：" + font.getFontName());
		}
		QRSwing.customFontName(font);
		//endregion 全局界面字体

		QRSwing.registerGlobalKeyEvents();

		QRSwing.registerGlobalEventWindow(MainWindow.INSTANCE);

		//提前加载一遍试试
		SettingWindow.INSTANCE.setVisible(false);
		logger.config("设置窗口预加载完毕。");

	}
}