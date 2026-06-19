package sg.qr.kiarelemb.start;

import sg.qr.kiarelemb.MainWindow;
import sg.qr.kiarelemb.grading.pipeline.TemplateProcessor;
import swing.qr.kiarelemb.window.enhance.QROpinionDialog;

import java.awt.event.ActionEvent;
import java.io.File;

/**
 * @author Kiarelemb
 * @projectName QR_Scan_Grade
 * @className ExistTemplateBtn
 * @description TODO
 * @create 2026/6/4 12:22
 */
public class ExistTemplateBtn extends StartBtn {

	public static final ExistTemplateBtn EXIST_TEMPLATE_BTN = new ExistTemplateBtn();

	private ExistTemplateBtn() {
		super("从现有模板创建");
		setToolTipText("从已有的模板中，开始新的批阅流程。");
	}

	@Override
	protected void actionEvent(ActionEvent o) {
		File TEMPLATE_DIR = new File("sg");
		File[] files = TEMPLATE_DIR.listFiles(file ->
				file.isFile() && file.getName().toLowerCase().endsWith("." + TemplateProcessor.TEMPLATE_EXTENSION));
		if (files == null || files.length == 0) {
			QROpinionDialog.messageTellShow(MainWindow.INSTANCE, "sg 目录下暂无答题卡模板。");
			return;
		}

		MainWindow.INSTANCE.setCursorWait();
		try {
			MainWindow.INSTANCE.setCenterComponent(new ExistTmptPanel());
		} finally {
			MainWindow.INSTANCE.setCursorDefault();
		}
	}
}