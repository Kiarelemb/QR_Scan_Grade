package sg.qr.kiarelemb.start;

import sg.qr.kiarelemb.MainWindow;
import sg.qr.kiarelemb.data.Keys;
import sg.qr.kiarelemb.exam.processing.DocumentPageLoader;
import swing.qr.kiarelemb.QRSwing;
import swing.qr.kiarelemb.task.QRTaskRunner;
import swing.qr.kiarelemb.window.enhance.QROpinionDialog;
import swing.qr.kiarelemb.window.utils.QRFileSelectDialog;
import swing.qr.kiarelemb.window.utils.QRPicturePreviewDialog;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;

import static sg.qr.kiarelemb.data.Keys.SELECTED_FILE_DIRECTORY;

public class NewTemplateButton extends StartActionButton {

	public static final NewTemplateButton NEW_TEMPLATE_BTN = new NewTemplateButton();

	private NewTemplateButton() {
		super("新建答题卡模板");
		setToolTipText("导入高清答题卡扫描图或 PDF，以开始新的批阅流程。");
	}

	@Override
	protected void actionEvent(ActionEvent event) {
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
		File selectedFile = fileSelect.selectedFile();

		if (DocumentPageLoader.isPdfFile(selectedFile)) {
			convertPdfAndShowPreview(selectedFile);
		} else {
			showImagePreview(selectedFile);
		}
	}

	private static void convertPdfAndShowPreview(File pdfFile) {
		QRTaskRunner.runWithProgress(MainWindow.INSTANCE, "正在转换 PDF 模板…",
				context -> DocumentPageLoader.documentImages(pdfFile, context::progress),
				NewTemplateButton::showTemplatePreview,
				error -> QROpinionDialog.messageErrShow(MainWindow.INSTANCE, "读取模板文件失败：\n" + error.getMessage()));
	}

	private static void showImagePreview(File imageFile) {
		try {
			List<File> images = DocumentPageLoader.documentImages(imageFile);
			showTemplatePreview(images);
		} catch (Exception ex) {
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, "读取模板文件失败：\n" + ex.getMessage());
		}
	}

	private static void showTemplatePreview(List<File> images) {
		QRPicturePreviewDialog dialog = new QRPicturePreviewDialog(MainWindow.INSTANCE,
				images.toArray(File[]::new), true) {
			@Override
			protected void sureAction(ActionEvent e) {
				super.sureAction(e);
				NewTemplateDialog window = new NewTemplateDialog(images);
				window.setVisible(true);
			}
		};
		dialog.setVisible(true);
	}
}
