package sg.qr.kiarelemb.exam.results;

public final class ExportResultsButton extends ResultsActionButton {
	public static final ExportResultsButton EXPORT_SCORES_BUTTON = new ExportResultsButton();

	private ExportResultsButton() {
		super("导出成绩");
		addClickAction(event -> currentProjectEnd().exportScores(event));
	}
}