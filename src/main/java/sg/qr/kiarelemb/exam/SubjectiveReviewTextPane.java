package sg.qr.kiarelemb.exam;

import sg.qr.kiarelemb.component.CheckTextPane;

public final class SubjectiveReviewTextPane extends CheckTextPane {
	public static final SubjectiveReviewTextPane SUBJECTIVE_CHECK_TEXT_PANE = new SubjectiveReviewTextPane();

	private SubjectiveReviewTextPane() {
	}

	public void setSubjectiveText(String text, int questionCount) {
		setReviewText(text, questionCount);
	}
}
