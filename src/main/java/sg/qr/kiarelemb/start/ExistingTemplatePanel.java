package sg.qr.kiarelemb.start;

import sg.qr.kiarelemb.MainWindow;
import sg.qr.kiarelemb.data.Keys;
import sg.qr.kiarelemb.exam.NewGradingProjectDialog;
import sg.qr.kiarelemb.exam.model.SheetLayout;
import sg.qr.kiarelemb.exam.model.SheetTemplate;
import sg.qr.kiarelemb.exam.processing.SheetTemplateFileStore;
import swing.qr.kiarelemb.QRSwing;
import swing.qr.kiarelemb.basic.QRLabel;
import swing.qr.kiarelemb.basic.QRPanel;
import swing.qr.kiarelemb.basic.QRRoundButton;
import swing.qr.kiarelemb.basic.QRTextField;
import swing.qr.kiarelemb.theme.QRColorsAndFonts;
import swing.qr.kiarelemb.utils.QRPicturePreviewPanel;
import swing.qr.kiarelemb.window.basic.QRDialog;
import swing.qr.kiarelemb.window.enhance.QROpinionDialog;
import swing.qr.kiarelemb.window.enhance.QRSmallTipShow;
import swing.qr.kiarelemb.window.utils.QRFileSelectDialog;
import swing.qr.kiarelemb.window.utils.QRValueInputDialog;

import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

public class ExistingTemplatePanel extends QRPanel {
	private static final File TEMPLATE_DIR = new File("sg");
	private static final File ORDER_FILE = new File(TEMPLATE_DIR, ".template-order");
	private static final Font INFO_FONT = QRColorsAndFonts.createFont(16);
	private static final Border SELECTED_CARD_BORDER = new LineBorder(QRColorsAndFonts.BLUE_LIGHT, 3);

	private final QRPanel templatePanel = new QRPanel();
	private final QRLabel summaryLabel = new QRLabel("请选择一个模板");
	private final List<TemplateItem> templateItems = new ArrayList<>();
	private final QRRoundButton moveLeftButton = new QRRoundButton("左移");
	private final QRRoundButton moveRightButton = new QRRoundButton("右移");
	private final QRRoundButton deleteButton = new QRRoundButton("删除");
	private final QRRoundButton newProjectButton = new QRRoundButton("新建批阅流程");
	private final QRRoundButton backButton = new QRRoundButton("返回");
	private final QRRoundButton renameButton = new QRRoundButton("重命名");
	private TemplateItem selectedItem;

	public ExistingTemplatePanel() {
		initView();
		loadTemplates();
	}

	private void initView() {
		setLayout(new BorderLayout());

		QRPanel topPanel = new QRPanel(false, new BorderLayout());
		topPanel.setBorder(new LineBorder(QRColorsAndFonts.FRAME_COLOR_BACK, 10));
		QRPanel buttonPanel = new QRPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		initToolbarButton(buttonPanel, backButton, this::backToStartPanel);
		initToolbarButton(buttonPanel, renameButton, this::renameSelectedTemplate);
		initToolbarButton(buttonPanel, moveLeftButton, this::moveSelectedLeft);
		initToolbarButton(buttonPanel, moveRightButton, this::moveSelectedRight);
		initToolbarButton(buttonPanel, deleteButton, this::deleteSelectedTemplate);
		topPanel.add(buttonPanel, BorderLayout.EAST);
		add(topPanel, BorderLayout.NORTH);

		templatePanel.setLayout(new FlowLayout(FlowLayout.LEFT, 18, 18));
		add(templatePanel.addScrollPane(), BorderLayout.CENTER);

		QRPanel bottomPanel = new QRPanel(false, new BorderLayout());
		bottomPanel.setBorder(new LineBorder(QRColorsAndFonts.FRAME_COLOR_BACK, 10));
		summaryLabel.setFont(INFO_FONT);
		bottomPanel.add(summaryLabel, BorderLayout.CENTER);
		newProjectButton.setPreferredSize(new Dimension(130, 30));
		newProjectButton.addClickAction(this::openNewProjectWindow);
		bottomPanel.add(newProjectButton, BorderLayout.EAST);
		add(bottomPanel, BorderLayout.SOUTH);

		updateEditButtons();
	}

