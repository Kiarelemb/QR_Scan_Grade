package sg.qr.kiarelemb.start;

import sg.qr.kiarelemb.MainWindow;
import sg.qr.kiarelemb.data.Keys;
import sg.qr.kiarelemb.grading.pipeline.DocumentImageLoader;
import swing.qr.kiarelemb.QRSwing;
import swing.qr.kiarelemb.window.enhance.QROpinionDialog;
import swing.qr.kiarelemb.window.utils.QRFileSelectDialog;
import swing.qr.kiarelemb.window.utils.QRPicturePreviewDialog;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;

import static sg.qr.kiarelemb.data.Keys.SELECTED_FILE_DIRECTORY;

public class NewTemplateBtn extends StartBtn {

	public static final NewTemplateBtn NEW_TEMPLATE_BTN = new NewTemplateBtn();

	private NewTemplateBtn() {
		super("新建答题卡模板");
		setToolTipText("导入高清答题卡扫描图或 PDF，以开始新的批阅流程。");
	}

	@Override
	protected void actionEvent(ActionEvent event) {
		while (true) {
			MainWindow.INSTANCE.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

			String dir = Keys.strValue(SELECTED_FILE_DIRECTORY);
			File dirFile = new File(dir);
			QRFileSelectDialog fileSelect = new QRFileSelectDialog(MainWindow.INSTANCE,
					QRFileSelectDialog.SelectMode.FILE_ONLY, dirFile, "请选择图片或 PDF 文档", "pdf", "jpg", "png");
			MainWindow.INSTANCE.setCursor(Cursor.getDefaultCursor());

			fileSelect.setVisible(true);
			if (!fileSelect.selectedSucceeded()) {
				return;
			}

			String parent = fileSelect.selectedFile().getParent();
			QRSwing.setGlobalSetting(SELECTED_FILE_DIRECTORY, parent);
			QRPicturePreviewDialog dialog = getPicturePreviewDialog(fileSelect);
			if (dialog.isConfirmed()) {
				break;
			}
		}
	}

	private static QRPicturePreviewDialog getPicturePreviewDialog(QRFileSelectDialog fileSelect) {
		File templateImageFile;
		int pageCount;
		try {
			templateImageFile = DocumentImageLoader.firstImage(fileSelect.selectedFile());
			pageCount = DocumentImageLoader.pdfPageCount(fileSelect.selectedFile());
		} catch (Exception ex) {
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, "读取模板文件失败：\n" + ex.getMessage());
			templateImageFile = fileSelect.selectedFile();
			pageCount = 1;
		}
		File selectedTemplateImage = templateImageFile;
		int selectedPageCount = pageCount;
		QRPicturePreviewDialog dialog = new QRPicturePreviewDialog(MainWindow.INSTANCE, selectedTemplateImage, true) {
			@Override
			protected void sureAction(ActionEvent e) {
				super.sureAction(e);
				NewTmptWindow window = new NewTmptWindow(selectedTemplateImage, selectedPageCount);
				window.setVisible(true);
			}
		};
		dialog.setVisible(true);
		return dialog;
	}
}
