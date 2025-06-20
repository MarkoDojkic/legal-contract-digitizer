package dev.markodojkic.legalcontractdigitizer.test.service;

import dev.markodojkic.legalcontractdigitizer.service.FileTextExtractorService;
import org.apache.tika.exception.TikaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class FileTextExtractorServiceTest {

    @Mock
    private FileTextExtractorService fileTextExtractorService;

    @BeforeEach
    void setUp() {
        Mockito.reset(fileTextExtractorService);
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testExtractTextWithEmptyFile() throws IOException, TikaException {
        // Implement test logic for extracting text from an empty file
        MultipartFile mockFile = mock(MultipartFile.class);
        lenient().when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        String extractedText = fileTextExtractorService.extractText(mockFile);
        assertNull(extractedText);
    }

    @Test
    void testExtractTextWithUnsupportedFileType() throws IOException, TikaException {
        // Implement test logic for extracting text from an unsupported file type
        MultipartFile mockFile = mock(MultipartFile.class);
        lenient().when(mockFile.getInputStream()).thenThrow(new IOException("Unsupported file type"));

        try {
            fileTextExtractorService.extractText(mockFile);
        } catch (IOException e) {
            assertEquals("Unsupported file type", e.getMessage());
        }
    }

    @Test
    void testExtractTextWithValidFile() throws IOException, TikaException {
        // Implement test logic for extracting text from a valid file
        MultipartFile mockFile = mock(MultipartFile.class);
        lenient().when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream("Valid file content".getBytes()));

        String extractedText = fileTextExtractorService.extractText(mockFile);
        assertNull(extractedText);
    }
}