	private void initToolbarButton(QRPanel buttonPanel, QRRoundButton button, ActionListener action) {
		button.setPreferredSize(new Dimension(80, 30));
		button.addClickAction(action::actionPerformed);
		buttonPanel.add(button);
	}

	private void backToStartPanel(ActionEvent event) {
		MainWindow.INSTANCE.showStartPanel();
	}

	private void loadTemplates() {
		templateItems.clear();
		selectedItem = null;
		templatePanel.removeAll();

		File[] files = TEMPLATE_DIR.listFiles(file ->
				file.isFile() && file.getName().toLowerCase().endsWith("." + SheetTemplateFileStore.TEMPLATE_EXTENSION));

		for (File file : sortTemplateFiles(files)) {
			try {
				SheetTemplate template = SheetTemplateFileStore.load(file);
				TemplateItem item = new TemplateItem(file, template);
				templateItems.add(item);
				templatePanel.add(item.card);
			} catch (IOException ex) {
				QROpinionDialog.messageErrShow(MainWindow.INSTANCE, "读取模板失败：\n" + file.getAbsolutePath() + "\n" + ex.getMessage());
			}
		}

		summaryLabel.setText(templateItems.isEmpty()
				? "未读取到可用模板。"
				: "已读取 " + templateItems.size() + " 个模板。双击模板可查看详情。");
		updateEditButtons();
		templatePanel.revalidate();
		templatePanel.repaint();
	}

	private List<File> sortTemplateFiles(File[] files) {
		List<String> savedOrder = readTemplateOrder();
		Map<String, Integer> orderIndex = new HashMap<>();
		for (int i = 0; i < savedOrder.size(); i++) {
			orderIndex.putIfAbsent(savedOrder.get(i), i);
		}
		List<File> sortedFiles = new ArrayList<>(Arrays.asList(files));
		sortedFiles.sort((first, second) -> {
			int firstOrder = orderIndex.getOrDefault(first.getName(), Integer.MAX_VALUE);
			int secondOrder = orderIndex.getOrDefault(second.getName(), Integer.MAX_VALUE);
			if (firstOrder != secondOrder) {
				return Integer.compare(firstOrder, secondOrder);
			}
			int ignoreCase = String.CASE_INSENSITIVE_ORDER.compare(first.getName(), second.getName());
			return ignoreCase != 0 ? ignoreCase : first.getName().compareTo(second.getName());
		});
		return sortedFiles;
	}

