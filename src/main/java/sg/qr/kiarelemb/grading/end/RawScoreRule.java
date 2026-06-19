package sg.qr.kiarelemb.grading.end;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Kiarelemb
 * @projectName QR_Scan_Grade
 * @className RawScoreRule
 * @description TODO
 * @create 2026/6/18 18:31
 */
record RawScoreRule(int lineNumber, String questionType, List<Integer> questionNumbers,
					BigDecimal totalScore) {
	public static RawScoreRule parse(String line, int lineNumber, int questionCount) {
		String[] parts = line.split("[ \\t]+");
		if (parts.length != 2 && parts.length != 3) {
			throw new ProjectEnd.ScoreRuleException(lineNumber, "第 " + lineNumber + " 行计分规则格式错误，应为：题型 题号范围 总分。");
		}
		String questionType = normalizeSectionName(parts[0].trim());
		List<Integer> questionNumbers = parseQuestionRange(parts[1].trim(), lineNumber, questionCount);
		BigDecimal totalScore = null;
		if (parts.length == 3) {
			try {
				totalScore = new BigDecimal(parts[2].trim());
			} catch (NumberFormatException ex) {
				throw new ProjectEnd.ScoreRuleException(lineNumber, "第 " + lineNumber + " 行总分不是有效数字。");
			}
			if (totalScore.compareTo(BigDecimal.ZERO) < 0) {
				throw new ProjectEnd.ScoreRuleException(lineNumber, "第 " + lineNumber + " 行总分不能为负数。");
			}
		}
		return new RawScoreRule(lineNumber, questionType, questionNumbers, totalScore);
	}

	public ScoreRule toScoreRule() {
		return new ScoreRule(questionType, questionNumbers, totalScore);
	}

	private static String normalizeSectionName(String name) {
		if (name.endsWith("题") && name.length() > 1) {
			return name.substring(0, name.length() - 1);
		}
		return name;
	}

	private static List<Integer> parseQuestionRange(String rangeText, int lineNumber, int questionCount) {
		Set<Integer> numbers = new LinkedHashSet<>();
		String[] ranges = rangeText.split("[,，]");
		for (String range : ranges) {
			String part = range.trim();
			if (part.isEmpty()) {
				continue;
			}

			int start;
			int end;
			if (part.contains("-")) {
				String[] bounds = part.split("-", -1);
				if (bounds.length != 2) {
					throw new ProjectEnd.ScoreRuleException(lineNumber, "第 " + lineNumber + " 行题号范围格式错误。");
				}
				start = parseQuestionNumber(bounds[0], lineNumber);
				end = parseQuestionNumber(bounds[1], lineNumber);
			} else {
				start = parseQuestionNumber(part, lineNumber);
				end = start;
			}

			if (start > end) {
				throw new ProjectEnd.ScoreRuleException(lineNumber, "第 " + lineNumber + " 行题号范围起点不能大于终点。");
			}
			if (start < 1 || end > questionCount) {
				throw new ProjectEnd.ScoreRuleException(lineNumber, "第 " + lineNumber + " 行题号超出范围，当前共有 " + questionCount + " 题。");
			}
			for (int number = start; number <= end; number++) {
				numbers.add(number);
			}
		}

		if (numbers.isEmpty()) {
			throw new ProjectEnd.ScoreRuleException(lineNumber, "第 " + lineNumber + " 行没有有效题号。");
		}
		return new ArrayList<>(numbers);
	}

	private static int parseQuestionNumber(String value, int lineNumber) {
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException ex) {
			throw new ProjectEnd.ScoreRuleException(lineNumber, "第 " + lineNumber + " 行题号不是有效整数。");
		}
	}
}