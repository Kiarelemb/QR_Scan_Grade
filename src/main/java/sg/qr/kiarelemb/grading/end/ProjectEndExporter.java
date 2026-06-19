package sg.qr.kiarelemb.grading.end;

import sg.qr.kiarelemb.MainWindow;
import sg.qr.kiarelemb.grading.model.Project;
import sg.qr.kiarelemb.grading.qsmethod.QuestionScoring;
import swing.qr.kiarelemb.window.enhance.QROpinionDialog;
import swing.qr.kiarelemb.window.utils.QRFileSelectDialog;
import swing.qr.kiarelemb.window.utils.QRValueInputDialog;

import javax.swing.table.DefaultTableModel;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public final class ProjectEndExporter {
	public static final ProjectEndExporter EXPORTER = new ProjectEndExporter();
	private static final int QUESTION_SCORE_BLOCK_SIZE = 10;

	private ProjectEndExporter() {
	}

	public void export(ProjectEnd projectEnd) {
		if (projectEnd.scoreResults().isEmpty()) {
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, "请先计算成绩。");
			return;
		}
		QRFileSelectDialog dialog = new QRFileSelectDialog(MainWindow.INSTANCE,
				QRFileSelectDialog.SelectMode.DIRECTORY_ONLY,
				new File("."),
				"请选择成绩导出文件夹");
		dialog.setVisible(true);
		if (!dialog.selectedSucceeded()) {
			return;
		}
		QRValueInputDialog nameInput = new QRValueInputDialog(MainWindow.INSTANCE, "合法的文件名", "请输入导出文件名：");
		nameInput.setDefaultValue("成绩输出");
		nameInput.setVisible(true);
		String fileName = nameInput.getAnswer();
		if (fileName == null) {
			return;
		}
		fileName = fileName.trim();
		if (fileName.isEmpty()) {
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, "文件名不能为空。");
			return;
		}
		if (fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
			fileName = fileName.substring(0, fileName.length() - 4);
		}

		String content = buildScoreExportCsv(projectEnd);
		File directory = dialog.selectedFile();
		try {
			File utf8File = new File(directory, fileName + "_utf8.csv");
			Files.writeString(utf8File.toPath(), content, StandardCharsets.UTF_8);
			File systemFile = new File(directory, fileName + "_gbk.csv");
			Files.writeString(systemFile.toPath(), content, Charset.forName("GBK"));
			QROpinionDialog.messageTellShow(MainWindow.INSTANCE,
					"成绩已导出(请优先打开gbk文件)：\n" + utf8File.getName() + "\n" + systemFile.getName());
		} catch (IOException ex) {
			QROpinionDialog.messageErrShow(MainWindow.INSTANCE, "导出成绩失败：\n" + ex.getMessage());
		}
	}

	private String buildScoreExportCsv(ProjectEnd projectEnd) {
		StringBuilder builder = new StringBuilder();
		appendPersonalScores(builder, projectEnd.resultTableModel());
		appendAnswerData(builder, projectEnd.project());
		appendManualScoreData(builder, projectEnd.project());
		appendQuestionScores(builder, projectEnd);
		appendScoreAnalysis(builder, projectEnd);
		appendExamOverview(builder, projectEnd);
		return builder.toString();
	}

	private void appendPersonalScores(StringBuilder builder, DefaultTableModel resultTableModel) {
		builder.append("===== 个人总分 =====").append(System.lineSeparator());
		for (int col = 0; col < resultTableModel.getColumnCount(); col++) {
			if (col > 0) {
				builder.append(',');
			}
			builder.append(csv(resultTableModel.getColumnName(col)));
		}
		builder.append(System.lineSeparator());
		for (int row = 0; row < resultTableModel.getRowCount(); row++) {
			for (int col = 0; col < resultTableModel.getColumnCount(); col++) {
				if (col > 0) {
					builder.append(',');
				}
				builder.append(csv(resultTableModel.getValueAt(row, col)));
			}
			builder.append(System.lineSeparator());
		}
		builder.append(System.lineSeparator());
	}

	private void appendAnswerData(StringBuilder builder, Project project) {
		builder.append("===== 作答数据 =====").append(System.lineSeparator());
		String[] standardAnswers = project.standardAnswers() == null ? new String[0] : project.standardAnswers();
		List<Object> questionRow = new ArrayList<>();
		questionRow.add("题号");
		for (int i = 1; i <= standardAnswers.length; i++) {
			questionRow.add(i);
		}
		appendCsvRow(builder, questionRow.toArray());

		List<Object> correctRow = new ArrayList<>();
		correctRow.add("正解");
		correctRow.addAll(Arrays.asList(standardAnswers));
		appendCsvRow(builder, correctRow.toArray());

		Map<String, String> studentNames = project.studentNamesByExamId();
		for (Map.Entry<String, String> entry : project.combinedAnswersByExamId().entrySet()) {
			String examId = entry.getKey();
			String displayName = studentNames.getOrDefault(examId, examId);
			String[] answers = splitAnswers(entry.getValue());
			List<Object> answerRow = new ArrayList<>();
			answerRow.add(displayName);
			for (int i = 0; i < standardAnswers.length; i++) {
				answerRow.add(i < answers.length ? answers[i] : "");
			}
			appendCsvRow(builder, answerRow.toArray());
		}
		builder.append(System.lineSeparator());
	}

	private void appendManualScoreData(StringBuilder builder, Project project) {
		Map<String, Map<String, String>> manualScores = project.manualScoresByExamId();
		if (manualScores.isEmpty()) {
			return;
		}
		Set<String> sections = new LinkedHashSet<>();
		for (Map<String, String> scores : manualScores.values()) {
			sections.addAll(scores.keySet());
		}
		builder.append("===== 人工评分数据 =====").append(System.lineSeparator());
		List<Object> header = new ArrayList<>();
		header.add("准考证号");
		header.add("姓名");
		header.addAll(sections);
		appendCsvRow(builder, header.toArray());
		Map<String, String> names = project.studentNamesByExamId();
		for (Map.Entry<String, Map<String, String>> entry : manualScores.entrySet()) {
			List<Object> row = new ArrayList<>();
			row.add(entry.getKey());
			row.add(names.getOrDefault(entry.getKey(), ""));
			for (String section : sections) {
				row.add(entry.getValue().getOrDefault(section, ""));
			}
			appendCsvRow(builder, row.toArray());
		}
		builder.append(System.lineSeparator());
	}

	private void appendQuestionScores(StringBuilder builder, ProjectEnd projectEnd) {
		builder.append("===== 小题赋分 =====").append(System.lineSeparator());
		Map<Integer, BigDecimal> rates = questionCorrectRates(projectEnd);
		QuestionScoring.ScaleScoreReport report = projectEnd.lastScaleReport();
		if (projectEnd.lastResultScaleMode() && report != null) {
			appendScaleConfig(builder, report.config());
		}
		Map<Integer, BigDecimal> weights = projectEnd.lastResultScaleMode() && report != null ? report.weights() : Map.of();
		for (ScoreRule rule : projectEnd.parseDisplayScoreRules(projectEnd.ruleTextPane.getText(), projectEnd.scoreRuleQuestionCount())) {
			List<Integer> questionNumbers = rule.questionNumbers();
			for (int start = 0; start < questionNumbers.size(); start += QUESTION_SCORE_BLOCK_SIZE) {
				List<Integer> block = questionNumbers.subList(start, Math.min(start + QUESTION_SCORE_BLOCK_SIZE, questionNumbers.size()));
				List<Object> questionRow = new ArrayList<>();
				questionRow.add("题号");
				questionRow.addAll(block);
				appendCsvRow(builder, questionRow.toArray());
				appendQuestionValueRow(builder, "赋分", block, projectEnd.scorePlan().questionScores(),
						QuestionValueFormat.SCORE);
				appendQuestionValueRow(builder, "正确率", block, rates, QuestionValueFormat.PERCENT);
				if (projectEnd.lastResultScaleMode()) {
					appendQuestionValueRow(builder, "权重", block, weights, QuestionValueFormat.RAW_TWO_DECIMAL);
				}
				builder.append(System.lineSeparator());
			}
			BigDecimal actual = sectionAverage(projectEnd.scoreResults(), rule.questionType());
			BigDecimal expected = sectionTotalScore(rule.questionNumbers(), projectEnd.scorePlan().questionScores());
			builder.append(csv(rule.questionType() + " : 应得 " + formatTwoDecimal(expected)
							   + " 分，实得均分 " + formatTwoDecimal(actual) + " 分"))
					.append(System.lineSeparator()).append(System.lineSeparator());
		}
	}

	private void appendScaleConfig(StringBuilder builder, QuestionScoring.Config config) {
		appendCsvRow(builder, "尺度参数", "");
		appendCsvRow(builder, "算法", config.weightFunction());
		appendCsvRow(builder, "logit.power", formatDouble(config.logitPower()));
		appendCsvRow(builder, "center.p", formatDouble(config.centerP()));
		appendCsvRow(builder, "min.p", formatDouble(config.minP()));
		appendCsvRow(builder, "max.p", formatDouble(config.maxP()));
		appendCsvRow(builder, "min.weight", formatDouble(config.minWeight()));
		appendCsvRow(builder, "max.weight", formatDouble(config.maxWeight()));
		appendCsvRow(builder, "epsilon", formatDouble(config.epsilon()));
		builder.append(System.lineSeparator());
	}

	private void appendScoreAnalysis(StringBuilder builder, ProjectEnd projectEnd) {
		builder.append("===== 成绩分析 =====").append(System.lineSeparator());
		Map<Integer, Integer> wrongCounts = questionWrongCounts(projectEnd.project());
		int studentCount = Math.max(1, projectEnd.project().combinedAnswersByExamId().size());
		for (ScoreRule rule : projectEnd.parseDisplayScoreRules(projectEnd.ruleTextPane.getText(), projectEnd.scoreRuleQuestionCount())) {
			appendCsvRow(builder, rule.questionType(), "", "", "");
			appendCsvRow(builder, "题号", "错题数", "错题率", "具体分析");
			for (Integer questionNumber : rule.questionNumbers()) {
				int wrong = wrongCounts.getOrDefault(questionNumber, 0);
				BigDecimal wrongRate = BigDecimal.valueOf(wrong)
						.multiply(BigDecimal.valueOf(100))
						.divide(BigDecimal.valueOf(studentCount), 2, RoundingMode.HALF_UP);
				appendCsvRow(builder, questionNumber, wrong, wrongRate.toPlainString() + "%", "");
			}
			builder.append(System.lineSeparator());
		}
	}

	private void appendExamOverview(StringBuilder builder, ProjectEnd projectEnd) {
		builder.append("===== 考试概况 =====").append(System.lineSeparator()).append(System.lineSeparator());
		List<ScoreResult> scoreResults = projectEnd.scoreResults();
		int studentCount = projectEnd.project().combinedAnswersByExamId().size();
		BigDecimal totalAverage = averageScoreResultValue(scoreResults, ScoreResult::earnedScore);
		BigDecimal totalScore = totalScore(projectEnd.scorePlan().questionScores());
		BigDecimal totalRate = totalScore.compareTo(BigDecimal.ZERO) == 0
				? BigDecimal.ZERO
				: totalAverage.multiply(BigDecimal.valueOf(100)).divide(totalScore, 1, RoundingMode.HALF_UP);
		builder.append("本次考试试卷满分").append(formatOneDecimal(totalScore)).append("分，包括");
		List<ScoreRule> rules = projectEnd.parseDisplayScoreRules(projectEnd.ruleTextPane.getText(), projectEnd.scoreRuleQuestionCount());
		for (int i = 0; i < rules.size(); i++) {
			ScoreRule rule = rules.get(i);
			if (i > 0) {
				builder.append("、");
			}
			BigDecimal expected = sectionTotalScore(rule.questionNumbers(), projectEnd.scorePlan().questionScores());
			builder.append(rule.questionType()).append("（").append(formatOneDecimal(expected)).append("分）");
		}
		builder.append("。参考学生").append(studentCount).append("人，班级总分均分")
				.append(formatOneDecimal(totalAverage)).append("分（得分率")
				.append(totalRate.toPlainString()).append("%）。")
				.append(System.lineSeparator()).append(System.lineSeparator());
		builder.append("从各项均分看：").append(System.lineSeparator());
		for (ScoreRule rule : rules) {
			BigDecimal avg = sectionAverage(scoreResults, rule.questionType());
			BigDecimal expected = sectionTotalScore(rule.questionNumbers(), projectEnd.scorePlan().questionScores());
			BigDecimal rate = expected.compareTo(BigDecimal.ZERO) == 0
					? BigDecimal.ZERO
					: avg.multiply(BigDecimal.valueOf(100)).divide(expected, 1, RoundingMode.HALF_UP);
			builder.append(rule.questionType()).append("（满分").append(formatOneDecimal(expected))
					.append("）均分").append(formatOneDecimal(avg))
					.append("，得分率").append(rate.toPlainString()).append("%")
					.append(System.lineSeparator());
		}
	}

	private Map<Integer, BigDecimal> questionCorrectRates(ProjectEnd projectEnd) {
		if (projectEnd.lastResultScaleMode() && projectEnd.lastScaleReport() != null) {
			return projectEnd.lastScaleReport().rawRates();
		}
		Map<Integer, Integer> wrongCounts = questionWrongCounts(projectEnd.project());
		Map<Integer, BigDecimal> rates = new LinkedHashMap<>();
		int studentCount = Math.max(1, projectEnd.project().combinedAnswersByExamId().size());
		for (int questionNumber = 1; questionNumber <= projectEnd.standardAnswerCount(); questionNumber++) {
			int wrong = wrongCounts.getOrDefault(questionNumber, 0);
			BigDecimal correct = BigDecimal.valueOf(studentCount - wrong)
					.divide(BigDecimal.valueOf(studentCount), 6, RoundingMode.HALF_UP);
			rates.put(questionNumber, correct);
		}
		return rates;
	}

	private Map<Integer, Integer> questionWrongCounts(Project project) {
		Map<Integer, Integer> wrongCounts = new LinkedHashMap<>();
		String[] standardAnswers = project.standardAnswers() == null ? new String[0] : project.standardAnswers();
		for (int questionNumber = 1; questionNumber <= standardAnswers.length; questionNumber++) {
			wrongCounts.put(questionNumber, 0);
		}
		for (String answerLine : project.combinedAnswersByExamId().values()) {
			String[] answers = splitAnswers(answerLine);
			for (int i = 0; i < standardAnswers.length; i++) {
				String expected = normalizeAnswer(standardAnswers[i]);
				String actual = i < answers.length ? normalizeAnswer(answers[i]) : "";
				if (expected.isEmpty() || !expected.equals(actual)) {
					int questionNumber = i + 1;
					wrongCounts.put(questionNumber, wrongCounts.getOrDefault(questionNumber, 0) + 1);
				}
			}
		}
		return wrongCounts;
	}

	private BigDecimal sectionAverage(List<ScoreResult> scoreResults, String sectionName) {
		if (scoreResults.isEmpty()) {
			return BigDecimal.ZERO;
		}
		BigDecimal sum = BigDecimal.ZERO;
		int count = 0;
		for (ScoreResult result : scoreResults) {
			BigDecimal value = result.sectionScores().get(sectionName);
			if (value != null) {
				sum = sum.add(value);
				count++;
			}
		}
		return count == 0 ? BigDecimal.ZERO : sum.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
	}

	private BigDecimal sectionTotalScore(List<Integer> questionNumbers, Map<Integer, BigDecimal> questionScores) {
		BigDecimal total = BigDecimal.ZERO;
		for (Integer questionNumber : questionNumbers) {
			total = total.add(questionScores.getOrDefault(questionNumber, BigDecimal.ZERO));
		}
		return total;
	}

	private BigDecimal averageScoreResultValue(List<ScoreResult> scoreResults,
											   java.util.function.Function<ScoreResult, BigDecimal> getter) {
		BigDecimal sum = BigDecimal.ZERO;
		int count = 0;
		for (ScoreResult result : scoreResults) {
			BigDecimal value = getter.apply(result);
			if (value != null) {
				sum = sum.add(value);
				count++;
			}
		}
		return count == 0 ? BigDecimal.ZERO : sum.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
	}

	private void appendQuestionValueRow(StringBuilder builder, String title, List<Integer> questionNumbers,
										Map<Integer, BigDecimal> values, QuestionValueFormat format) {
		List<Object> row = new ArrayList<>();
		row.add(title);
		for (Integer questionNumber : questionNumbers) {
			BigDecimal value = values.get(questionNumber);
			row.add(value == null ? "" : formatQuestionValue(value, format));
		}
		appendCsvRow(builder, row.toArray());
	}

	private String formatQuestionValue(BigDecimal value, QuestionValueFormat format) {
		return switch (format) {
			case SCORE -> formatOneDecimal(value);
			case PERCENT -> formatPercentNumber(value);
			case RAW_TWO_DECIMAL -> formatTwoDecimal(value);
		};
	}

	private void appendCsvRow(StringBuilder builder, Object... values) {
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				builder.append(',');
			}
			builder.append(csv(values[i]));
		}
		builder.append(System.lineSeparator());
	}

	private String csv(Object value) {
		String text = value == null ? "" : String.valueOf(value);
		if (text.contains("\"")) {
			text = text.replace("\"", "\"\"");
		}
		return (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r"))
				? "\"" + text + "\""
				: text;
	}

	private String[] splitAnswers(String answers) {
		String value = answers == null ? "" : answers.trim();
		if (value.isEmpty()) {
			return new String[0];
		}
		return Arrays.stream(value.split("[ \\t\\r\\n]+"))
				.map(String::trim)
				.filter(answer -> !answer.isEmpty())
				.toArray(String[]::new);
	}

	private String normalizeAnswer(String answer) {
		return answer == null ? "" : answer.trim().toUpperCase(Locale.ROOT);
	}

	private BigDecimal totalScore(Map<Integer, BigDecimal> scores) {
		BigDecimal total = BigDecimal.ZERO;
		for (BigDecimal score : scores.values()) {
			total = total.add(score);
		}
		return total;
	}

	private String formatOneDecimal(BigDecimal score) {
		return score.setScale(1, RoundingMode.HALF_UP).toPlainString();
	}

	private String formatTwoDecimal(BigDecimal score) {
		return score.setScale(2, RoundingMode.HALF_UP).toPlainString();
	}

	private String formatDouble(double value) {
		return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
	}

	private String formatPercentNumber(BigDecimal ratio) {
		BigDecimal percent = ratio.multiply(BigDecimal.valueOf(100));
		return percent.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
	}

	private enum QuestionValueFormat {
		SCORE,
		PERCENT,
		RAW_TWO_DECIMAL
	}
}