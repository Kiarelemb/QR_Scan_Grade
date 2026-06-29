package sg.qr.kiarelemb.start;

import sg.qr.kiarelemb.MainWindow;
import sg.qr.kiarelemb.exam.ManualScoringPanel;
import sg.qr.kiarelemb.exam.SubjectiveOcrReviewPanel;
import sg.qr.kiarelemb.exam.model.GradingProject;
import sg.qr.kiarelemb.exam.model.SheetTemplate;
import sg.qr.kiarelemb.exam.processing.SheetTemplateFileStore;
import swing.qr.kiarelemb.basic.QRList;
import swing.qr.kiarelemb.basic.QRPanel;
import swing.qr.kiarelemb.basic.QRRoundButton;
import swing.qr.kiarelemb.listener.QRMouseListener;
import swing.qr.kiarelemb.theme.QRColorsAndFonts;
import swing.qr.kiarelemb.window.basic.QRDialog;
import swing.qr.kiarelemb.window.enhance.QROpinionDialog;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * @author Kiarelemb
 * @projectName QR_Scan_Grade
 * @className ContinueProjectWindow
 * @description TODO
 * @create 2026/6/6 18:11
 */
public class ContinueProjectWindow extends QRDialog {
	private static final File PROJECT_DIR = new File("sgp");
	private static final String PROJECT_EXTENSION = "sgp";

	private final QRList<ProjectItem> projectList = new QRList<>();

	public ContinueProjectWindow() {
		super(MainWindow.INSTANCE);
		initView();
		loadProjects();
	}

	@Override
	public void windowOpened(WindowEvent e) {
		if (projectList.getListSize() > 0) {
			projectList.setSelectedIndex(0);
		}
	}

	private void initView() {
		setTitle("请选择继续批阅的项目");
		setTitlePlace(CENTER);
		setSize(620, 420);
		setLocationRelativeTo(MainWindow.INSTANCE);
		setParentWindowNotFollowMove();

		mainPanel.setLayout(new BorderLayout());

		projectList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		projectList.setFont(QRColorsAndFonts.createFont(17));
		projectList.setFixedCellHeight(42);
		projectList.addMouseListener(QRMouseListener.TYPE.CLICK, e -> {
			if (e.getClickCount() >= 2) {
				continueSelectedProject();
			}
		});
		mainPanel.add(projectList.addScrollPane(), BorderLayout.CENTER);

		QRPanel bottomPanel = new QRPanel(false, new FlowLayout(FlowLayout.RIGHT, 8, 0));
		bottomPanel.setBorder(new LineBorder(QRColorsAndFonts.FRAME_COLOR_BACK, 10));
		QRRoundButton cancelButton = new QRRoundButton("取消");
		cancelButton.setPreferredSize(new Dimension(80, 30));
		cancelButton.addClickAction(event -> dispose());
		QRRoundButton continueButton = new QRRoundButton("继续批阅");
		continueButton.setPreferredSize(new Dimension(100, 30));
		continueButton.addClickAction(this::continueSelectedProject);
		bottomPanel.add(cancelButton);
		bottomPanel.add(continueButton);
		mainPanel.add(bottomPanel, BorderLayout.SOUTH);
	}

	private void loadProjects() {
		projectList.clear();
		File[] files = PROJECT_DIR.listFiles(file ->
				file.isFile() && file.getName().toLowerCase().endsWith("." + PROJECT_EXTENSION));
		if (files == null || files.length == 0) {
			QROpinionDialog.messageTellShow(this, "sgp 目录下暂无批阅项目。");
			dispose();
			return;
		}

		Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
		for (File file : files) {
			try {
				GradingProject project = new GradingProject(file);
				project.read();
				projectList.addItem(new ProjectItem(project));
			} catch (Exception ex) {
				QROpinionDialog.messageErrShow(this, "读取批阅项目失败：\n" + file.getAbsolutePath() + "\n" + ex.getMessage());
			}
		}
	}

	private void continueSelectedProject(ActionEvent event) {
		continueSelectedProject();
	}

	private void continueSelectedProject() {
		ProjectItem item = projectList.getSelectedValue();
		if (item == null) {
			QROpinionDialog.messageTellShow(this, "请先选择一个批阅项目。");
			return;
		}
		dispose();
		MainWindow.INSTANCE.startProject(item.project());
	}

	private record ProjectItem(GradingProject project) {
		@Override
		public String toString() {
			return projectName() + "    " + progressText();
		}

		private String summary() {
			return projectName() + "：" + progressText();
		}

		private String projectName() {
			String fileName = new File(project.projectFilePath()).getName();
			String suffix = "." + PROJECT_EXTENSION;
			if (fileName.toLowerCase().endsWith(suffix)) {
				return fileName.substring(0, fileName.length() - suffix.length());
			}
			return fileName;
		}

		private String progressText() {
			int total = project.answerFiles() == null ? 0 : project.answerFiles().size();
			List<String> parts = new ArrayList<>();
			parts.add("选择题：" + progress(project.index(), total));
			try {
				SheetTemplate template = SheetTemplateFileStore.load(new File(project.templateFilePath()));
				if (SubjectiveOcrReviewPanel.hasSubjectiveQuestions(project, template)) {
					parts.add("填空校对：" + progress(project.savedSubjectiveAnswerCount(), total));
				}
				if (ManualScoringPanel.hasManualQuestions(project, template)) {
					int manualTotal = ManualScoringPanel.manualReviewItemCount(project, template);
					int savedManualCount = project.savedManualScoreCount();
					if (manualTotal <= 0 && savedManualCount > 0) {
						manualTotal = savedManualCount;
					}
					parts.add("人工判分：" + progress(savedManualCount, manualTotal));
				}
			} catch (Exception ignored) {
			}
			return String.join("；", parts);
		}

		private String progress(int value, int total) {
			int safeTotal = Math.max(0, total);
			int safeValue = Math.max(0, safeTotal == 0 ? value : Math.min(value, safeTotal));
			return safeValue + " / " + safeTotal;
		}
	}
}