	private List<String> readTemplateOrder() {
		if (!ORDER_FILE.isFile()) {
			return List.of();
		}
		try {
			return Files.readAllLines(ORDER_FILE.toPath(), StandardCharsets.UTF_8)
					.stream()
					.map(String::trim)
					.filter(name -> !name.isEmpty())
					.toList();
		} catch (IOException ex) {
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, "读取模板顺序失败：\n" + ex.getMessage());
			return List.of();
		}
	}

	private void saveTemplateOrder() {
		try {
			Files.createDirectories(TEMPLATE_DIR.toPath());
			List<String> order = new ArrayList<>();
			for (TemplateItem item : templateItems) {
				order.add(item.file.getName());
			}
			Files.write(ORDER_FILE.toPath(), order, StandardCharsets.UTF_8);
		} catch (IOException ex) {
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, "保存模板顺序失败：\n" + ex.getMessage());
		}
	}

	private void selectTemplate(TemplateItem item) {
		selectedItem = item;
		refreshSelectedCards();
		SheetTemplate template = item.template;
		summaryLabel.setText(template.name() + "，"
		                     + TemplateAnalysisPane.buildShortSummary(template.answerSheet())
		                     + "，准考证号 " + template.answerSheet().getExamIdDigits() + " 位");
		updateEditButtons();
	}

	private void showTemplateDetail(SheetTemplate template) {
		new TemplateDetailWindow(template).setVisible(true);
	}

	private void moveSelectedLeft(ActionEvent event) {
		moveSelectedTemplate(-1);
	}

	private void moveSelectedRight(ActionEvent event) {
		moveSelectedTemplate(1);
	}

	private void renameSelectedTemplate(ActionEvent event) {
		if (selectedItem == null) {
			QROpinionDialog.messageTellShow(MainWindow.INSTANCE, "请先选择一个模板。");
			return;
		}
		TemplateItem item = selectedItem;
		QRValueInputDialog input = new QRValueInputDialog(MainWindow.INSTANCE, "新的模板名", "请输入新的模板名：");
		input.textField().setType(QRTextField.TYPE.FILE_NAME);
		input.setVisible(true);
		if (!input.isApproved()) return;
		String answer = input.getAnswer();
		String newName = normalizeTemplateName(answer.trim());
		if (newName.equals(item.template.name())) {
			QRSmallTipShow.display(MainWindow.INSTANCE, "模板名没有变化。");
			return;
		}

		setCursorWait();
		try {
			SheetTemplate renamed = renamedTemplate(item.template, newName);
			SheetTemplateFileStore.save(renamed, item.file);
			TemplateItem renamedItem = new TemplateItem(item.file, renamed);
			int index = templateItems.indexOf(item);
			if (index >= 0) {
				templateItems.set(index, renamedItem);
			}
			selectedItem = renamedItem;
			refreshTemplateCards();
			selectTemplate(renamedItem);
			QRSmallTipShow.display(MainWindow.INSTANCE, "模板已重命名为：" + newName);
		} catch (IOException ex) {
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, "重命名模板失败：\n" + item.file.getAbsolutePath() + "\n" + ex.getMessage());
		} finally {
			setCursorDefault();
			updateEditButtons();
		}
	}

	private SheetTemplate renamedTemplate(SheetTemplate template, String newName) {
		return new SheetTemplate(
				newName,
				template.pictureFile(),
				renamedAnswerSheet(template, newName),
				template.examRegionRect(),
				template.choiceRegionRect(),
				template.fillBlankRegionRect(),
				template.defaultScoreRules(),
				template.pageCount(),
				template.subjectiveRegions(),
				template.pictureFiles()
		);
	}

	private SheetLayout renamedAnswerSheet(SheetTemplate template, String newName) {
		SheetLayout answerSheet = template.answerSheet();
		return new SheetLayout(
				newName,
				answerSheet.getImageWidth(),
				answerSheet.getImageHeight(),
				answerSheet.getExamIdDigits(),
				answerSheet.getQuestions()
		);
	}

	private String normalizeTemplateName(String name) {
		if (name.toLowerCase().endsWith("." + SheetTemplateFileStore.TEMPLATE_EXTENSION)) {
			name = name.substring(0, name.length() - SheetTemplateFileStore.TEMPLATE_EXTENSION.length() - 1).trim();
		}
		return name;
	}

	private void moveSelectedTemplate(int offset) {
		if (selectedItem == null) {
			QROpinionDialog.messageTellShow(MainWindow.INSTANCE, "请先选择一个模板。");
			return;
		}
		int index = templateItems.indexOf(selectedItem);
		int targetIndex = index + offset;
		if (index < 0 || targetIndex < 0 || targetIndex >= templateItems.size()) {
			updateEditButtons();
			return;
		}
		TemplateItem current = templateItems.get(index);
		templateItems.set(index, templateItems.get(targetIndex));
		templateItems.set(targetIndex, current);
		saveTemplateOrder();
		refreshTemplateCards();
		updateEditButtons();
	}

	private void deleteSelectedTemplate(ActionEvent event) {
		if (selectedItem == null) {
			QROpinionDialog.messageTellShow(MainWindow.INSTANCE, "请先选择一个模板。");
			return;
		}
		TemplateItem item = selectedItem;
		int choice = QROpinionDialog.messageInfoShow(MainWindow.INSTANCE, "确认删除模板：\n"
		                                                                  + item.template.name() + "\n" + item.file.getAbsolutePath());
		if (choice != QROpinionDialog.OK) {
			return;
		}
		setCursorWait();
		try {
			Files.delete(item.file.toPath());
			templateItems.remove(item);
			selectedItem = null;
			saveTemplateOrder();
			refreshTemplateCards();
			summaryLabel.setText(templateItems.isEmpty() ? "未读取到可用模板。" : "已删除模板 " + item.template.name() + "。");
		} catch (IOException ex) {
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, "删除模板失败：\n" + item.file.getAbsolutePath() + "\n" + ex.getMessage());
		} finally {
			setCursorDefault();
			updateEditButtons();
		}
	}

	private void refreshTemplateCards() {
		templatePanel.removeAll();
		for (TemplateItem item : templateItems) {
			templatePanel.add(item.card);
		}
		refreshSelectedCards();
		templatePanel.revalidate();
		templatePanel.repaint();
	}

	private void refreshSelectedCards() {
		for (TemplateItem item : templateItems) {
			item.card.setSelected(item == selectedItem);
		}
	}

	private void updateEditButtons() {
		int selectedIndex = selectedItem == null ? -1 : templateItems.indexOf(selectedItem);
		renameButton.setEnabled(selectedIndex >= 0);
		moveLeftButton.setEnabled(selectedIndex > 0);
		moveRightButton.setEnabled(selectedIndex >= 0 && selectedIndex < templateItems.size() - 1);
		deleteButton.setEnabled(selectedIndex >= 0);
		newProjectButton.setEnabled(selectedIndex >= 0);
	}

	private void openNewProjectWindow(ActionEvent event) {
		if (selectedItem == null) {
			QROpinionDialog.messageTellShow(MainWindow.INSTANCE, "请先选择一个模板。");
			return;
		}
		String dir = Keys.strValue(Keys.SELECTED_FILE_DIRECTORY);
		File dirFile = dir == null || dir.isBlank() ? new File(".") : new File(dir);
		QRFileSelectDialog fileSelect;
		setCursorWait();
		try {
			fileSelect = new QRFileSelectDialog(MainWindow.INSTANCE,
					QRFileSelectDialog.SelectMode.DIRECTORY_ONLY,
					dirFile,
					"请选择考生答卷扫描所在文件夹");
		} finally {
			setCursorDefault();
		}
		fileSelect.setVisible(true);
		if (!fileSelect.selectedSucceeded()) {
			return;
		}
		File answerDirectory = fileSelect.selectedFile();
		QRSwing.setGlobalSetting(Keys.SELECTED_FILE_DIRECTORY, answerDirectory.getAbsolutePath());
		new NewGradingProjectDialog(selectedItem.template, selectedItem.file, answerDirectory).setVisible(true);
	}

	private final class TemplateItem {
		private final File file;
		private final SheetTemplate template;
		private final TemplateCard card;

		private TemplateItem(File file, SheetTemplate template) {
			this.file = file;
			this.template = template;
			this.card = new TemplateCard(this);
		}
	}

	private final class TemplateCard extends QRPicturePreviewPanel {
		private final TemplateItem item;
		private final Border defaultBorder;

		private TemplateCard(TemplateItem item) {
			super(item.template.pictureFile(), item.template.name());
			this.item = item;
			this.defaultBorder = getBorder();
			addMouseListener();
		}

		@Override
		protected void mouseClick(MouseEvent e) {
			selectTemplate(item);
			if (e.getClickCount() >= 2) {
				showTemplateDetail(item.template);
			}
		}

		private void setSelected(boolean selected) {
			setBorder(selected ? SELECTED_CARD_BORDER : defaultBorder);
			repaint();
		}
	}

	private static final class TemplateDetailWindow extends QRDialog {
		private TemplateDetailWindow(SheetTemplate template) {
			super(MainWindow.INSTANCE);
			setTitle(template.name());
			setTitlePlace(CENTER);
			setSize(1180, 760);
			setLocationRelativeTo(MainWindow.INSTANCE);
			setParentWindowNotFollowMove();
			mainPanel.setLayout(new BorderLayout());
			mainPanel.add(new TemplateAnalysisPane(template), BorderLayout.CENTER);
		}
	}
}
