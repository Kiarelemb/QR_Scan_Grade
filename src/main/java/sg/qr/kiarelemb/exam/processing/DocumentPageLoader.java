package sg.qr.kiarelemb.exam.processing;

import method.qr.kiarelemb.utils.QRLoggerUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

public final class DocumentPageLoader {
	private static final Logger logger = QRLoggerUtils.getLogger(DocumentPageLoader.class);
	private static final List<String> IMAGE_EXTENSIONS = List.of("jpg", "jpeg", "png", "bmp", "gif", "tif", "tiff");
	private static final int PDF_DPI = 300;
	private static final File TEMP_DIR = new File("tmp/pdf-images");

	private DocumentPageLoader() {
	}

	public static File firstImage(File source) throws IOException {
		List<File> images = documentImages(source);
		if (images.isEmpty()) {
			throw new IOException("文档没有可读取的页面：" + source.getAbsolutePath());
		}
		return images.get(0);
	}

	public static List<File> documentImages(File source) throws IOException {
		return documentImages(source, null);
	}

	public static List<File> documentImages(File source, BiConsumer<Integer, Integer> progress) throws IOException {
		if (isPdfFile(source)) {
			return renderPdf(source, progress);
		}
		return List.of(source);
	}

	public static List<File> sortedAnswerImages(File directory) throws IOException {
		return sortedAnswerImages(directory, null);
	}

	public static List<File> sortedAnswerImages(File directory, BiConsumer<Integer, Integer> progress) throws IOException {
		File[] imageFiles = directory.listFiles(file -> file.isFile() && isImageFile(file));
		if (imageFiles != null && imageFiles.length > 0) {
			List<File> sortedImages = new ArrayList<>(Arrays.asList(imageFiles));
			sortedImages.sort(DocumentPageLoader::compareByName);
			return sortedImages;
		}

		File singlePdf = singlePdfFile(directory);
		if (singlePdf != null) {
			return renderPdfToDirectory(singlePdf, progress);
		}
		return List.of();
	}

	public static int pdfPageCount(File pdfFile) throws IOException {
		if (!isPdfFile(pdfFile)) {
			return 1;
		}
		try (PDDocument document = Loader.loadPDF(pdfFile)) {
			return Math.max(1, document.getNumberOfPages());
		}
	}

	public static boolean isImageFile(File file) {
		String extension = extension(file);
		return IMAGE_EXTENSIONS.contains(extension);
	}

	public static boolean isPdfFile(File file) {
		return "pdf".equals(extension(file));
	}

	public static File singlePdfFile(File directory) {
		File[] pdfFiles = directory.listFiles(file -> file.isFile() && isPdfFile(file));
		if (pdfFiles == null || pdfFiles.length != 1) {
			return null;
		}
		return pdfFiles[0];
	}

	public static List<File> convertedPdfImages(File pdfFile) {
		File directory = pdfFile.getParentFile();
		if (directory == null || !directory.isDirectory()) {
			return List.of();
		}
		String prefix = convertedImagePrefix(pdfFile);
		File[] files = directory.listFiles(file -> file.isFile() && file.getName().startsWith(prefix) && isImageFile(file));
		if (files == null || files.length == 0) {
			return List.of();
		}
		List<File> images = new ArrayList<>(Arrays.asList(files));
		images.sort(DocumentPageLoader::compareByName);
		return images;
	}

	public static List<File> renderPdfToDirectory(File pdfFile) throws IOException {
		return renderPdfToDirectory(pdfFile, null);
	}

	public static List<File> renderPdfToDirectory(File pdfFile, BiConsumer<Integer, Integer> progress) throws IOException {
		List<File> existing = convertedPdfImages(pdfFile);
		int pageCount = pdfPageCount(pdfFile);
		if (existing.size() == pageCount) {
			if (progress != null) progress.accept(pageCount, pageCount);
			return existing;
		}
		List<File> outputFiles = new ArrayList<>();
		try (PDDocument document = Loader.loadPDF(pdfFile)) {
			PDFRenderer renderer = new PDFRenderer(document);
			String prefix = convertedImagePrefix(pdfFile);
			File directory = pdfFile.getParentFile();
			int totalPages = document.getNumberOfPages();
			for (int page = 0; page < totalPages; page++) {
				if (Thread.currentThread().isInterrupted()) {
					break;
				}
				BufferedImage image = renderer.renderImageWithDPI(page, PDF_DPI, ImageType.RGB);
				File output = new File(directory, prefix + String.format(Locale.ROOT, "%03d", page + 1) + ".png");
				ImageIO.write(image, "png", output);
				outputFiles.add(output);
				if (progress != null) progress.accept(page + 1, totalPages);
			}
		}
		return outputFiles;
	}

	public static List<File> renderedPdfImagesForDirectory(File directory) {
		File[] pdfFiles = directory.listFiles(file -> file.isFile() && isPdfFile(file));
		if (pdfFiles == null || pdfFiles.length == 0) {
			return List.of();
		}
		List<File> renderedImages = new ArrayList<>();
		for (File pdfFile : pdfFiles) {
			renderedImages.addAll(convertedPdfImages(pdfFile));
		}
		renderedImages.sort(DocumentPageLoader::compareByName);
		return renderedImages;
	}

	private static List<File> renderPdf(File pdfFile) throws IOException {
		return renderPdf(pdfFile, null);
	}

	private static List<File> renderPdf(File pdfFile, BiConsumer<Integer, Integer> progress) throws IOException {
		TEMP_DIR.mkdirs();
		List<File> outputFiles = new ArrayList<>();
		try (PDDocument document = Loader.loadPDF(pdfFile)) {
			PDFRenderer renderer = new PDFRenderer(document);
			String baseName = baseName(pdfFile);
			int totalPages = document.getNumberOfPages();
			for (int page = 0; page < totalPages; page++) {
				if (Thread.currentThread().isInterrupted()) {
					break;
				}
				BufferedImage image = renderer.renderImageWithDPI(page, PDF_DPI, ImageType.RGB);
				File output = new File(TEMP_DIR, baseName + "_p" + String.format(Locale.ROOT, "%03d", page + 1) + ".png");
				ImageIO.write(image, "png", output);
				outputFiles.add(output);
				if (progress != null) progress.accept(page + 1, totalPages);
			}
		}
		logger.fine("PDF渲染完成: " + outputFiles.size() + " 页");
		return outputFiles;
	}

	private static int compareByName(File first, File second) {
		int ignoreCase = String.CASE_INSENSITIVE_ORDER.compare(first.getName(), second.getName());
		return ignoreCase != 0 ? ignoreCase : first.getName().compareTo(second.getName());
	}

	private static String extension(File file) {
		String name = file == null ? "" : file.getName();
		int dot = name.lastIndexOf('.');
		if (dot < 0 || dot == name.length() - 1) {
			return "";
		}
		return name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private static String baseName(File file) {
		String name = file.getName();
		int dot = name.lastIndexOf('.');
		if (dot > 0) {
			name = name.substring(0, dot);
		}
		return name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
	}

	private static String convertedImagePrefix(File pdfFile) {
		return baseName(pdfFile) + "_converted_p";
	}
}