package sg.qr.kiarelemb;

import method.qr.kiarelemb.utils.QRLoggerUtils;
import sg.qr.kiarelemb.component.ProjectStateSaver;
import sg.qr.kiarelemb.component.SplitPane;
import sg.qr.kiarelemb.data.Keys;
import sg.qr.kiarelemb.exam.ObjectiveReviewPanel;
import sg.qr.kiarelemb.exam.ManualScoringPanel;
import sg.qr.kiarelemb.exam.SubjectiveOcrReviewPanel;
import sg.qr.kiarelemb.exam.results.ResultsPanel;
import sg.qr.kiarelemb.exam.model.GradingProject;
import sg.qr.kiarelemb.exam.model.SheetTemplate;
import sg.qr.kiarelemb.exam.processing.DocumentPageLoader;
import sg.qr.kiarelemb.exam.processing.SheetTemplateFileStore;
import sg.qr.kiarelemb.menu.data.EnglishScoreInput;
import sg.qr.kiarelemb.menu.type.SettingsItem;
import sg.qr.kiarelemb.res.Info;
import swing.qr.kiarelemb.QRSwing;
import swing.qr.kiarelemb.basic.QRButton;
import swing.qr.kiarelemb.task.QRTaskRunner;
import swing.qr.kiarelemb.utils.QRComponentUtils;
import swing.qr.kiarelemb.window.basic.QRFrame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * @author Kiarelemb QR
 * @program: 智能阅卷系统
 * @description:
 * @create 2023-01-12 23:17
 **/
public class MainWindow extends QRFrame {
	private static final Logger logger = QRLoggerUtils.getLogger(MainWindow.class);

	public static final MainWindow INSTANCE = new MainWindow();
	private Component centerComponent;

	private MainWindow() {
		super("智能阅卷系统 " + Info.SOFTWARE_VERSION);
		this.mainPanel.setLayout(new BorderLayout());
		setTitlePanel();

		//菜单
		menuInit();

		setCenterComponent(SplitPane.SPLIT_PANE);

		setTitleCenter();
		setCloseButtonSystemExit();
		quickKeyLoad();
		QRComponentUtils.componentLoopToSetOpaque((JComponent) getContentPane(), !QRSwing.windowImageSet);
	}

	private void menuInit() {
		this.titleMenuPanel.setAutoExpend(true);
		QRButton typeMenu = this.titleMenuPanel.add("数据");
		QRButton aboutMenu = this.titleMenuPanel.add("关于");

		typeMenu.add(SettingsItem.SETTINGS_ITEM);
		typeMenu.add(EnglishScoreInput.ENGLISH_SCORE_INPUT);

	}

	public void startProject(File projectFile) {
		startProject(new GradingProject(projectFile));
	}

	public void startProject(GradingProject project) {
		project.read();
		int pageCount = 1;
		try {
			SheetTemplate template = SheetTemplateFileStore.load(new File(project.templateFilePath()));
			pageCount = template.pageCount();
		} catch (Exception ex) {
			logger.warning("Cannot read template page count: " + ex.getMessage());
		}
		int finalPageCount = pageCount;

		// 检查是否需要实际转换 PDF
		File answerDir = new File(project.answerDirectoryPath());
		boolean needsConversion = false;
		try {
			File singlePdf = DocumentPageLoader.singlePdfFile(answerDir);
			if (singlePdf != null
				&& DocumentPageLoader.convertedPdfImages(singlePdf).size() < DocumentPageLoader.pdfPageCount(singlePdf)) {
				needsConversion = true;
			}
		} catch (IOException e) {
			logger.warning("Cannot check PDF state: " + e.getMessage());
		}

		if (needsConversion) {
			QRTaskRunner.runWithProgress(this, "正在加载答卷…",
					context -> DocumentPageLoader.sortedAnswerImages(answerDir, context::progress),
					images -> continueStartProject(project, finalPageCount),
					error -> swing.qr.kiarelemb.window.enhance.QROpinionDialog.messageErrShow(this, "加载答卷失败：\n" + error.getMessage()));
			return;
		}

		continueStartProject(project, pageCount);
	}

