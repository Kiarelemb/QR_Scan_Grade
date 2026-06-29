package sg.qr.kiarelemb.exam.template.detect;

/**
 * @author Kiarelemb
 * @projectName QR_Scan_Grade
 * @className DetectedBox
 * @description TODO
 * @create 2026/6/1 06:56
 */
public record DetectedBox(int x, int y, int w, int h, int cx, int cy) {
	/**
	 *
	 */
	public DetectedBox {
	}

	public DetectedBox(int x, int y, int w, int h) {
		this(x, y, w, h, x + w / 2, y + h / 2);
	}

	@Override
	public String toString() {
		return String.format("Box[x=%d, y=%d, w=%d, h=%d, cx=%d, cy=%d]", x, y, w, h, cx, cy);
	}


}