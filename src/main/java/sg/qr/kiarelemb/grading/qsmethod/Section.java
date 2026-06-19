package sg.qr.kiarelemb.grading.qsmethod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Kiarelemb
 * @projectName QR_QSMethod
 * @className Section
 * @description TODO
 * @create 2026/4/6 19:54
 */
public record Section(
		String name,
		int startIdx,
		int endIdx,
		double totalScore,
		List<Integer> questionIndices) {

	public Section(String name, int startIdx, int endIdx, double totalScore) {
		this(name, startIdx, endIdx, totalScore, questionRange(startIdx, endIdx));
	}

	public Section {
		questionIndices = Collections.unmodifiableList(new ArrayList<>(questionIndices));
	}

	private static List<Integer> questionRange(int startIdx, int endIdx) {
		List<Integer> indices = new ArrayList<>();
		for (int i = startIdx; i <= endIdx; i++) {
			indices.add(i - 1);
		}
		return indices;
	}

	public int size() {
		return questionIndices.size();
	}
}
