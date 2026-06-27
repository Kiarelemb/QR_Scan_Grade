package sg.qr.kiarelemb.component;

import swing.qr.kiarelemb.basic.QRLabel;
import swing.qr.kiarelemb.basic.QRTextPane;
import swing.qr.kiarelemb.data.QRMousePointIndexData;
import swing.qr.kiarelemb.listener.QRDocumentListener;
import swing.qr.kiarelemb.listener.QRFocusListener;
import swing.qr.kiarelemb.listener.QRMouseMotionListener;
import swing.qr.kiarelemb.theme.QRColorsAndFonts;
import swing.qr.kiarelemb.window.basic.QREmptyDialog;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

public class CheckTextPane extends QRTextPane {
	private final QRLabel questionTip = new QRLabel();
	private QREmptyDialog questionTipWindow;
	private boolean programmaticChange;
	private int answerCount;
	private TextChangeListener textChangeListener;

	protected CheckTextPane() {
		setFont(QRColorsAndFonts.createFont(28));
		setLineWrap(true);
		addDocumentListenerActionAll(this::textChanged);
		addCaretListenerAction(event -> {
			if (hasFocus()) {
				showCaretQuestionTip();
			}
		});
		addMouseMotionAction(QRMouseMotionListener.TYPE.MOVE, event -> showMouseQuestionTip(event.getPoint()));
		addFocusAction(QRFocusListener.TYPE.LOST, event -> hideQuestionTip());
	}

	public void setTextChangeListener(TextChangeListener textChangeListener) {
		this.textChangeListener = textChangeListener;
	}

	public void setReviewText(String text, int answerCount) {
		programmaticChange = true;
		try {
			this.answerCount = Math.max(0, answerCount);
			setText(text == null ? "" : text);
			indexesUpdate();
			showCaretQuestionTip();
		} finally {
			programmaticChange = false;
		}
	}

	public void clearReviewText() {
		programmaticChange = true;
		try {
			this.answerCount = 0;
			clear();
			hideQuestionTip();
		} finally {
			programmaticChange = false;
		}
	}

	protected int answerStartIndex() {
		return 0;
	}

	private void textChanged(DocumentEvent event) {
		if (programmaticChange || textChangeListener == null) {
			return;
		}
		textChangeListener.textChanged(getText());
		indexesUpdate();
		showCaretQuestionTip();
	}

	private void showCaretQuestionTip() {
		int answerNumber = answerNumberAtIndex(getCaretPosition(), true);
		if (answerNumber <= 0) {
			hideQuestionTip();
			return;
		}
		Rectangle2D rectangle = rectangleAt(getCaretPosition());
		if (rectangle != null) {
			showQuestionTip(answerNumber, new Point((int) rectangle.getX(), (int) rectangle.getY()));
		}
	}

	private Rectangle2D rectangleAt(int position) {
		try {
			indexesUpdate();
			return positionRectangle(Math.max(0, Math.min(position, textLength())));
		} catch (Exception ignored) {
			try {
				return modelToView2D(position);
			} catch (Exception ignoredAgain) {
				return null;
			}
		}
	}

	private void showMouseQuestionTip(Point point) {
		if (!hasFocus()) {
			return;
		}
		indexesUpdate();
		QRMousePointIndexData data = getMousePointIndexData(point);
		int index = data == null || data.caretPosition() == null ? -1 : data.caretPosition();
		int answerNumber = answerNumberAtIndex(index, false);
		if (answerNumber <= 0) {
			hideQuestionTip();
			return;
		}
		showQuestionTip(answerNumber, point);
	}

	private int answerNumberAtIndex(int index, boolean includeAdjacent) {
		if (index < answerStartIndex()) {
			return -1;
		}
		List<TokenSpan> spans = tokenSpans();
		for (int i = 0; i < spans.size(); i++) {
			TokenSpan span = spans.get(i);
			if (index >= span.start() && index < span.end()) {
				return i + 1;
			}
			if (includeAdjacent && index == span.end()) {
				return i + 1;
			}
		}
		return -1;
	}

	private List<TokenSpan> tokenSpans() {
		String text = getText();
		if (text == null || text.isEmpty()) {
			return List.of();
		}
		List<TokenSpan> spans = new ArrayList<>();
		int start = -1;
		for (int i = answerStartIndex(); i < text.length(); i++) {
			if (Character.isWhitespace(text.charAt(i))) {
				if (start >= 0) {
					spans.add(new TokenSpan(start, i));
					start = -1;
				}
			} else if (start < 0) {
				start = i;
			}
		}
		if (start >= 0) {
			spans.add(new TokenSpan(start, text.length()));
		}
		return answerCount <= 0 || spans.size() <= answerCount ? spans : spans.subList(0, answerCount);
	}

	private void showQuestionTip(int answerNumber, Point componentPoint) {
		if (answerNumber <= 0 || !hasFocus()) {
			hideQuestionTip();
			return;
		}
		ensureQuestionTipWindow();
		questionTip.setText("第" + answerNumber + "题");
		questionTipWindow.pack();
		Point screenPoint = new Point(componentPoint);
		SwingUtilities.convertPointToScreen(screenPoint, this);
		questionTipWindow.setLocation(screenPoint.x, screenPoint.y - questionTipWindow.getHeight() - 4);
		questionTipWindow.setVisible(true);
	}

	private void ensureQuestionTipWindow() {
		Window owner = SwingUtilities.getWindowAncestor(this);
		if (questionTipWindow != null && questionTipWindow.getOwner() == owner) {
			return;
		}
		if (questionTipWindow != null) {
			questionTipWindow.dispose();
		}
		questionTipWindow = new QREmptyDialog(owner, false);
		questionTipWindow.setFocusableWindowState(false);
		questionTipWindow.getContentPane().add(questionTip);
	}

	private void hideQuestionTip() {
		if (questionTipWindow != null) {
			questionTipWindow.setVisible(false);
		}
	}

	public interface TextChangeListener {
		void textChanged(String text);
	}

	private record TokenSpan(int start, int end) {
	}
}