package sg.qr.kiarelemb.exam.results;

public final class CalculateScoresButton extends ResultsActionButton {
	public static final CalculateScoresButton CALCULATE_SCORES_BUTTON = new CalculateScoresButton();

	private CalculateScoresButton() {
		super("计算成绩");
		addClickAction(event -> currentProjectEnd().calculateScores(event));
	}
}