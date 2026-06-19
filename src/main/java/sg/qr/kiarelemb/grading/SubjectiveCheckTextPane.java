package sg.qr.kiarelemb.grading;

import sg.qr.kiarelemb.component.CheckTextPane;

public final class SubjectiveCheckTextPane extends CheckTextPane {
	public static final SubjectiveCheckTextPane SUBJECTIVE_CHECK_TEXT_PANE = new SubjectiveCheckTextPane();

	private SubjectiveCheckTextPane() {
	}

	public void setSubjectiveText(String text, int questionCount) {
		setReviewText(text, questionCount);
	}
}
