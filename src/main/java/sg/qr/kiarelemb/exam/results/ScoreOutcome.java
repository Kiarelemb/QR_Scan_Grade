package sg.qr.kiarelemb.exam.results;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Kiarelemb
 * @projectName QR_Scan_Grade
 * @className ScoreOutcome
 * @description TODO
 * @create 2026/6/12 22:11
 */
public record ScoreOutcome(int rank, String examineeId, String name, BigDecimal entranceEnglishScore,
						   Map<String, BigDecimal> sectionScores, BigDecimal earnedScore,
						   BigDecimal improvementScore) {
	public ScoreOutcome {
		sectionScores = Collections.unmodifiableMap(new LinkedHashMap<>(sectionScores));
	}
}
