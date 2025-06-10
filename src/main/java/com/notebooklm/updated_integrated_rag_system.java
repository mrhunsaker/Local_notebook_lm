package com.notebooklm;

import com.notebooklm.util.TesseractNativeExtractor;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Scanner;

public class IntegratedRAGSystem {
    private final Properties config;
    private final SolrVectorDB vectorDB;
    private final CouchDBClient couchDBClient;
    private final EnhancedDocumentProcessor documentProcessor;
    private final InternalLLMClient llmClient;
    private final LLMServer llmServer;
    
    public IntegratedRAGSystem() throws Exception {
        System.out.println("🚀 Starting Self-Contained RAG System...");
        
        // Extract native libraries first
        TesseractNativeExtractor.extractNativeLibraries();
        
        // Load configuration
        this.config = loadConfiguration();
        
        // Initialize components
        this.vectorDB = new SolrVectorDB(config.getProperty("rag.config.solrUrl"));
        this.couchDBClient = new CouchDBClient(
            config.getProperty("rag.config.couchDbUrl"), 
            "conversations", 
            "admin", 
            "password"
        );
        this.documentProcessor = new EnhancedDocumentProcessor();
        
        // Start embedded LLM server
        String modelPath = config.getProperty("rag.config.graniteModelPath");
        this.llmServer = new LLMServer(modelPath);
        
        // Initialize LLM client
        this.llmClient = new InternalLLMClient();
        
        System.out.println("✅ Self-Contained RAG System initialized successfully!");
    }
    
    private Properties loadConfiguration() throws IOException {
        Properties props = new Properties();
        try (InputStream is = getClass().getResourceAsStream("/application.properties")) {
            if (is == null) {
                throw new IOException("application.properties not found in classpath");
            }
            props.load(is);
        }
        return props;
    }
    
    public void run() {
        Scanner scanner = new Scanner(System.in);
        String conversationId = java.util.UUID.randomUUID().toString();
        
        while (true) {
            System.out.println("\n=== Self-Contained RAG Notebook ===");
            System.out.println("1. Index documents");
            System.out.println("2. Ask a question");
            System.out.println("3. Exit");
            System.out.print("Choose an option: ");
            
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1":
                    indexDocuments();
                    break;
                case "2":
                    askQuestion(scanner, conversationId);
                    break;
                case "3":
                    System.out.println("Goodbye!");
                    return;
                default:
                    System.out.println("Invalid option. Please try again.");
            }
        }
    }
    
    private void indexDocuments() {
        try {
            String documentsPath = config.getProperty("rag.config.documentsPath");
            System.out.println("📁 Indexing documents from: " + documentsPath);
            
            // Implementation would process all files in the directory
            // This is a simplified version
            java.io.File directory = new java.io.File(documentsPath);
            if (!directory.exists() || !directory.isDirectory()) {
                System.err.println("Documents directory not found: " + documentsPath);
                return;
            }
            
            java.io.File[] files = directory.listFiles();
            if (files == null || files.length == 0) {
                System.out.println("No files found in documents directory");
                return;
            }
            
            int processedCount = 0;
            for (java.io.File file : files) {
                if (file.isFile()) {
                    try {
                        System.out.println("Processing: " + file.getName());
                        var chunks = documentProcessor.processFile(file.getAbsolutePath());
                        
                        for (var chunk : chunks) {
                            // Generate embedding and store in Solr
                            // This would use the GraniteModelWrapper for embeddings
                            vectorDB.indexDocument(chunk);
                        }
                        processedCount++;
                    } catch (Exception e) {
                        System.err.println("Failed to process " + file.getName() + ": " + e.getMessage());
                    }
                }
            }
            
            System.out.println("✅ Indexed " + processedCount + " documents successfully!");
            
        } catch (Exception e) {
            System.err.println("Failed to index documents: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void askQuestion(Scanner scanner, String conversationId) {
        try {
            System.out.print("❓ Enter your question: ");
            String question = scanner.nextLine().trim();
            
            if (question.isEmpty()) {
                System.out.println("Please enter a valid question.");
                return;
            }
            
            System.out.println("🔍 Searching for relevant information...");
            
            // Search for relevant chunks
            var relevantChunks = vectorDB.searchSimilarDocuments(question, 5);
            
            // Build context from relevant chunks
            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append("Based on the following information:\n\n");
            
            for (var chunk : relevantChunks) {
                contextBuilder.append("From ").append(chunk.getTitle()).append(":\n");
                contextBuilder.append(chunk.getContent()).append("\n\n");
            }
            
            contextBuilder.append("Question: ").append(question).append("\n\n");
            contextBuilder.append("Please provide a comprehensive answer based on the provided information:");
            
            String fullPrompt = contextBuilder.toString();
            
            System.out.println("🤖 Generating response...");
            String response = llmClient.generateTextResponse(fullPrompt);
            
            System.out.println("\n📝 Response:");
            System.out.println(response);
            
            // Store conversation
            couchDBClient.storeResponse(conversationId, question, response, relevantChunks);
            
            System.out.println("\n📚 Sources:");
            for (var chunk : relevantChunks) {
                System.out.println("- " + chunk.getFileName());
            }
            
        } catch (Exception e) {
            System.err.println("Failed to process question: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void shutdown() {
        try {
            if (llmClient != null) {
                llmClient.close();
            }
            if (llmServer != null) {
                llmServer.close();
            }
            System.out.println("✅ System shutdown complete.");
        } catch (Exception e) {
            System.err.println("Error during shutdown: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        try {
            IntegratedRAGSystem system = new IntegratedRAGSystem();
            
            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(system::shutdown));
            
            system.run();
            
        } catch (Exception e) {
            System.err.println("Failed to start application: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}