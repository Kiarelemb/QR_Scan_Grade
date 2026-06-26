package sg.qr.kiarelemb.exam.results;

public final class BackToReviewButton extends ResultsActionButton {
	public static final BackToReviewButton BACK_TO_REVIEW_BUTTON = new BackToReviewButton();

	private BackToReviewButton() {
		super("返回校对");
		addClickAction(event -> currentProjectEnd().backToReview());
	}
}