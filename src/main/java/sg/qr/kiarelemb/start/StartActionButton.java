package sg.qr.kiarelemb.start;

import swing.qr.kiarelemb.basic.QRRoundButton;
import swing.qr.kiarelemb.theme.QRColorsAndFonts;

/**
 * @author Kiarelemb
 * @projectName QR_Scan_Grade
 * @className StartBtn
 * @description TODO
 * @create 2026/6/4 12:30
 */
public class StartActionButton extends QRRoundButton {
	public StartActionButton(String name){
		super(name);
		setPreferredSize(200,100);
	}

	@Override
	public void componentFresh() {
		super.componentFresh();
		setFont(QRColorsAndFonts.createFont(24));
	}
}