package com.notebooklm;

import com.notebooklm.util.TesseractNativeExtractor;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

public class EnhancedDocumentProcessor {
    private final TikaConfig tikaConfig;
    private final Tesseract tesseract;
    
    public EnhancedDocumentProcessor() {
        try {
            // Extract native libraries first
            TesseractNativeExtractor.extractNativeLibraries();
            
            // Initialize Tesseract with extracted binaries
            this.tesseract = initializeTesseract();
            
            // Configure Tika
            this.tikaConfig = new TikaConfig(getClass().getResourceAsStream("/tika-config.xml"));
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize document processor", e);
        }
        System.out.println("✓ Enhanced Document Processor with embedded Tesseract ready.");
    }
    
    private Tesseract initializeTesseract() {
        Tesseract tesseract = new Tesseract();
        
        // Set paths to extracted binaries and data
        tesseract.setDatapath(TesseractNativeExtractor.getTessDataPath());
        tesseract.setLanguage("eng");
        tesseract.setOcrEngineMode(1);
        tesseract.setPageSegMode(1);
        
        // Set the tesseract executable path
        tesseract.setTessVariable("user_defined_dpi", "300");
        
        return tesseract;
    }
    
    public List<DocumentChunk> processFile(String filePath) throws Exception {
        File file = new File(filePath);
        
        // Check if file is an image that needs OCR
        if (isImageFile(file)) {
            return processImageWithOCR(file);
        } else {
            return processWithTika(file);
        }
    }
    
    private boolean isImageFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || 
               name.endsWith(".png") || name.endsWith(".tiff") || 
               name.endsWith(".tif") || name.endsWith(".bmp") ||
               name.endsWith(".gif");
    }
    
    private List<DocumentChunk> processImageWithOCR(File file) throws Exception {
        System.out.println("Processing image with OCR: " + file.getName());
        
        try {
            String content = tesseract.doOCR(file);
            if (content == null || content.trim().isEmpty()) {
                System.out.println("No text found in image: " + file.getName());
                content = "No text extracted from image";
            }
            
            ProcessedDocument document = new ProcessedDocument(
                file.getAbsolutePath(), 
                file.getName(), 
                content, 
                new HashMap<>(),  // Empty metadata for direct OCR
                "tesseract_ocr"
            );
            
            return chunkDocument(document);
            
        } catch (TesseractException e) {
            System.err.println("OCR failed for " + file.getName() + ": " + e.getMessage());
            throw new Exception("OCR processing failed", e);
        }
    }
    
    private List<DocumentChunk> processWithTika(File file) throws Exception {
        Metadata metadata = new Metadata();
        
        // Use a BodyContentHandler that doesn't limit text size
        BodyContentHandler handler = new BodyContentHandler(-1);
        
        AutoDetectParser parser = new AutoDetectParser(this.tikaConfig);
        ParseContext context = new ParseContext();
        
        try (InputStream stream = new FileInputStream(file)) {
            parser.parse(stream, handler, metadata, context);
            // The handler now contains text extracted by Tika, including OCR from Tesseract
            String content = handler.toString();
            
            ProcessedDocument document = new ProcessedDocument(
                file.getAbsolutePath(), 
                file.getName(), 
                content, 
                parseMetadata(metadata), 
                "tika_tesseract"
            );
            return chunkDocument(document);
        }
    }
    
    private Map<String, String> parseMetadata(Metadata metadata) {
        Map<String, String> result = new HashMap<>();
        for (String name : metadata.names()) {
            result.put(name, metadata.get(name));
        }
        return result;
    }
    
    private List<DocumentChunk> chunkDocument(ProcessedDocument document) {
        List<DocumentChunk> chunks = new ArrayList<>();
        String content = document.getContent();
        
        if (content == null || content.trim().isEmpty()) {
            System.out.println("Warning: Empty content for document: " + document.getFileName());
            return chunks;
        }
        
        // Simple chunking strategy - split by paragraphs or sentences
        String[] paragraphs = content.split("\n\n");
        
        for (int i = 0; i < paragraphs.length; i++) {
            String paragraph = paragraphs[i].trim();
            if (!paragraph.isEmpty() && paragraph.length() > 50) { // Skip very short chunks
                DocumentChunk chunk = new DocumentChunk(
                    document.getFilePath(),
                    document.getFileName(),
                    paragraph,
                    i
                );
                chunks.add(chunk);
            }
        }
        
        // If no good chunks were found, create one chunk with all content
        if (chunks.isEmpty() && content.length() > 10) {
            chunks.add(new DocumentChunk(
                document.getFilePath(),
                document.getFileName(),
                content,
                0
            ));
        }
        
        System.out.println("Created " + chunks.size() + " chunks for: " + document.getFileName());
        return chunks;
    }
    
    // Helper classes
    public static class ProcessedDocument {
        private final String filePath;
        private final String fileName;
        private final String content;
        private final Map<String, String> metadata;
        private final String processingMethod;
        
        public ProcessedDocument(String filePath, String fileName, String content, 
                               Map<String, String> metadata, String processingMethod) {
            this.filePath = filePath;
            this.fileName = fileName;
            this.content = content;
            this.metadata = metadata;
            this.processingMethod = processingMethod;
        }
        
        // Getters
        public String getFilePath() { return filePath; }
        public String getFileName() { return fileName; }
        public String getContent() { return content; }
        public Map<String, String> getMetadata() { return metadata; }
        public String getProcessingMethod() { return processingMethod; }
    }
    
    public static class DocumentChunk {
        private final String filePath;
        private final String fileName;
        private final String content;
        private final int chunkIndex;
        
        public DocumentChunk(String filePath, String fileName, String content, int chunkIndex) {
            this.filePath = filePath;
            this.fileName = fileName;
            this.content = content;
            this.chunkIndex = chunkIndex;
        }
        
        // Getters
        public String getFilePath() { return filePath; }
        public String getFileName() { return fileName; }
        public String getContent() { return content; }
        public int getChunkIndex() { return chunkIndex; }
        
        public String getTitle() {
            return fileName + " (chunk " + chunkIndex + ")";
        }
    }
}