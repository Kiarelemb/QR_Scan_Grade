package sg.qr.kiarelemb.component;

import org.bytedeco.opencv.opencv_core.Rect;
import sg.qr.kiarelemb.MainWindow;
import sg.qr.kiarelemb.data.Utils;
import swing.qr.kiarelemb.basic.QRLabel;
import swing.qr.kiarelemb.basic.QRPanel;
import swing.qr.kiarelemb.basic.QRRoundButton;
import swing.qr.kiarelemb.basic.QRTextField;
import swing.qr.kiarelemb.theme.QRColorsAndFonts;
import swing.qr.kiarelemb.utils.PicturePanel;
import swing.qr.kiarelemb.window.basic.QRDialog;
import swing.qr.kiarelemb.window.enhance.QROpinionDialog;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public final class ManualScoreReviewDialog extends QRDialog {
	private final List<Entry> entries;
	private final List<ScoreItem> scoreItems;
	private final ScoreStore scoreStore;
	private final Rect region;
	private final Map<String, QRTextField> scoreFields = new LinkedHashMap<>();
	private final PicturePanel picturePanel = new PicturePanel();
	private final QRLabel progressLabel = new QRLabel();
	private int index;

	public ManualScoreReviewDialog(Window owner, String title, List<Entry> entries, List<ScoreItem> scoreItems,
								   Rect region, ScoreStore scoreStore) {
		super(owner);
		this.entries = entries == null ? List.of() : List.copyOf(entries);
		this.scoreItems = scoreItems == null ? List.of() : List.copyOf(scoreItems);
		this.region = region;
		this.scoreStore = scoreStore;
		setTitle(title == null || title.isBlank() ? "录入人工分" : title);
		setSize(900, 680);
		setLocationRelativeTo(owner == null ? MainWindow.INSTANCE : owner);
		setParentWindowNotFollowMove();
		mainPanel.setLayout(new BorderLayout(8, 8));

		picturePanel.setZoomRange(0.25, 5.0);
		mainPanel.add(progressLabel, BorderLayout.NORTH);
		mainPanel.add(picturePanel, BorderLayout.CENTER);
		mainPanel.add(buildScorePanel(), BorderLayout.SOUTH);
		loadCurrent();
	}

	private Component buildScorePanel() {
		QRPanel wrapper = new QRPanel(false, new BorderLayout(8, 8));
		wrapper.setBorder(new javax.swing.border.LineBorder(QRColorsAndFonts.FRAME_COLOR_BACK, 10));
		QRPanel fields = new QRPanel(false, new FlowLayout(FlowLayout.LEFT, 10, 0));
		for (ScoreItem item : scoreItems) {
			QRLabel label = new QRLabel(item.name() + "/" + Utils.formatScore(item.maxScore()));
			label.setPreferredSize(new Dimension(95, 30));
			QRTextField field = new QRTextField();
			field.setPreferredSize(new Dimension(90, 30));
			field.addActionListener(event -> next());
			scoreFields.put(item.name(), field);
			fields.add(label);
			fields.add(field);
		}
		wrapper.add(fields, BorderLayout.CENTER);

		QRPanel buttons = new QRPanel(false, new FlowLayout(FlowLayout.RIGHT, 8, 0));
		QRRoundButton previousButton = new QRRoundButton("上一张");
		previousButton.setPreferredSize(80, 30);
		previousButton.addClickAction(event -> previous());
		QRRoundButton nextButton = new QRRoundButton("下一张");
		nextButton.setPreferredSize(80, 30);
		nextButton.addClickAction(event -> next());
		QRRoundButton closeButton = new QRRoundButton("保存退出");
		closeButton.setPreferredSize(100, 30);
		closeButton.addClickAction(event -> {
			if (saveCurrent()) {
				dispose();
			}
		});
		buttons.add(previousButton);
		buttons.add(nextButton);
		buttons.add(closeButton);
		wrapper.add(buttons, BorderLayout.EAST);
		return wrapper;
	}

	private void loadCurrent() {
		if (entries.isEmpty()) {
			progressLabel.setText("没有可录入人工分的答卷。");
			return;
		}
		index = Math.max(0, Math.min(index, entries.size() - 1));
		Entry entry = entries.get(index);
		progressLabel.setText("当前：" + entry.answerFile().getName() + "，准考证号：" + entry.examineeId()
							  + (entry.name().isBlank() ? "" : "，姓名：" + entry.name()) + "，进度：" + (index + 1) + " / " + entries.size());
		try {
			BufferedImage image = ImageIO.read(entry.answerFile());
			BufferedImage crop = crop(image);
			picturePanel.setImage(crop, new Dimension(crop.getWidth(), crop.getHeight()));
			picturePanel.resetView();
		} catch (IOException ex) {
			QROpinionDialog.messageErrShow(this, "读取答卷图片失败：\n" + ex.getMessage());
		}
		Map<String, String> savedScores = scoreStore.load(entry);
		for (Map.Entry<String, QRTextField> fieldEntry : scoreFields.entrySet()) {
			fieldEntry.getValue().setText(savedScores.getOrDefault(fieldEntry.getKey(), ""));
		}
		if (!scoreFields.isEmpty()) {
			scoreFields.values().iterator().next().requestFocusInWindow();
		}
	}

	private BufferedImage crop(BufferedImage image) {
		if (region == null) {
			return image;
		}
		int x = Math.max(0, Math.min(region.x(), image.getWidth() - 1));
		int y = Math.max(0, Math.min(region.y(), image.getHeight() - 1));
		int right = Math.max(x + 1, Math.min(region.x() + region.width(), image.getWidth()));
		int bottom = Math.max(y + 1, Math.min(region.y() + region.height(), image.getHeight()));
		return image.getSubimage(x, y, right - x, bottom - y);
	}

	private void previous() {
		if (saveCurrent() && index > 0) {
			index--;
			loadCurrent();
		}
	}

	private void next() {
		if (!saveCurrent()) {
			return;
		}
		if (index < entries.size() - 1) {
			index++;
			loadCurrent();
		} else {
			QROpinionDialog.messageTellShow(this, "人工分已保存。");
		}
	}

	private boolean saveCurrent() {
		if (entries.isEmpty()) {
			return true;
		}
		Entry entry = entries.get(index);
		Map<String, String> scores = new LinkedHashMap<>();
		for (ScoreItem item : scoreItems) {
			QRTextField field = scoreFields.get(item.name());
			String text = field == null || field.getText() == null ? "" : field.getText().trim();
			if (!text.isEmpty()) {
				BigDecimal score;
				try {
					score = new BigDecimal(text).setScale(2, RoundingMode.HALF_UP);
				} catch (NumberFormatException ex) {
					QROpinionDialog.messageErrShow(this, item.name() + " 分数不是有效数字。");
					return false;
				}
				if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(item.maxScore()) > 0) {
					QROpinionDialog.messageErrShow(this, item.name() + " 分数应在 0-" + Utils.formatScore(item.maxScore()) + " 之间。");
					return false;
				}
				text = score.stripTrailingZeros().toPlainString();
			}
			scores.put(item.name(), text);
		}
		scoreStore.save(entry, scores);
		return true;
	}

	public record Entry(File answerFile, String examineeId, String name) {
		public Entry {
			name = name == null ? "" : name;
		}
	}

	public record ScoreItem(String name, BigDecimal maxScore) {
	}

	public interface ScoreStore {
		Map<String, String> load(Entry entry);

		void save(Entry entry, Map<String, String> scores);
	}
}