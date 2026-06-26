package sg.qr.kiarelemb.exam.scoring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public final class QuestionScorePolicy {
	private final String[] correctAnswers;
	private final Map<String, String> recognizedAnswers;
	private final List<ScoreSection> sections;
	private final Map<String, BigDecimal> entranceEnglishScores;
	private final Config config;
	private final int totalQuestions;

	public QuestionScorePolicy(String[] standardAnswers,
							   Map<String, String> recognizedAnswers,
							   List<ScoreSection> sections,
							   Map<String, BigDecimal> entranceEnglishScores,
							   Config config) {
		this.correctAnswers = standardAnswers == null ? new String[0] : standardAnswers.clone();
		this.recognizedAnswers = recognizedAnswers == null ? Map.of() : new LinkedHashMap<>(recognizedAnswers);
		this.sections = sections == null ? List.of() : List.copyOf(sections);
		this.entranceEnglishScores = entranceEnglishScores == null ? Map.of() : new LinkedHashMap<>(entranceEnglishScores);
		this.config = config == null ? Config.defaults() : config.normalized();
		this.totalQuestions = this.correctAnswers.length;
	}

	public ScaleScoreReport calculateScaleScores() {
		if (totalQuestions == 0 || recognizedAnswers.isEmpty()) {
			return ScaleScoreReport.empty();
		}
		validateSections();

		List<StudentAnswers> students = parseStudents();
		if (students.isEmpty()) {
			return ScaleScoreReport.empty();
		}

		boolean[][] absent = buildAbsentMatrix(students);
		double[] rawRate = computeRawRates(students, absent);
		double[] boundedRate = computeBoundedRates(rawRate);
		double[] weights = computeWeights(boundedRate);
		double[] questionScores = computeScaleQuestionScores(weights);
		double[] sectionAverage = computeSectionAverages(students, absent, questionScores);
		List<ScaleStudentScore> studentScores = computeStudentScores(students, absent, questionScores, sectionAverage);

		return new ScaleScoreReport(
				toBigDecimalMap(questionScores),
				toBigDecimalMap(rawRate),
				toBigDecimalMap(weights),
				buildSectionTotals(questionScores),
				studentScores,
				sectionNames(),
				config
		);
	}

	private void validateSections() {
		if (sections.isEmpty()) {
			throw new IllegalArgumentException("请填写计分规则。");
		}
		for (ScoreSection section : sections) {
			if (section.questionIndices().isEmpty()) {
				throw new IllegalArgumentException("大题没有有效题号：" + section.name());
			}
			for (int questionIndex : section.questionIndices()) {
				if (questionIndex < 0 || questionIndex >= totalQuestions) {
					throw new IllegalArgumentException("大题范围错误：" + section.name());
				}
			}
		}
	}

	private List<StudentAnswers> parseStudents() {
		List<StudentAnswers> students = new ArrayList<>();
		for (Map.Entry<String, String> entry : recognizedAnswers.entrySet()) {
			String examineeId = entry.getKey();
			String[] answers = splitAnswers(entry.getValue());
			Integer[] correctFlags = new Integer[totalQuestions];
			for (int q = 0; q < totalQuestions; q++) {
				String actual = q < answers.length ? normalizeAnswer(answers[q]) : "";
				String expected = normalizeAnswer(correctAnswers[q]);
				correctFlags[q] = (!actual.isEmpty() && actual.equals(expected)) ? 1 : 0;
			}
			students.add(new StudentAnswers(examineeId, answers, new Examinee(examineeId, correctFlags)));
		}
		return students;
	}

	private boolean[][] buildAbsentMatrix(List<StudentAnswers> students) {
		boolean[][] absent = new boolean[students.size()][sections.size()];
		for (int s = 0; s < sections.size(); s++) {
			ScoreSection section = sections.get(s);
			for (int i = 0; i < students.size(); i++) {
				absent[i][s] = isAbsentInSection(students.get(i), section);
			}
		}
		return absent;
	}

	private boolean isAbsentInSection(StudentAnswers student, ScoreSection section) {
		String[] answers = student.rawAnswers();
		for (int idx : section.questionIndices()) {
			if (idx >= answers.length || !"Z".equalsIgnoreCase(answers[idx])) {
				return false;
			}
		}
		return true;
	}

	private double[] computeRawRates(List<StudentAnswers> students, boolean[][] absent) {
		double[] rawRate = new double[totalQuestions];
		for (int q = 0; q < totalQuestions; q++) {
			int valid = 0;
			int correct = 0;
			int sectionIdx = findSectionIndexByQuestion(q);
			for (int i = 0; i < students.size(); i++) {
				if (sectionIdx >= 0 && absent[i][sectionIdx]) {
					continue;
				}
				valid++;
				if (students.get(i).student().answers()[q] == 1) {
					correct++;
				}
			}
			rawRate[q] = valid == 0 ? 0.5 : (double) correct / valid;
		}
		return rawRate;
	}

	private double[] computeBoundedRates(double[] rawRate) {
		double[] boundedRate = new double[rawRate.length];
		for (int i = 0; i < rawRate.length; i++) {
			boundedRate[i] = Math.min(config.maxP(), Math.max(config.minP(), rawRate[i]));
		}
		return boundedRate;
	}

	private double[] computeWeights(double[] boundedRate) {
		double[] weights = new double[boundedRate.length];
		for (int i = 0; i < boundedRate.length; i++) {
			weights[i] = Math.min(config.maxWeight(), Math.max(config.minWeight(), computeWeight(boundedRate[i])));
		}
		return weights;
	}

	private double computeWeight(double p) {
		return switch (config.weightFunction()) {
			case INVERSE -> 1.0 / (p + config.epsilon());
			case NEG_LOG -> -Math.log(p + config.epsilon());
			case LOGIT_ABS -> Math.pow(Math.abs(logit(p) - logit(config.centerP())), config.logitPower());
		};
	}

	private double logit(double p) {
		return Math.log((p + config.epsilon()) / (1 - p + config.epsilon()));
	}

	private double[] computeScaleQuestionScores(double[] weights) {
		double[] scores = new double[totalQuestions];
		for (ScoreSection section : sections) {
			List<Integer> indices = section.questionIndices();
			double sumWeight = indices.stream().mapToDouble(i -> weights[i]).sum();
			double[] temp = new double[indices.size()];
			double allocated = 0;
			for (int i = 0; i < indices.size(); i++) {
				double score = section.totalScore() * (weights[indices.get(i)] / sumWeight);
				double rounded = round(score);
				temp[i] = rounded;
				allocated += rounded;
			}
			double diff = round(section.totalScore() - allocated);
			if (Math.abs(diff) > config.epsilon() && !indices.isEmpty()) {
				adjustScores(temp, indices, weights, diff);
			}
			for (int i = 0; i < indices.size(); i++) {
				scores[indices.get(i)] = temp[i];
			}
		}
		return scores;
	}

	private double[] computeSectionAverages(List<StudentAnswers> students, boolean[][] absent, double[] questionScores) {
		double[] sectionAvg = new double[sections.size()];
		for (int s = 0; s < sections.size(); s++) {
			ScoreSection section = sections.get(s);
			double sumScore = 0.0;
			int count = 0;
			for (int i = 0; i < students.size(); i++) {
				if (absent[i][s]) {
					continue;
				}
				sumScore += scoreSection(students.get(i), section, questionScores);
				count++;
			}
			sectionAvg[s] = count == 0 ? 0.0 : sumScore / count;
		}
		return sectionAvg;
	}

	private List<ScaleStudentScore> computeStudentScores(List<StudentAnswers> students, boolean[][] absent, double[] questionScores, double[] sectionAvg) {
		List<ScaleStudentScore> scores = new ArrayList<>();
		for (int i = 0; i < students.size(); i++) {
			StudentAnswers student = students.get(i);
			Map<String, BigDecimal> sectionScores = new LinkedHashMap<>();
			double totalScore = 0.0;
			for (int s = 0; s < sections.size(); s++) {
				ScoreSection section = sections.get(s);
				double partScore = absent[i][s] ? sectionAvg[s] : scoreSection(student, section, questionScores);
				totalScore += partScore;
				BigDecimal score = toScore(partScore, 1);
				sectionScores.put(section.name(), score);
			}
			BigDecimal total = toScore(totalScore, 1);
			BigDecimal entranceScore = entranceEnglishScores.get(student.examineeId());
			scores.add(new ScaleStudentScore(0, student.examineeId(), "", entranceScore, sectionScores, total,
					entranceScore == null ? null : total.subtract(entranceScore)));
		}
		scores.sort(Comparator.comparing(ScaleStudentScore::earnedScore).reversed().thenComparing(ScaleStudentScore::examineeId));
		return rankedScores(scores);
	}

	private double scoreSection(StudentAnswers student, ScoreSection section, double[] questionScores) {
		double score = 0.0;
		for (int idx : section.questionIndices()) {
			if (student.student().answers()[idx] == 1) {
				score += questionScores[idx];
			}
		}
		return score;
	}

	private List<ScaleStudentScore> rankedScores(List<ScaleStudentScore> sortedScores) {
		List<ScaleStudentScore> ranked = new ArrayList<>();
		BigDecimal previousScore = null;
		int rank = 0;
		for (int i = 0; i < sortedScores.size(); i++) {
			ScaleStudentScore score = sortedScores.get(i);
			if (previousScore == null || score.earnedScore().compareTo(previousScore) != 0) {
				rank = i + 1;
				previousScore = score.earnedScore();
			}
			ranked.add(new ScaleStudentScore(rank, score.examineeId(), score.name(), score.entranceEnglishScore(), score.sectionScores(), score.earnedScore(), score.improvementScore()));
		}
		return ranked;
	}

	private void adjustScores(double[] scores, List<Integer> indices, double[] weights, double diff) {
		if (Math.abs(diff) < config.epsilon()) {
			return;
		}
		int target = 0;
		for (int i = 1; i < indices.size(); i++) {
			if ((diff > 0 && weights[indices.get(i)] > weights[indices.get(target)])
				|| (diff < 0 && weights[indices.get(i)] < weights[indices.get(target)])) {
				target = i;
			}
		}
		scores[target] = round(scores[target] + diff);
	}

	private int findSectionIndexByQuestion(int questionIndex) {
		for (int s = 0; s < sections.size(); s++) {
			if (sections.get(s).questionIndices().contains(questionIndex)) {
				return s;
			}
		}
		return -1;
	}

	private Map<Integer, BigDecimal> toBigDecimalMap(double[] values) {
		Map<Integer, BigDecimal> map = new LinkedHashMap<>();
		for (int i = 0; i < values.length; i++) {
			map.put(i + 1, toScore(values[i], 4));
		}
		return map;
	}

	private Map<String, BigDecimal> buildSectionTotals(double[] questionScores) {
		Map<String, BigDecimal> totals = new LinkedHashMap<>();
		for (ScoreSection section : sections) {
			double sum = section.questionIndices().stream().mapToDouble(i -> questionScores[i]).sum();
			totals.put(section.name(), toScore(sum, 2));
		}
		return totals;
	}

	private List<String> sectionNames() {
		List<String> names = new ArrayList<>();
		for (ScoreSection section : sections) {
			if (!names.contains(section.name())) {
				names.add(section.name());
			}
		}
		return names;
	}

	private static BigDecimal toScore(double value, int scale) {
		return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros();
	}

	private static double round(double value) {
		double base = Math.pow(10, 2);
		return Math.round(value * base) / base;
	}

	private static String[] splitAnswers(String answers) {
		String value = answers == null ? "" : answers.trim();
		return value.isEmpty() ? new String[0] : value.split("[ \\t\\r\\n]+");
	}

	private static String normalizeAnswer(String answer) {
		return answer == null ? "" : answer.trim().toUpperCase(Locale.ROOT);
	}

	private record StudentAnswers(String examineeId, String[] rawAnswers, Examinee student) {
	}

	public enum WeightFunction {
		INVERSE,
		NEG_LOG,
		LOGIT_ABS
	}

	public record Config(WeightFunction weightFunction,
						 double logitPower,
						 double centerP,
						 double minP,
						 double maxP,
						 double minWeight,
						 double maxWeight,
						 double epsilon) {
		public static Config defaults() {
			return new Config(WeightFunction.LOGIT_ABS, 0.5, 0.5, 0.1, 0.9, 0.3, 2.0, 1e-6);
		}

		private Config normalized() {
			WeightFunction function = weightFunction == null ? WeightFunction.LOGIT_ABS : weightFunction;
			double normalizedMinP = minP <= 0 ? 1e-4 : minP;
			double normalizedMaxP = maxP >= 1 ? 1 - 1e-4 : maxP;
			if (normalizedMaxP <= normalizedMinP) {
				normalizedMaxP = Math.min(1 - 1e-4, normalizedMinP + 0.01);
			}
			double normalizedMinWeight = minWeight <= 0 ? 0.1 : minWeight;
			double normalizedMaxWeight = maxWeight <= normalizedMinWeight ? normalizedMinWeight + 1 : maxWeight;
			double normalizedEpsilon = epsilon <= 0 ? 1e-6 : epsilon;
			double normalizedLogitPower = logitPower <= 0 ? 1.0 : logitPower;
			double normalizedCenterP = Math.min(normalizedMaxP, Math.max(normalizedMinP, centerP));
			return new Config(function, normalizedLogitPower, normalizedCenterP, normalizedMinP, normalizedMaxP, normalizedMinWeight, normalizedMaxWeight, normalizedEpsilon);
		}
	}

	public record ScaleScoreReport(Map<Integer, BigDecimal> questionScores,
								   Map<Integer, BigDecimal> rawRates,
								   Map<Integer, BigDecimal> weights,
								   Map<String, BigDecimal> sectionTotals,
								   List<ScaleStudentScore> studentScores,
								   List<String> sectionNames,
								   Config config) {
		public ScaleScoreReport {
			questionScores = Collections.unmodifiableMap(new LinkedHashMap<>(questionScores));
			rawRates = Collections.unmodifiableMap(new LinkedHashMap<>(rawRates));
			weights = Collections.unmodifiableMap(new LinkedHashMap<>(weights));
			sectionTotals = Collections.unmodifiableMap(new LinkedHashMap<>(sectionTotals));
			studentScores = List.copyOf(studentScores);
			sectionNames = List.copyOf(sectionNames);
		}

		private static ScaleScoreReport empty() {
			return new ScaleScoreReport(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new ArrayList<>(), new ArrayList<>(), Config.defaults());
		}
	}

	public record ScaleStudentScore(int rank,
									String examineeId,
									String name,
									BigDecimal entranceEnglishScore,
									Map<String, BigDecimal> sectionScores,
									BigDecimal earnedScore,
									BigDecimal improvementScore) {
		public ScaleStudentScore {
			sectionScores = Collections.unmodifiableMap(new LinkedHashMap<>(sectionScores));
		}
	}
}
