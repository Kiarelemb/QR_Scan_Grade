package sg.qr.kiarelemb.grading.end;

import swing.qr.kiarelemb.basic.QRRoundButton;

import java.awt.*;

abstract class ProjectEndButton extends QRRoundButton {
	private static final Dimension BUTTON_SIZE = new Dimension(110, 30);

	ProjectEndButton(String text) {
		super(text);
		setPreferredSize(BUTTON_SIZE);
	}

	ProjectEnd currentProjectEnd() {
		return ProjectEnd.INSTANCE;
	}
}
