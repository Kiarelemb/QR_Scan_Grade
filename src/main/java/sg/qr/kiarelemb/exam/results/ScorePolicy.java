package sg.qr.kiarelemb.exam.results;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Kiarelemb
 * @projectName QR_Scan_Grade
 * @className ScorePolicy
 * @description TODO
 * @create 2026/6/18 18:32
 */
public record ScorePolicy(String questionType, List<Integer> questionNumbers, BigDecimal totalScore) {
	public static ScorePolicy merge(List<RawScorePolicy> rawRules) {
		List<String> names = new ArrayList<>();
		Set<Integer> numbers = new LinkedHashSet<>();
		BigDecimal totalScore = BigDecimal.ZERO;
		for (RawScorePolicy rawRule : rawRules) {
			if (!names.contains(rawRule.questionType())) {
				names.add(rawRule.questionType());
			}
			numbers.addAll(rawRule.questionNumbers());
			if (rawRule.totalScore() != null) {
				totalScore = rawRule.totalScore();
			}
		}
		return new ScorePolicy(String.join("+", names), new ArrayList<>(numbers), totalScore);
	}
}