	private void continueStartProject(GradingProject project, int pageCount) {
		setCursorWait();
		try {
			if (project.refreshAnswerFilesFromDirectory(pageCount)) {
				project.write();
			}
			if (project.answerFiles() == null || project.answerFiles().isEmpty()) {
				swing.qr.kiarelemb.window.enhance.QROpinionDialog.messageTellShow(this,
						"该项目的答卷文件夹中还没有扫描件。\n请将答卷图片放入：\n" + project.answerDirectoryPath());
				showStartPanel();
				return;
			}
			if (project.index() >= project.answerFiles().size()) {
				project.setIndex(project.answerFiles().size());
				project.write();
				if (shouldContinueSubjectiveReview(project)) {
					showAfterChoiceReview(project);
					return;
				}
				showProjectEnd(project);
			} else {
				showProjectReview(project);
			}
		} finally {
			setCursorDefault();
		}
	}

	private boolean shouldContinueSubjectiveReview(GradingProject project) {
		try {
			SheetTemplate template = SheetTemplateFileStore.load(new File(project.templateFilePath()));
			return shouldShowMachineSubjectiveReview(project, template)
				   || shouldShowManualReview(project, template);
		} catch (Exception ex) {
			logger.warning("Cannot check subjective review progress: " + ex.getMessage());
			return false;
		}
	}

	private boolean shouldShowMachineSubjectiveReview(GradingProject project, SheetTemplate template) {
		return SubjectiveOcrReviewPanel.hasSubjectiveQuestions(project, template)
			   && !project.allSubjectiveAnswersSaved();
	}

	private boolean shouldShowManualReview(GradingProject project, SheetTemplate template) {
		return ManualScoringPanel.hasManualQuestions(project, template)
			   && !ManualScoringPanel.allManualScoresSaved(project, template);
	}

	public void showProjectReview(GradingProject project) {
		setCenterComponent(SplitPane.SPLIT_PANE);
		SplitPane.SPLIT_PANE.showTopComponent(new ObjectiveReviewPanel(project));
	}

	public void showProjectEnd(GradingProject project) {
		setCenterComponent(new ResultsPanel(project));
	}

	public void showAfterChoiceReview(GradingProject project) {
		try {
			SheetTemplate template = SheetTemplateFileStore.load(new File(project.templateFilePath()));
			if (shouldShowMachineSubjectiveReview(project, template)) {
				setCenterComponent(new SubjectiveOcrReviewPanel(project, template));
				return;
			}
			if (shouldShowManualReview(project, template)) {
				setCenterComponent(new ManualScoringPanel(project, template));
				return;
			}
		} catch (Exception ex) {
			logger.warning("Cannot load subjective review template: " + ex.getMessage());
		}
		showProjectEnd(project);
	}

	public void showStartPanel() {
		setCenterComponent(SplitPane.SPLIT_PANE);
		SplitPane.SPLIT_PANE.showStartPanel();
	}

	public void setCenterComponent(Component component) {
		if (centerComponent != null) {
			if (centerComponent instanceof ProjectStateSaver saver) {
				saver.saveProjectState();
			}
			this.mainPanel.remove(centerComponent);
		}
		centerComponent = component;
		this.mainPanel.add(centerComponent, BorderLayout.CENTER);
		this.mainPanel.revalidate();
		this.mainPanel.repaint();
	}

	/**
	 * 加载快捷键
	 */
	public void quickKeyLoad() {
		//保存分割面板的分割比例
		addActionBeforeDispose(e -> {
			if (centerComponent instanceof ProjectStateSaver saver) {
				saver.saveProjectState();
			}
			QRSwing.setGlobalSetting(Keys.WINDOW_SPLIT_WEIGHT, SplitPane.SPLIT_PANE.getDividerLocation());
			logger.info("************************************** 您已退出程序 **************************************");
		});
	}

	@Override
	public void windowOpened(WindowEvent e) {
	}

	@Override
	public void componentFresh() {
		super.componentFresh();
		if (MainWindow.INSTANCE.backgroundImageSet() && QRSwing.windowBackgroundImagePath != null) {
			String path = QRSwing.windowBackgroundImagePath;
			MainWindow.INSTANCE.setBackgroundImage(null);
			MainWindow.INSTANCE.setBackgroundImage(path);
		}
		logger.info("全局窗口刷新完成");
	}
}
