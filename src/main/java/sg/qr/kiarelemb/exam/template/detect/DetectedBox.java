package sg.qr.kiarelemb.exam.template.detect;

import java.util.Objects;

/**
 * @author Kiarelemb
 * @projectName QR_Scan_Grade
 * @className DetectedBox
 * @description TODO
 * @create 2026/6/1 06:56
 */
public final class DetectedBox {
	public final int x;
	public final int y;
	public final int w;
	public final int h;
	public final int cx;
	public final int cy;

	/**
	 *
	 */
	public DetectedBox(int x, int y, int w, int h, int cx, int cy) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		this.cx = cx;
		this.cy = cy;
	}

	public DetectedBox(int x, int y, int w, int h) {
		this(x, y, w, h, x + w / 2, y + h / 2);
	}

	@Override
	public String toString() {
		return String.format("Box[x=%d, y=%d, w=%d, h=%d, cx=%d, cy=%d]", x, y, w, h, cx, cy);
	}

	public int x() {
		return x;
	}

	public int y() {
		return y;
	}

	public int w() {
		return w;
	}

	public int h() {
		return h;
	}

	public int cx() {
		return cx;
	}

	public int cy() {
		return cy;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (obj == null || obj.getClass() != this.getClass()) return false;
		var that = (DetectedBox) obj;
		return this.x == that.x &&
			   this.y == that.y &&
			   this.w == that.w &&
			   this.h == that.h &&
			   this.cx == that.cx &&
			   this.cy == that.cy;
	}

	@Override
	public int hashCode() {
		return Objects.hash(x, y, w, h, cx, cy);
	}

}
