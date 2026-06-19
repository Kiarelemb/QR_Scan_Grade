package sg.qr.kiarelemb.grading.end;

public final class CalculateScoresButton extends ProjectEndButton {
	public static final CalculateScoresButton CALCULATE_SCORES_BUTTON = new CalculateScoresButton();

	private CalculateScoresButton() {
		super("计算成绩");
		addClickAction(event -> currentProjectEnd().calculateScores(event));
	}
}
