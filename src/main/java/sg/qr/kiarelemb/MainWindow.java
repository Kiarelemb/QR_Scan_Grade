package sg.qr.kiarelemb;

import method.qr.kiarelemb.utils.QRLoggerUtils;
import sg.qr.kiarelemb.component.ProjectStateSaver;
import sg.qr.kiarelemb.component.SplitPane;
import sg.qr.kiarelemb.data.Keys;
import sg.qr.kiarelemb.grading.ProjectReviewPanel;
import sg.qr.kiarelemb.grading.ProjectManualReviewPanel;
import sg.qr.kiarelemb.grading.ProjectSubjectiveReviewPanel;
import sg.qr.kiarelemb.grading.end.ProjectEnd;
import sg.qr.kiarelemb.grading.model.Project;
import sg.qr.kiarelemb.grading.model.Template;
import sg.qr.kiarelemb.grading.pipeline.TemplateProcessor;
import sg.qr.kiarelemb.menu.data.EnglishScoreInput;
import sg.qr.kiarelemb.menu.type.SettingsItem;
import sg.qr.kiarelemb.res.Info;
import swing.qr.kiarelemb.QRSwing;
import swing.qr.kiarelemb.basic.QRButton;
import swing.qr.kiarelemb.utils.QRComponentUtils;
import swing.qr.kiarelemb.window.basic.QRFrame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.File;
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
		startProject(new Project(projectFile));
	}

	public void startProject(Project project) {
		setCursorWait();
		try {
			project.read();
			int pageCount = 1;
			try {
				Template template = TemplateProcessor.load(new File(project.templateFilePath()));
				pageCount = template.pageCount();
			} catch (Exception ex) {
				logger.warning("Cannot read template page count: " + ex.getMessage());
			}
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

	private boolean shouldContinueSubjectiveReview(Project project) {
		try {
			Template template = TemplateProcessor.load(new File(project.templateFilePath()));
			return shouldShowMachineSubjectiveReview(project, template)
				   || shouldShowManualReview(project, template);
		} catch (Exception ex) {
			logger.warning("Cannot check subjective review progress: " + ex.getMessage());
			return false;
		}
	}

	private boolean shouldShowMachineSubjectiveReview(Project project, Template template) {
		return ProjectSubjectiveReviewPanel.hasSubjectiveQuestions(project, template)
			   && !project.allSubjectiveAnswersSaved();
	}

	private boolean shouldShowManualReview(Project project, Template template) {
		return ProjectManualReviewPanel.hasManualQuestions(template)
			   && !ProjectManualReviewPanel.allManualScoresSaved(project, template);
	}

	public void showProjectReview(Project project) {
		setCenterComponent(SplitPane.SPLIT_PANE);
		SplitPane.SPLIT_PANE.showTopComponent(new ProjectReviewPanel(project));
	}

	public void showProjectEnd(Project project) {
		setCenterComponent(new ProjectEnd(project));
	}

	public void showAfterChoiceReview(Project project) {
		try {
			Template template = TemplateProcessor.load(new File(project.templateFilePath()));
			if (shouldShowMachineSubjectiveReview(project, template)) {
				setCenterComponent(new ProjectSubjectiveReviewPanel(project, template));
				return;
			}
			if (shouldShowManualReview(project, template)) {
				setCenterComponent(new ProjectManualReviewPanel(project, template));
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
