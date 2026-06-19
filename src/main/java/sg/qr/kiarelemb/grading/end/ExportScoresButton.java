package sg.qr.kiarelemb.grading.end;

public final class ExportScoresButton extends ProjectEndButton {
	public static final ExportScoresButton EXPORT_SCORES_BUTTON = new ExportScoresButton();

	private ExportScoresButton() {
		super("导出成绩");
		addClickAction(event -> currentProjectEnd().exportScores(event));
	}
}
