package sg.qr.kiarelemb;

import method.qr.kiarelemb.utils.*;
import sg.qr.kiarelemb.data.Keys;
import sg.qr.kiarelemb.res.Info;
import swing.qr.kiarelemb.QRSwing;
import swing.qr.kiarelemb.task.QRTaskRunner;
import swing.qr.kiarelemb.window.utils.QRProgressDialog;

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
		QRLoggerUtils.classMsgMaxLength = 130;
		QRLoggerUtils.initLogger(Level.INFO, Level.CONFIG);
		logger = QRLoggerUtils.getLogger(Enter.class);
		QRTimeCountUtil qcu = new QRTimeCountUtil();
		QRSwing.start("res/settings/setting.properties", "res/settings/window.properties");
//		LogTextPane.LOG_TEXT_PANE.init();
		logger.info("************************************** 项目开始启动 **************************************");
		logger.info("QRSwing 框架加载完毕，" + qcu.endAndGet());
		// 设置窗口图标
		QRSwing.windowIcon = new ImageIcon(Info.ICON_PNG_PATH);
		QRSwing.setWindowTitleMenu(true);

		QRProgressDialog loadDialog = new QRProgressDialog(null, false)
				.setIndeterminate(true)
				.setCancelButtonVisible(false)
				.setProgressDescription("");
		QRTaskRunner.run(context -> {
			loadDialog.setVisible(true);
			return "完成";
		});

		variousLoad();

		logger.info("-------------------------------------- 配置加载完毕 --------------------------------------");
		loadDialog.setVisible(false);
		QRSystemUtils.setWindowShowSlowly(MainWindow.INSTANCE, QRSwing.windowTransparency);
	}

	private static void variousLoad() {
		logger.info("当前系统：" + QRSystemUtils.getSystemName());

//		QRSleepUtils.sleepSec(10);

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

		// 关闭第三方库的无害警告日志
		Logger.getLogger("com.github.kwhat.jnativehook").setLevel(Level.OFF);     // XkbGetKeyboard 错误
		Logger.getLogger("org.apache.pdfbox").setLevel(Level.OFF);              // 字体回退警告
		Logger.getLogger("org.apache.fontbox").setLevel(Level.OFF);             // 字体缺少 PostScript 名
		QRSwing.registerGlobalKeyEvents();

		QRSwing.registerGlobalEventWindow(MainWindow.INSTANCE);

	}
}