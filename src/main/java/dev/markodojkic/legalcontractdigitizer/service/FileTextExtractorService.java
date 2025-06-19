package dev.markodojkic.legalcontractdigitizer.service;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class FileTextExtractorService {

	private final Tika tika = new Tika();

	/**
	 * Extracts text from various file formats such as PDF or DOCX
	 */
	public String extractText(MultipartFile file) throws IOException, TikaException {
		return tika.parseToString(file.getInputStream());
	}
}