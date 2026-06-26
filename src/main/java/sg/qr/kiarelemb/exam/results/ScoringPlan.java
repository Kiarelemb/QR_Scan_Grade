package sg.qr.kiarelemb.exam.results;

import sg.qr.kiarelemb.data.Utils;

import java.math.BigDecimal;
import java.util.*;

/**
 * @author Kiarelemb
 * @projectName QR_Scan_Grade
 * @className ScoringPlan
 * @description TODO
 * @create 2026/6/12 22:13
 */
public record ScoringPlan(Map<Integer, BigDecimal> questionScores, Map<Integer, String> questionSections,
						  List<String> sectionNames) {
	public ScoringPlan {
		questionScores = Collections.unmodifiableMap(new LinkedHashMap<>(questionScores));
		questionSections = Collections.unmodifiableMap(new LinkedHashMap<>(questionSections));
		sectionNames = Collections.unmodifiableList(new ArrayList<>(sectionNames));
	}

	public static ScoringPlan empty() {
		return new ScoringPlan(new LinkedHashMap<>(), new LinkedHashMap<>(), new ArrayList<>());
	}

	public BigDecimal totalScore() {
		return Utils.totalScore(questionScores);
	}
}
