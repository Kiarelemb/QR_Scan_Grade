package sg.qr.kiarelemb.menu.data;

import method.qr.kiarelemb.utils.QRFileUtils;
import sg.qr.kiarelemb.MainWindow;
import sg.qr.kiarelemb.menu.MenuItem;
import swing.qr.kiarelemb.basic.QRPanel;
import swing.qr.kiarelemb.basic.QRRoundButton;
import swing.qr.kiarelemb.basic.QRTextPane;
import swing.qr.kiarelemb.theme.QRColorsAndFonts;
import swing.qr.kiarelemb.window.basic.QRDialog;
import swing.qr.kiarelemb.window.enhance.QROpinionDialog;

import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

public class EnglishScoreInput extends MenuItem {

	public static final EnglishScoreInput ENGLISH_SCORE_INPUT = new EnglishScoreInput();
	public static final File ENGLISH_SCORE_FILE = new File("data" + File.separator + "English_Score.txt");

	private EnglishScoreInput() {
		super("导入入班英语成绩", null);
	}

	@Override
	protected void actionEvent(ActionEvent o) {
		new EnglishScoreDialog().setVisible(true);
	}

	public static Map<String, BigDecimal> readScores() {
		Map<String, BigDecimal> scores = new LinkedHashMap<>();
		if (!QRFileUtils.fileExists(ENGLISH_SCORE_FILE)) {
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, "入班英语成绩文件不存在");
		}
		QRFileUtils.fileReaderWithUtf8(ENGLISH_SCORE_FILE.getAbsolutePath(), "\t", (lineText, parts) -> {
			if (parts.length != 2 || parts[0].trim().isEmpty()) {
				return;
			}
			try {
				scores.put(parts[0].trim(), new BigDecimal(parts[1].trim()).setScale(1, RoundingMode.HALF_UP));
			} catch (NumberFormatException ignored) {
			}
		});
		return scores;
	}

	private static final class EnglishScoreDialog extends QRDialog {
		private final QRTextPane textPane = new QRTextPane();

		private EnglishScoreDialog() {
			super(MainWindow.INSTANCE);
			setTitle("导入入班英语成绩");
			setTitlePlace(CENTER);
			setSize(560, 520);
			setLocationRelativeTo(MainWindow.INSTANCE);
			setParentWindowNotFollowMove();

			mainPanel.setLayout(new BorderLayout(5, 5));
			QRPanel center = new QRPanel(false, new BorderLayout());
			center.setBorder(new LineBorder(QRColorsAndFonts.LINE_COLOR, 3));
			textPane.addUndoManager();
			String text = readFileText();

			if (text.isBlank()) {
				text = "张三\t90.0" + System.lineSeparator()
					   + "李四\t80.0" + System.lineSeparator();
			}
			textPane.setText(text);
			center.add(textPane.addScrollPane(), BorderLayout.CENTER);
			mainPanel.add(center, BorderLayout.CENTER);

			QRPanel bottom = new QRPanel(false, new FlowLayout(FlowLayout.RIGHT, 8, 0));
			bottom.setBorder(new LineBorder(QRColorsAndFonts.FRAME_COLOR_BACK, 10));
			QRRoundButton cancelButton = new QRRoundButton("取消");
			cancelButton.setPreferredSize(new Dimension(80, 30));
			cancelButton.addClickAction(event -> dispose());
			QRRoundButton saveButton = new QRRoundButton("保存");
			saveButton.setPreferredSize(new Dimension(80, 30));
			saveButton.addClickAction(this::save);
			bottom.add(cancelButton);
			bottom.add(saveButton);
			mainPanel.add(bottom, BorderLayout.SOUTH);
		}

		private String readFileText() {
			if (!ENGLISH_SCORE_FILE.canRead()) {
				return "";
			}
			return QRFileUtils.fileReaderWithUtf8All(ENGLISH_SCORE_FILE);
		}

		private void save(ActionEvent event) {
			String normalized = normalize(textPane.getText());
			if (normalized == null) {
				return;
			}
			QRFileUtils.dirCreate(ENGLISH_SCORE_FILE.getParent());
			QRFileUtils.fileWriterWithUTF8(ENGLISH_SCORE_FILE.getAbsolutePath(), normalized);
			QROpinionDialog.messageTellShow(this, "入班英语成绩已保存。");
			dispose();
		}

		private String normalize(String text) {
			StringBuilder builder = new StringBuilder();
			String[] lines = (text == null ? "" : text).split("\\R", -1);
			for (int i = 0; i < lines.length; i++) {
				String line = lines[i].trim();
				if (line.isEmpty()) {
					continue;
				}
				String[] parts = line.split("\\t", -1);
				if (parts.length != 2 || parts[0].trim().isEmpty()) {
					QROpinionDialog.messageErrShow(this, "第 " + (i + 1) + " 行格式错误，应为：姓名\\t分数");
					return null;
				}
				BigDecimal score;
				try {
					score = new BigDecimal(parts[1].trim()).setScale(1, RoundingMode.HALF_UP);
				} catch (NumberFormatException ex) {
					QROpinionDialog.messageErrShow(this, "第 " + (i + 1) + " 行分数不是有效数字。");
					return null;
				}
				builder.append(parts[0].trim()).append('\t').append(score.toPlainString()).append(System.lineSeparator());
			}
			return builder.toString();
		}
	}
}