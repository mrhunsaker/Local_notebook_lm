package com.notebooklm.util;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class TesseractNativeExtractor {
    private static final String TEMP_DIR_PREFIX = "tesseract-native-";
    private static Path extractedLibsPath;
    private static Path extractedTessDataPath;
    
    public static synchronized void extractNativeLibraries() throws IOException {
        if (extractedLibsPath != null) {
            return; // Already extracted
        }
        
        String platform = detectPlatform();
        Path tempDir = Files.createTempDirectory(TEMP_DIR_PREFIX);
        
        // Extract native binaries
        extractedLibsPath = tempDir.resolve("bin");
        Files.createDirectories(extractedLibsPath);
        extractPlatformBinaries(platform, extractedLibsPath);
        
        // Extract tessdata
        extractedTessDataPath = tempDir.resolve("tessdata");
        Files.createDirectories(extractedTessDataPath);
        extractTessData(extractedTessDataPath);
        
        // Set up cleanup on shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                deleteRecursively(tempDir);
            } catch (IOException e) {
                System.err.println("Failed to cleanup temp directory: " + e.getMessage());
            }
        }));
        
        System.out.println("✓ Tesseract native libraries extracted to: " + tempDir);
    }
    
    private static String detectPlatform() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        
        if (os.contains("win")) {
            return "windows-x64";
        } else if (os.contains("linux")) {
            return "linux-x64";
        } else if (os.contains("mac")) {
            return "macos-x64";
        } else {
            throw new UnsupportedOperationException("Unsupported platform: " + os + " " + arch);
        }
    }
    
    private static void extractPlatformBinaries(String platform, Path targetDir) throws IOException {
        String resourcePath = "/native/" + platform + "/";
        
        // List of files to extract for each platform
        List<String> filesToExtract = getFilesForPlatform(platform);
        
        for (String fileName : filesToExtract) {
            extractResource(resourcePath + fileName, targetDir.resolve(fileName));
        }
        
        // Make files executable on Unix systems
        if (!platform.startsWith("windows")) {
            makeExecutable(targetDir);
        }
    }
    
    private static List<String> getFilesForPlatform(String platform) {
        switch (platform) {
            case "windows-x64":
                return Arrays.asList("tesseract.exe", "libleptonica-5.dll", "libtesseract-5.dll");
            case "linux-x64":
                return Arrays.asList("tesseract", "libtesseract.so.5", "libleptonica.so.5");
            case "macos-x64":
                return Arrays.asList("tesseract", "libtesseract.5.dylib", "libleptonica.5.dylib");
            default:
                throw new UnsupportedOperationException("Unknown platform: " + platform);
        }
    }
    
    private static void extractTessData(Path targetDir) throws IOException {
        String[] languages = {"eng", "fra", "deu"}; // Add more as needed
        
        for (String lang : languages) {
            String resourcePath = "/tessdata/" + lang + ".traineddata";
            try (InputStream is = TesseractNativeExtractor.class.getResourceAsStream(resourcePath)) {
                if (is != null) {
                    Files.copy(is, targetDir.resolve(lang + ".traineddata"), 
                              StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("✓ Extracted tessdata for language: " + lang);
                }
            }
        }
    }
    
    private static void extractResource(String resourcePath, Path targetPath) throws IOException {
        try (InputStream is = TesseractNativeExtractor.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }
    
    private static void makeExecutable(Path directory) throws IOException {
        Files.walk(directory)
             .filter(Files::isRegularFile)
             .forEach(path -> path.toFile().setExecutable(true));
    }
    
    private static void deleteRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                 .sorted(Comparator.reverseOrder())
                 .map(Path::toFile)
                 .forEach(File::delete);
        }
    }
    
    public static String getTesseractExecutablePath() {
        if (extractedLibsPath == null) {
            throw new IllegalStateException("Native libraries not extracted yet");
        }
        
        String executableName = System.getProperty("os.name").toLowerCase().contains("win") 
                               ? "tesseract.exe" : "tesseract";
        return extractedLibsPath.resolve(executableName).toString();
    }
    
    public static String getTessDataPath() {
        if (extractedTessDataPath == null) {
            throw new IllegalStateException("Tessdata not extracted yet");
        }
        return extractedTessDataPath.toString();
    }
    
    public static String getNativeLibraryPath() {
        if (extractedLibsPath == null) {
            throw new IllegalStateException("Native libraries not extracted yet");
        }
        return extractedLibsPath.toString();
    }
}