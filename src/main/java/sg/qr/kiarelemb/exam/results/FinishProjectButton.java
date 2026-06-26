package sg.qr.kiarelemb.exam.results;

import method.qr.kiarelemb.utils.QRFileUtils;
import sg.qr.kiarelemb.MainWindow;
import sg.qr.kiarelemb.exam.processing.DocumentPageLoader;
import swing.qr.kiarelemb.window.enhance.QROpinionDialog;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public final class FinishProjectButton extends ResultsActionButton {
	public static final FinishProjectButton END_PROJECT = new FinishProjectButton();

	private FinishProjectButton() {
		super("结束流程");
		setEnabled(false);
		addClickAction(event -> endCurrentProject());
	}

	private void endCurrentProject() {
		ResultsPanel projectEnd = currentProjectEnd();
		if (projectEnd == null) {
			return;
		}
		File projectFile = new File(projectEnd.project().projectFilePath());
		int choice = QROpinionDialog.messageInfoShow(MainWindow.INSTANCE,
				"确认结束当前批阅流程并删除项目文件吗？\n" + projectFile.getAbsolutePath());
		if (choice != QROpinionDialog.OK) {
			return;
		}
		try {
			Files.deleteIfExists(projectFile.toPath());
			askDeleteRenderedPdfImages(projectEnd);
			setEnabled(false);
			QROpinionDialog.messageTellShow(MainWindow.INSTANCE, "批阅流程已结束，项目文件已删除。");
			MainWindow.INSTANCE.showStartPanel();
		} catch (IOException ex) {
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, "删除项目文件失败：\n" + ex.getMessage());
		}
	}

	private void askDeleteRenderedPdfImages(ResultsPanel projectEnd) {
		File answerDirectory = new File(projectEnd.project().answerDirectoryPath());
		List<File> renderedImages = DocumentPageLoader.renderedPdfImagesForDirectory(answerDirectory);
		if (renderedImages.isEmpty()) {
			return;
		}
		int choice = QROpinionDialog.messageInfoShow(MainWindow.INSTANCE,
				"是否删除本次由 PDF 转换生成的临时图片？\n共 " + renderedImages.size() + " 个文件。");
		if (choice != QROpinionDialog.OK) {
			return;
		}
		renderedImages.forEach(QRFileUtils::fileDelete);
	}
}