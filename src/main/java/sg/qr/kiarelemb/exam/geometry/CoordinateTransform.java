package sg.qr.kiarelemb.exam.geometry;

import org.bytedeco.opencv.opencv_core.Rect;
import sg.qr.kiarelemb.exam.processing.RegistrationMarkDetector;

public final class CoordinateTransform {
	private final double sourceScaleX;
	private final double sourceScaleY;
	private final double a;
	private final double b;
	private final double c;
	private final double d;
	private final double e;
	private final double f;

	private CoordinateTransform(double sourceScaleX, double sourceScaleY,
								double scaleX, double scaleY, double offsetX, double offsetY) {
		this.sourceScaleX = sourceScaleX;
		this.sourceScaleY = sourceScaleY;
		this.a = scaleX;
		this.b = 0;
		this.c = offsetX;
		this.d = 0;
		this.e = scaleY;
		this.f = offsetY;
	}

	private CoordinateTransform(double sourceScaleX, double sourceScaleY,
								double a, double b, double c,
								double d, double e, double f) {
		this.sourceScaleX = sourceScaleX;
		this.sourceScaleY = sourceScaleY;
		this.a = a;
		this.b = b;
		this.c = c;
		this.d = d;
		this.e = e;
		this.f = f;
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

	public static CoordinateTransform affine(double sourceScaleX, double sourceScaleY,
											 double a, double b, double c,
											 double d, double e, double f) {
		return new CoordinateTransform(sourceScaleX, sourceScaleY, a, b, c, d, e, f);
	}

	public Rect transform(Rect rect) {
		if (rect == null) {
			return null;
		}
		double x0 = rect.x();
		double y0 = rect.y();
		double x1 = rect.x() + rect.width();
		double y1 = rect.y() + rect.height();
		double[] xs = new double[]{
				transformX(x0, y0),
				transformX(x1, y0),
				transformX(x1, y1),
				transformX(x0, y1)
		};
		double[] ys = new double[]{
				transformY(x0, y0),
				transformY(x1, y0),
				transformY(x1, y1),
				transformY(x0, y1)
		};
		double minX = min(xs);
		double maxX = max(xs);
		double minY = min(ys);
		double maxY = max(ys);
		int x = (int) Math.round(minX);
		int y = (int) Math.round(minY);
		int w = Math.max(1, (int) Math.round(maxX - minX));
		int h = Math.max(1, (int) Math.round(maxY - minY));
		return new Rect(x, y, w, h);
	}

	public double scaleX() {
		return Math.hypot(a, d);
	}

	public double scaleY() {
		return Math.hypot(b, e);
	}

	public double offsetX() {
		return c;
	}

	public double offsetY() {
		return f;
	}

	private double transformX(double x, double y) {
		return a * x * sourceScaleX + b * y * sourceScaleY + c;
	}

	private double transformY(double x, double y) {
		return d * x * sourceScaleX + e * y * sourceScaleY + f;
	}

	private double min(double[] values) {
		double result = values[0];
		for (double value : values) {
			result = Math.min(result, value);
		}
		return result;
	}

	private double max(double[] values) {
		double result = values[0];
		for (double value : values) {
			result = Math.max(result, value);
		}
		return result;
	}
}
