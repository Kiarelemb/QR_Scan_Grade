package sg.qr.kiarelemb.grading.qsmethod;

import method.qr.kiarelemb.utils.QRMathUtils;

import java.util.List;

/**
 * @author Kiarelemb
 * @projectName QR_QSMethod
 * @className Student
 * @description TODO
 * @create 2026/4/6 19:14
 */
public record Student(String name, Integer[] answers) {

	public double getPartMark(double[] questionScores, int start, int end) {
		double score = 0;
		for (int i = start; i < end; i++) {
			score += getQuestionMark(questionScores, i);
		}
		return score;
	}

	public int getRightNumber(int start, int end) {
		int right = 0;
		for (int i = start; i < end; i++) {
			if (answerRight(i)) right++;
		}
		return right;
	}

	public double getQuestionMark(double[] questionScores, int startIndex) {
		return answerRight(startIndex) ? questionScores[startIndex] : 0;
	}

	public boolean answerRight(int startIndex) {
		return answers[startIndex] == 1;
	}

	public String toString(double[] questionScores, List<Section> sections) {
		StringBuilder sb = new StringBuilder(this.name + "\t");
		double score = 0;
		double part;
		for (Section section : sections) {
			part = getPartMark(questionScores, section.startIdx() - 1, section.endIdx());
			sb.append(getRightNumber(section.startIdx() - 1, section.endIdx())).append("\t");
			sb.append(QRMathUtils.doubleFormat(part, 1)).append("\t");
			score += part;
		}
		sb.append(QRMathUtils.doubleFormat(score, 1));
		return sb.toString();
	}
}