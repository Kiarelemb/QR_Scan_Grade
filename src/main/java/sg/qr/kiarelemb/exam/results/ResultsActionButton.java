package sg.qr.kiarelemb.exam.results;

import swing.qr.kiarelemb.basic.QRRoundButton;

import java.awt.*;

abstract class ResultsActionButton extends QRRoundButton {
	private static final Dimension BUTTON_SIZE = new Dimension(110, 30);

	ResultsActionButton(String text) {
		super(text);
		setPreferredSize(BUTTON_SIZE);
	}

	ResultsPanel currentProjectEnd() {
		return ResultsPanel.INSTANCE;
	}
}