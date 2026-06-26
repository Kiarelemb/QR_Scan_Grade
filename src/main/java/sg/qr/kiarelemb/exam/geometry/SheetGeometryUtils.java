package sg.qr.kiarelemb.exam.geometry;

import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SheetGeometryUtils {
	private SheetGeometryUtils() {
	}

	public static Mat buildCornerMat(Point tl, Point tr, Point br, Point bl) {
		Mat mat = new Mat(4, 1, opencv_core.CV_32FC2);
		FloatIndexer idx = mat.createIndexer();
		idx.put(0, 0, (float) tl.x(), (float) tl.y());
		idx.put(1, 0, (float) tr.x(), (float) tr.y());
		idx.put(2, 0, (float) br.x(), (float) br.y());
		idx.put(3, 0, (float) bl.x(), (float) bl.y());
		idx.release();
		return mat;
	}

	public static List<Integer> clusterValues(int[] values, int tolerance) {
		return clusterValues(values, tolerance, Integer.MAX_VALUE);
	}

	public static List<Integer> clusterValues(int[] values, int tolerance, int maxSpread) {
		if (values.length == 0) return Collections.emptyList();
		int[] sorted = values.clone();
		Arrays.sort(sorted);
		List<Integer> clusters = new ArrayList<>();
		List<Integer> group = new ArrayList<>();
		group.add(sorted[0]);
		for (int i = 1; i < sorted.length; i++) {
			if (sorted[i] - sorted[i - 1] <= tolerance && sorted[i] - group.get(0) <= maxSpread) {
				group.add(sorted[i]);
			} else {
				clusters.add(group.get(group.size() / 2));
				group.clear();
				group.add(sorted[i]);
			}
		}
		clusters.add(group.get(group.size() / 2));
		return clusters;
	}

	public static int medianInt(int[] values) {
		if (values.length == 0) return 0;
		int[] sorted = values.clone();
		Arrays.sort(sorted);
		return sorted[sorted.length / 2];
	}

	public static int dominantCount(List<Integer> counts) {
		if (counts == null || counts.isEmpty()) {
			return 0;
		}
		Map<Integer, Integer> frequencies = new HashMap<>();
		for (int count : counts) {
			frequencies.put(count, frequencies.getOrDefault(count, 0) + 1);
		}
		return frequencies.entrySet().stream()
				.max(Comparator.<Map.Entry<Integer, Integer>>comparingInt(Map.Entry::getValue)
						.thenComparingInt(Map.Entry::getKey))
				.map(Map.Entry::getKey)
				.orElse(0);
	}

	public static List<Integer> closestColumnIndexes(List<Integer> colXs, List<Integer> referenceColXs) {
		List<Integer> result = new ArrayList<>();
		boolean[] used = new boolean[colXs.size()];
		for (int referenceX : referenceColXs) {
			int bestIndex = -1;
			int bestDistance = Integer.MAX_VALUE;
			for (int i = 0; i < colXs.size(); i++) {
				if (used[i]) {
					continue;
				}
				int distance = Math.abs(colXs.get(i) - referenceX);
				if (distance < bestDistance) {
					bestDistance = distance;
					bestIndex = i;
				}
			}
			if (bestIndex >= 0) {
				used[bestIndex] = true;
				result.add(bestIndex);
			}
		}
		Collections.sort(result);
		return result;
	}

	public static void sortCorners(Point[] corners) {
		double cx = 0, cy = 0;
		for (Point p : corners) {
			cx += p.x();
			cy += p.y();
		}
		cx /= 4;
		cy /= 4;
		final double cxx = cx, cyy = cy;
		Arrays.sort(corners, Comparator.comparingDouble(a -> Math.atan2(a.y() - cyy, a.x() - cxx)));
		int tl = 0;
		double minSum = corners[0].x() + corners[0].y();
		for (int i = 1; i < 4; i++) {
			double s = corners[i].x() + corners[i].y();
			if (s < minSum) {
				minSum = s;
				tl = i;
			}
		}
		Point[] sorted = new Point[4];
		for (int i = 0; i < 4; i++) sorted[i] = corners[(tl + i) % 4];
		System.arraycopy(sorted, 0, corners, 0, 4);
	}
}
