package sg.qr.kiarelemb.exam.template.detect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ChoiceLayoutAnalyzer {
	private ChoiceLayoutAnalyzer() {
	}

	static int inferOptionGap(List<Integer> sortedXCoords) {
		if (sortedXCoords == null || sortedXCoords.size() < 3) {
			return 0;
		}
		Map<Integer, Integer> gapFrequencies = new HashMap<>();
		for (int i = 1; i < sortedXCoords.size(); i++) {
			int gap = sortedXCoords.get(i) - sortedXCoords.get(i - 1);
			if (gap >= 50 && gap <= 115) {
				int bucket = Math.round(gap / 5.0f) * 5;
				gapFrequencies.put(bucket, gapFrequencies.getOrDefault(bucket, 0) + 1);
			}
		}
		if (gapFrequencies.isEmpty()) {
			return sortedXCoords.get(1) - sortedXCoords.get(0);
		}
		return gapFrequencies.entrySet().stream()
				.max(Comparator.<Map.Entry<Integer, Integer>>comparingInt(Map.Entry::getValue)
						.thenComparingInt(entry -> -Math.abs(entry.getKey() - 85)))
				.map(Map.Entry::getKey)
				.orElse(sortedXCoords.get(1) - sortedXCoords.get(0));
	}

	static List<Integer> choiceColumnStarts(List<Integer> sortedXCoords, int bubbleW) {
		List<int[]> groups = choiceColumnGroups(sortedXCoords, bubbleW);
		List<Integer> colStarts = new ArrayList<>();
		for (int[] group : groups) {
			colStarts.add(group[0]);
		}
		return colStarts;
	}

	static List<int[]> choiceColumnGroups(List<Integer> sortedXCoords, int bubbleW) {
		if (sortedXCoords == null || sortedXCoords.size() < 3) {
			return Collections.emptyList();
		}
		int optionGap = inferOptionGap(sortedXCoords);
		if (optionGap <= 0) {
			return Collections.emptyList();
		}
		int columnBreakGap = Math.max(100, optionGap * 2);

		List<int[]> result = new ArrayList<>();
		int groupStart = 0;
		for (int i = 1; i <= sortedXCoords.size(); i++) {
			if (i == sortedXCoords.size()
				|| sortedXCoords.get(i) - sortedXCoords.get(i - 1) > columnBreakGap) {
				extractColumnsFromGroup(sortedXCoords, groupStart, i, optionGap, bubbleW, result);
				groupStart = i;
			}
		}
		return result;
	}

	private static void extractColumnsFromGroup(List<Integer> coords, int start, int end,
												int optionGap, int bubbleW, List<int[]> result) {
		int size = end - start;
		if (size < 3) {
			return;
		}
		if (size <= 4) {
			result.add(new int[]{coords.get(start) - bubbleW / 2, size});
			return;
		}
		int tol = Math.max(22, optionGap / 3);
		boolean[] used = new boolean[size];
		for (int i = 0; i < size; i++) {
			if (used[i]) continue;
			int baseX = coords.get(start + i);
			List<Integer> seq = new ArrayList<>();
			seq.add(baseX);
			int nextTarget = baseX + optionGap;
			for (int j = i + 1; j < size && seq.size() < 4; j++) {
				if (used[j]) continue;
				int ax = coords.get(start + j);
				if (ax >= nextTarget - tol && ax <= nextTarget + tol) {
					seq.add(ax);
					nextTarget = ax + optionGap;
				} else if (ax > nextTarget + tol) {
					break;
				}
			}
			int cnt = seq.size();
			if (cnt >= 3 && cnt <= 4 && hasConsistentGaps(seq, optionGap, tol)) {
				result.add(new int[]{seq.get(0) - bubbleW / 2, cnt});
				for (Integer sx : seq) {
					for (int j = i; j < size; j++) {
						if (!used[j] && coords.get(start + j).equals(sx)) {
							used[j] = true;
							break;
						}
					}
				}
			}
		}
	}

	private static boolean hasConsistentGaps(List<Integer> coords, int expectedGap, int tolerance) {
		for (int i = 1; i < coords.size(); i++) {
			if (Math.abs(coords.get(i) - coords.get(i - 1) - expectedGap) > tolerance) {
				return false;
			}
		}
		return true;
	}
}
