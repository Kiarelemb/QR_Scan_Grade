package sg.qr.kiarelemb.data;

import org.bytedeco.opencv.opencv_core.Mat;
import sg.qr.kiarelemb.exam.results.ScoringPlan;
import sg.qr.kiarelemb.exam.results.ScoreOutcome;
import sg.qr.kiarelemb.exam.model.GradingProject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static org.bytedeco.opencv.global.opencv_core.CV_8UC3;

/**
 * @author Kiarelemb
 * @projectName QR_Scan_Grade
 * @className Utils
 * @description TODO
 * @create 2026/6/4 13:10
 */
public class Utils {

	/**
	 * 读取图片文件
	 *
	 * @param file 含中文的文件路径
	 * @return Mat
	 */
	public static Mat imreadUnicode(File file) {
		try {
			BufferedImage image = ImageIO.read(file);
			if (image == null) {
				return new Mat();
			}

			BufferedImage bgr = new BufferedImage(
					image.getWidth(),
					image.getHeight(),
					BufferedImage.TYPE_3BYTE_BGR
			);
			bgr.getGraphics().drawImage(image, 0, 0, null);

			byte[] pixels = ((DataBufferByte) bgr.getRaster().getDataBuffer()).getData();
			Mat mat = new Mat(bgr.getHeight(), bgr.getWidth(), CV_8UC3);
			mat.data().put(pixels);
			return mat;
		} catch (Exception e) {
			return new Mat();
		}
	}

	public static List<ScoreOutcome> calculateNormalScores(GradingProject project, Map<Integer, BigDecimal> questionScores) {
		return calculateNormalScores(project, new ScoringPlan(questionScores, defaultSections(questionScores), List.of("选择")));
	}

	public static List<ScoreOutcome> calculateNormalScores(GradingProject project, ScoringPlan scorePlan) {
		String[] standardAnswers = project.standardAnswers() == null ? new String[0] : project.standardAnswers();
		List<ScoreOutcome> results = new ArrayList<>();

		for (Map.Entry<String, String> entry : project.combinedAnswersByExamId().entrySet()) {
			String examineeId = entry.getKey();
			String[] answers = splitAnswers(entry.getValue());
			BigDecimal earnedScore = BigDecimal.ZERO;
			Map<String, BigDecimal> sectionScores = emptySectionScores(scorePlan.sectionNames());

			for (int i = 0; i < standardAnswers.length; i++) {
				String expected = normalizeAnswer(standardAnswers[i]);
				String actual = i < answers.length ? normalizeAnswer(answers[i]) : "";
				if (!expected.isEmpty() && expected.equals(actual)) {
					int questionNumber = i + 1;
					BigDecimal questionScore = scorePlan.questionScores().getOrDefault(questionNumber, BigDecimal.ZERO);
					String sectionName = scorePlan.questionSections().getOrDefault(questionNumber, "");
					earnedScore = earnedScore.add(questionScore);
					if (!sectionName.isEmpty()) {
						sectionScores.put(sectionName, sectionScores.getOrDefault(sectionName, BigDecimal.ZERO).add(questionScore));
					}
				}
			}

			results.add(new ScoreOutcome(0, examineeId, "", null, sectionScores, earnedScore, null));
		}

		results.sort(Comparator.comparing(ScoreOutcome::earnedScore).reversed()
				.thenComparing(ScoreOutcome::examineeId));
		return rankedResults(results);
	}

	public static Map<Integer, String> defaultSections(Map<Integer, BigDecimal> questionScores) {
		Map<Integer, String> sections = new LinkedHashMap<>();
		for (Integer questionNumber : questionScores.keySet()) {
			sections.put(questionNumber, "选择");
		}
		return sections;
	}

	public static Map<String, BigDecimal> emptySectionScores(List<String> sectionNames) {
		Map<String, BigDecimal> scores = new LinkedHashMap<>();
		for (String sectionName : sectionNames) {
			scores.put(sectionName, BigDecimal.ZERO);
		}
		return scores;
	}

	public static List<ScoreOutcome> rankedResults(List<ScoreOutcome> sortedResults) {
		List<ScoreOutcome> ranked = new ArrayList<>();
		BigDecimal previousScore = null;
		int rank = 0;
		for (int i = 0; i < sortedResults.size(); i++) {
			ScoreOutcome result = sortedResults.get(i);
			if (previousScore == null || result.earnedScore().compareTo(previousScore) != 0) {
				rank = i + 1;
				previousScore = result.earnedScore();
			}
			ranked.add(new ScoreOutcome(
					rank,
					result.examineeId(),
					result.name(),
					result.entranceEnglishScore(),
					result.sectionScores(),
					result.earnedScore(),
					result.improvementScore()
			));
		}
		return ranked;
	}

	public static List<String> buildExportRows(GradingProject project) {
		List<String> rows = new ArrayList<>();
		for (Map.Entry<String, String> entry : project.combinedAnswersByExamId().entrySet()) {
			rows.add(entry.getKey() + "\t" + normalizeAnswerLine(entry.getValue()));
		}
		return rows;
	}

	public static BigDecimal totalScore(Map<Integer, BigDecimal> scores) {
		BigDecimal total = BigDecimal.ZERO;
		for (BigDecimal score : scores.values()) {
			total = total.add(score);
		}
		return total;
	}

	public static String stripComment(String line) {
		int index = line.trim().indexOf('#');
		return index >= 0 ? line.substring(0, index) : line;
	}

	public static String[] splitAnswers(String answers) {
		String value = answers == null ? "" : answers.trim();
		if (value.isEmpty()) {
			return new String[0];
		}
		return Arrays.stream(value.split("[ \\t\\r\\n]+"))
				.map(String::trim)
				.filter(answer -> !answer.isEmpty())
				.toArray(String[]::new);
	}

	public static String normalizeAnswer(String answer) {
		return answer == null ? "" : answer.trim().toUpperCase(Locale.ROOT);
	}

	public static String normalizeAnswerLine(String answers) {
		return String.join(" ", splitAnswers(answers));
	}

	public static String formatScore(BigDecimal score) {
		return score.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
	}
}
