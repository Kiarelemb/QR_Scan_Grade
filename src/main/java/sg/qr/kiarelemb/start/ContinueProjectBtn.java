package sg.qr.kiarelemb.start;

import sg.qr.kiarelemb.MainWindow;
import swing.qr.kiarelemb.window.enhance.QROpinionDialog;

import java.awt.event.ActionEvent;
import java.io.File;

/**
 * @author Kiarelemb
 * @projectName QR_Scan_Grade
 * @className ContinueProjectBtn
 * @description TODO
 * @create 2026/6/6 18:07
 */
public class ContinueProjectBtn extends StartBtn {
	public static final ContinueProjectBtn CONTINUE_PROJECT_BTN = new ContinueProjectBtn();
	private static final File PROJECT_DIR = new File("sgp");
	private static final String PROJECT_EXTENSION = "sgp";

	private ContinueProjectBtn() {
		super("继续批阅");
		setToolTipText("继续上次的批阅项目。");
	}

	@Override
	protected void actionEvent(ActionEvent o) {
		File[] files = PROJECT_DIR.listFiles(file ->
				file.isFile() && file.getName().toLowerCase().endsWith("." + PROJECT_EXTENSION));
		if (files == null || files.length == 0) {
			QROpinionDialog.messageTellShow(MainWindow.INSTANCE, "sgp 目录下暂无批阅项目。");
			return;
		}
		ContinueProjectWindow window = new ContinueProjectWindow();
		window.setVisible(true);
	}
}