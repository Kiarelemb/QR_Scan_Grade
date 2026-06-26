package sg.qr.kiarelemb.exam.geometry;

import org.bytedeco.opencv.opencv_core.Rect;
import sg.qr.kiarelemb.exam.processing.RegistrationMarkDetector;

public final class CoordinateTransform {
	private final double sourceScaleX;
	private final double sourceScaleY;
	private final double scaleX;
	private final double scaleY;
	private final double offsetX;
	private final double offsetY;

	private CoordinateTransform(double sourceScaleX, double sourceScaleY,
								double scaleX, double scaleY, double offsetX, double offsetY) {
		this.sourceScaleX = sourceScaleX;
		this.sourceScaleY = sourceScaleY;
		this.scaleX = scaleX;
		this.scaleY = scaleY;
		this.offsetX = offsetX;
		this.offsetY = offsetY;
	}

	public static CoordinateTransform from(RegistrationMarkDetector.MarkBounds source,
										   RegistrationMarkDetector.MarkBounds target,
										   double sourceScaleX, double sourceScaleY) {
		double scaleX = (target.maxX() - target.minX()) / (source.maxX() - source.minX());
		double scaleY = (target.maxY() - target.minY()) / (source.maxY() - source.minY());
		double offsetX = target.minX() - source.minX() * scaleX;
		double offsetY = target.minY() - source.minY() * scaleY;
		return new CoordinateTransform(sourceScaleX, sourceScaleY, scaleX, scaleY, offsetX, offsetY);
	}

	public Rect transform(Rect rect) {
		if (rect == null) {
			return null;
		}
		double x1 = transformX(rect.x());
		double y1 = transformY(rect.y());
		double x2 = transformX(rect.x() + rect.width());
		double y2 = transformY(rect.y() + rect.height());
		int x = (int) Math.round(Math.min(x1, x2));
		int y = (int) Math.round(Math.min(y1, y2));
		int w = Math.max(1, (int) Math.round(Math.abs(x2 - x1)));
		int h = Math.max(1, (int) Math.round(Math.abs(y2 - y1)));
		return new Rect(x, y, w, h);
	}

	public double scaleX() {
		return scaleX;
	}

	public double scaleY() {
		return scaleY;
	}

	public double offsetX() {
		return offsetX;
	}

	public double offsetY() {
		return offsetY;
	}

	private double transformX(int x) {
		return x * sourceScaleX * scaleX + offsetX;
	}

	private double transformY(int y) {
		return y * sourceScaleY * scaleY + offsetY;
	}
}
