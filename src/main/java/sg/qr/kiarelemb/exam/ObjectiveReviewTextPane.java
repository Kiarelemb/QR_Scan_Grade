package sg.qr.kiarelemb.exam;

import sg.qr.kiarelemb.component.CheckTextPane;

import java.util.ArrayList;
import java.util.List;

public final class ObjectiveReviewTextPane extends CheckTextPane {
	public static final ObjectiveReviewTextPane CHOICE_CHECK_TEXT_PANE = new ObjectiveReviewTextPane();

	private ReviewDataChangeListener reviewDataChangeListener;

	private ObjectiveReviewTextPane() {
		setTextChangeListener(this::reviewTextChanged);
	}

	public void setReviewAnswerChangeListener(ReviewDataChangeListener reviewDataChangeListener) {
		this.reviewDataChangeListener = reviewDataChangeListener;
	}

	public void setReviewData(String examId, List<String> answers) {
		String answerText = answers == null ? "" : String.join(" ", answers);
		setReviewText((examId == null ? "" : examId) + "\t" + answerText, answers == null ? 0 : answers.size());
	}

	public void setExamId(String examId) {
		setReviewData(examId, currentAnswers(getText()));
	}

	public void clearReviewAnswers() {
		clearReviewText();
	}

	@Override
	protected int answerStartIndex() {
		String text = getText();
		if (text == null) {
			return 0;
		}
		int tabIndex = text.indexOf('\t');
		if (tabIndex >= 0) {
			return tabIndex + 1;
		}
		int firstTokenEnd = firstTokenEnd(text);
		return firstTokenEnd < 0 ? text.length() : firstTokenEnd;
	}

	private void reviewTextChanged(String text) {
		if (reviewDataChangeListener == null) {
			return;
		}
		reviewDataChangeListener.reviewDataChanged(currentExamId(text), currentAnswers(text));
	}

	private String currentExamId(String text) {
		if (text == null) {
			return "";
		}
		int tabIndex = text.indexOf('\t');
		if (tabIndex >= 0) {
			return text.substring(0, tabIndex).trim();
		}
		String value = text.trim();
		if (value.isEmpty()) {
			return "";
		}
		return value.split("[ \\t\\r\\n]+", 2)[0].trim();
	}

	private List<String> currentAnswers(String text) {
		String value = text == null ? "" : text.substring(answerStartIndex()).trim();
		if (value.isEmpty()) {
			return List.of();
		}
		String[] parts = value.split("[ \\t\\r\\n]+");
		List<String> answers = new ArrayList<>();
		for (String part : parts) {
			answers.add(part.trim());
		}
		return answers;
	}

	private int firstTokenEnd(String text) {
		boolean inToken = false;
		for (int i = 0; i < text.length(); i++) {
			if (Character.isWhitespace(text.charAt(i))) {
				if (inToken) {
					return i;
				}
			} else {
				inToken = true;
			}
		}
		return inToken ? text.length() : -1;
	}

	public interface ReviewDataChangeListener {
		void reviewDataChanged(String examId, List<String> answers);
	}
}
