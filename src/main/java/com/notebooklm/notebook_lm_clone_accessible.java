package com.notebooklm;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.List;
import java.util.concurrent.SwingWorker;
import java.util.Properties;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * GUI interface for the Self-Contained RAG System
 * Integrates with the existing IntegratedRAGSystem components
 * Enhanced with accessibility-focused font support
 */
public class NotebookLMCloneGUI extends JFrame {
    // Core RAG System Components
    private LLMServer llmServer;
    private SolrVectorDB vectorDB;
    private CouchDBClient couchDB;
    private EnhancedDocumentProcessor documentProcessor;
    private InternalLLMClient llmClient;
    
    // GUI Components
    private JButton selectFolderButton;
    private JButton indexDocumentsButton;
    private JTextField queryField;
    private JButton sendButton;
    private JList<String> fileList;
    private DefaultListModel<String> fileListModel;
    private JTextArea chatArea;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JButton clearChatButton;
    private JScrollPane chatScrollPane;
    
    // Font Configuration
    private Font primaryFont;
    private Font monoFont;
    private Font buttonFont;
    private Font labelFont;
    
    // Application State
    private String currentDocumentsPath;
    private Properties config;
    private boolean systemInitialized = false;
    
    public NotebookLMCloneGUI() {
        super("NotebookLM Clone - Self-Contained RAG System");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);
        
        try {
            loadFonts();
            loadConfiguration();
            initComponents();
            applyFontTheme();
            initializeSystem();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, 
                "Failed to initialize application: " + e.getMessage(), 
                "Initialization Error", 
                JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        
        setVisible(true);
    }
    
    /**
     * Load and configure fonts with Atkinson Hyperlegible as primary choice
     */
    private void loadFonts() {
        // Try to load fonts in order of preference
        Font[] candidateFonts = {
            loadCustomFont("AtkinsonHyperlegible-Regular.ttf", "Atkinson Hyperlegible"),
            loadCustomFont("APHont-Regular.ttf", "APHont"),
            loadCustomFont("JetBrainsMono-Regular.ttf", "JetBrains Mono NL"),
            new Font("JetBrains Mono", Font.PLAIN, 12),
            new Font("Consolas", Font.PLAIN, 12),
            new Font("Monaco", Font.PLAIN, 12),
            new Font("Lucida Console", Font.PLAIN, 12),
            new Font(Font.MONOSPACED, Font.PLAIN, 12)
        };
        
        // Find first available font
        Font selectedFont = null;
        for (Font font : candidateFonts) {
            if (font != null && isFontAvailable(font)) {
                selectedFont = font;
                System.out.println("Selected font: " + font.getFontName());
                break;
            }
        }
        
        if (selectedFont == null) {
            selectedFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            System.out.println("Fallback to default font");
        }
        
        // Create font variations
        primaryFont = selectedFont.deriveFont(Font.PLAIN, 12f);
        monoFont = selectedFont.deriveFont(Font.PLAIN, 12f);
        buttonFont = selectedFont.deriveFont(Font.PLAIN, 11f);
        labelFont = selectedFont.deriveFont(Font.PLAIN, 11f);
        
        // Set application-wide defaults
        setUIManagerFonts();
    }
    
    /**
     * Load custom font from resources or system
     * @param fileName Font file name to look for in resources
     * @param systemName System font name to try
     * @return Font object or null if not found
     */
    private Font loadCustomFont(String fileName, String systemName) {
        Font font = null;
        
        // Try loading from resources first
        try {
            InputStream fontStream = getClass().getResourceAsStream("/fonts/" + fileName);
            if (fontStream != null) {
                font = Font.createFont(Font.TRUETYPE_FONT, fontStream);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
                System.out.println("Loaded font from resources: " + fileName);
                return font.deriveFont(12f);
            }
        } catch (Exception e) {
            System.out.println("Could not load font from resources: " + fileName);
        }
        
        // Try system font by name
        try {
            font = new Font(systemName, Font.PLAIN, 12);
            if (isFontAvailable(font)) {
                System.out.println("Using system font: " + systemName);
                return font;
            }
        } catch (Exception e) {
            System.out.println("System font not available: " + systemName);
        }
        
        return null;
    }
    
    /**
     * Check if a font is actually available on the system
     */
    private boolean isFontAvailable(Font font) {
        if (font == null) return false;
        
        String[] availableFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String fontName : availableFonts) {
            if (fontName.equalsIgnoreCase(font.getFontName()) || 
                fontName.equalsIgnoreCase(font.getFamily())) {
                return true;
            }
        }
        
        // For custom loaded fonts, check if they render differently from default
        FontMetrics testMetrics = getFontMetrics(font);
        FontMetrics defaultMetrics = getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        
        return !font.getFamily().equals(Font.DIALOG) || 
               testMetrics.stringWidth("M") != defaultMetrics.stringWidth("M");
    }
    
    /**
     * Set UI Manager defaults to use our selected fonts
     */
    private void setUIManagerFonts() {
        // Set default fonts for various UI components
        UIManager.put("Button.font", buttonFont);
        UIManager.put("Label.font", labelFont);
        UIManager.put("TextField.font", primaryFont);
        UIManager.put("TextArea.font", monoFont);
        UIManager.put("List.font", primaryFont);
        UIManager.put("ComboBox.font", primaryFont);
        UIManager.put("Table.font", primaryFont);
        UIManager.put("Tree.font", primaryFont);
        UIManager.put("TabbedPane.font", labelFont);
        UIManager.put("MenuBar.font", labelFont);
        UIManager.put("Menu.font", labelFont);
        UIManager.put("MenuItem.font", labelFont);
        UIManager.put("PopupMenu.font", primaryFont);
        UIManager.put("ToolTip.font", labelFont);
        UIManager.put("TitledBorder.font", labelFont.deriveFont(Font.BOLD));
        UIManager.put("ProgressBar.font", labelFont);
    }
    
    /**
     * Apply font theme to all components after creation
     */
    private void applyFontTheme() {
        // Apply fonts to main components
        if (selectFolderButton != null) selectFolderButton.setFont(buttonFont);
        if (indexDocumentsButton != null) indexDocumentsButton.setFont(buttonFont);
        if (queryField != null) queryField.setFont(primaryFont);
        if (sendButton != null) sendButton.setFont(buttonFont);
        if (clearChatButton != null) clearChatButton.setFont(buttonFont);
        if (fileList != null) fileList.setFont(primaryFont);
        if (chatArea != null) chatArea.setFont(monoFont);
        if (statusLabel != null) statusLabel.setFont(labelFont);
        if (progressBar != null) progressBar.setFont(labelFont);
        
        // Apply fonts recursively to all components
        applyFontRecursively(this);
    }
    
    /**
     * Recursively apply fonts to all components in a container
     */
    private void applyFontRecursively(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JButton) {
                component.setFont(buttonFont);
            } else if (component instanceof JLabel) {
                component.setFont(labelFont);
            } else if (component instanceof JTextField || component instanceof JPasswordField) {
                component.setFont(primaryFont);
            } else if (component instanceof JTextArea) {
                component.setFont(monoFont);
            } else if (component instanceof JList || component instanceof JTree || component instanceof JTable) {
                component.setFont(primaryFont);
            } else if (component instanceof JComboBox) {
                component.setFont(primaryFont);
            }
            
            // Handle titled borders
            if (component instanceof JComponent) {
                JComponent jcomp = (JComponent) component;
                if (jcomp.getBorder() instanceof TitledBorder) {
                    TitledBorder titledBorder = (TitledBorder) jcomp.getBorder();
                    titledBorder.setTitleFont(labelFont.deriveFont(Font.BOLD));
                }
            }
            
            // Recurse into containers
            if (component instanceof Container) {
                applyFontRecursively((Container) component);
            }
        }
    }
    
    private void loadConfiguration() throws Exception {
        config = new Properties();
        try (FileInputStream fis = new FileInputStream("src/main/resources/application.properties")) {
            config.load(fis);
        } catch (IOException e) {
            // Create default configuration
            config.setProperty("rag.config.graniteModelPath", "./models/granite-8b-instruct-v3.2.Q4_K_M.gguf");
            config.setProperty("rag.config.documentsPath", "./source-documents");
            config.setProperty("rag.config.solrUrl", "http://localhost:8983/solr");
            config.setProperty("rag.config.couchDbUrl", "http://localhost:5984");
            config.setProperty("rag.config.couchDbUsername", "admin");
            config.setProperty("rag.config.couchDbPassword", "password");
            config.setProperty("rag.config.couchDbDatabase", "rag_conversations");
        }
        
        currentDocumentsPath = config.getProperty("rag.config.documentsPath");
    }
    
    private void initComponents() {
        setLayout(new BorderLayout());
        
        // Top Panel - Controls
        JPanel topPanel = createTopPanel();
        add(topPanel, BorderLayout.NORTH);
        
        // Center Panel - Main Content
        JSplitPane centerPanel = createCenterPanel();
        add(centerPanel, BorderLayout.CENTER);
        
        // Bottom Panel - Status
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
        
        // Setup keyboard shortcuts and accessibility
        setupAccessibility();
        setupKeyboardShortcuts();
    }
    
    private JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(new EmptyBorder(10, 10, 5, 10));
        
        // Folder Selection Panel
        JPanel folderPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        TitledBorder folderBorder = new TitledBorder("Document Management");
        folderBorder.setTitleFont(labelFont.deriveFont(Font.BOLD));
        folderPanel.setBorder(folderBorder);
        
        selectFolderButton = new JButton("Select Documents Folder");
        selectFolderButton.setMnemonic(KeyEvent.VK_F);
        selectFolderButton.setToolTipText("Select folder containing documents to process (Alt+F)");
        selectFolderButton.addActionListener(this::selectDocumentsFolder);
        selectFolderButton.setFont(buttonFont);
        
        indexDocumentsButton = new JButton("Index Documents");
        indexDocumentsButton.setMnemonic(KeyEvent.VK_I);
        indexDocumentsButton.setToolTipText("Process and index all documents in the selected folder (Alt+I)");
        indexDocumentsButton.addActionListener(this::indexDocuments);
        indexDocumentsButton.setEnabled(false);
        indexDocumentsButton.setFont(buttonFont);
        
        folderPanel.add(selectFolderButton);
        folderPanel.add(indexDocumentsButton);
        
        // Query Panel
        JPanel queryPanel = new JPanel(new BorderLayout());
        TitledBorder queryBorder = new TitledBorder("Ask a Question");
        queryBorder.setTitleFont(labelFont.deriveFont(Font.BOLD));
        queryPanel.setBorder(queryBorder);
        
        JLabel queryLabel = new JLabel("Query:");
        queryLabel.setFont(labelFont);
        queryField = new JTextField();
        queryField.setToolTipText("Enter your question here");
        queryField.setFont(primaryFont);
        queryLabel.setLabelFor(queryField);
        
        sendButton = new JButton("Send");
        sendButton.setMnemonic(KeyEvent.VK_S);
        sendButton.setToolTipText("Send query (Alt+S or Enter)");
        sendButton.addActionListener(this::sendQuery);
        sendButton.setEnabled(false);
        sendButton.setFont(buttonFont);
        
        clearChatButton = new JButton("Clear Chat");
        clearChatButton.setMnemonic(KeyEvent.VK_C);
        clearChatButton.setToolTipText("Clear chat history (Alt+C)");
        clearChatButton.addActionListener(this::clearChat);
        clearChatButton.setFont(buttonFont);
        
        queryField.addActionListener(this::sendQuery);
        
        JPanel queryInputPanel = new JPanel(new BorderLayout(5, 0));
        queryInputPanel.add(queryLabel, BorderLayout.WEST);
        queryInputPanel.add(queryField, BorderLayout.CENTER);
        queryInputPanel.add(sendButton, BorderLayout.EAST);
        
        JPanel queryButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        queryButtonPanel.add(clearChatButton);
        
        queryPanel.add(queryInputPanel, BorderLayout.CENTER);
        queryPanel.add(queryButtonPanel, BorderLayout.SOUTH);
        
        topPanel.add(folderPanel, BorderLayout.WEST);
        topPanel.add(queryPanel, BorderLayout.CENTER);
        
        return topPanel;
    }
    
    private JSplitPane createCenterPanel() {
        // File List Panel
        JPanel filePanel = new JPanel(new BorderLayout());
        TitledBorder fileBorder = new TitledBorder("Indexed Documents");
        fileBorder.setTitleFont(labelFont.deriveFont(Font.BOLD));
        filePanel.setBorder(fileBorder);
        
        fileListModel = new DefaultListModel<>();
        fileList = new JList<>(fileListModel);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.setToolTipText("List of processed documents");
        fileList.setFont(primaryFont);
        
        JScrollPane fileScrollPane = new JScrollPane(fileList);
        fileScrollPane.setPreferredSize(new Dimension(250, 0));
        filePanel.add(fileScrollPane, BorderLayout.CENTER);
        
        // Chat Panel
        JPanel chatPanel = new JPanel(new BorderLayout());
        TitledBorder chatBorder = new TitledBorder("Conversation");
        chatBorder.setTitleFont(labelFont.deriveFont(Font.BOLD));
        chatPanel.setBorder(chatBorder);
        
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(monoFont);
        chatArea.setToolTipText("Conversation history with the RAG system");
        
        chatScrollPane = new JScrollPane(chatArea);
        chatScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        chatPanel.add(chatScrollPane, BorderLayout.CENTER);
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, filePanel, chatPanel);
        splitPane.setDividerLocation(250);
        splitPane.setResizeWeight(0.0);
        
        return splitPane;
    }
    
    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(new EmptyBorder(5, 10, 10, 10));
        
        statusLabel = new JLabel("Ready - Please select documents folder and index documents");
        statusLabel.setToolTipText("Current system status");
        statusLabel.setFont(labelFont);
        
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        progressBar.setFont(labelFont);
        
        bottomPanel.add(statusLabel, BorderLayout.CENTER);
        bottomPanel.add(progressBar, BorderLayout.SOUTH);
        
        return bottomPanel;
    }
    
    private void setupAccessibility() {
        // Set accessible names and descriptions
        selectFolderButton.getAccessibleContext().setAccessibleName("Select Documents Folder");
        indexDocumentsButton.getAccessibleContext().setAccessibleName("Index Documents");
        queryField.getAccessibleContext().setAccessibleName("Query input field");
        sendButton.getAccessibleContext().setAccessibleName("Send query");
        fileList.getAccessibleContext().setAccessibleName("Indexed documents list");
        chatArea.getAccessibleContext().setAccessibleName("Conversation history");
        clearChatButton.getAccessibleContext().setAccessibleName("Clear chat history");
        
        // Setup focus traversal order
        setFocusTraversalPolicy(new CustomFocusTraversalPolicy(List.of(
            selectFolderButton, indexDocumentsButton, queryField, sendButton, clearChatButton, fileList
        )));
    }
    
    private void setupKeyboardShortcuts() {
        // Global shortcuts
        JRootPane rootPane = getRootPane();
        
        // Ctrl+Q to quit
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK), "quit");
        rootPane.getActionMap().put("quit", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                shutdown();
            }
        });
        
        // F5 to refresh file list
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "refresh");
        rootPane.getActionMap().put("refresh", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refreshFileList();
            }
        });
        
        // Ctrl+Plus/Minus for font size adjustment
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, InputEvent.CTRL_DOWN_MASK), "increaseFontSize");
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK), "increaseFontSize");
        rootPane.getActionMap().put("increaseFontSize", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                adjustFontSize(1.1f);
            }
        });
        
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK), "decreaseFontSize");
        rootPane.getActionMap().put("decreaseFontSize", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                adjustFontSize(0.9f);
            }
        });
    }
    
    /**
     * Adjust font sizes for better accessibility
     */
    private void adjustFontSize(float factor) {
        float newSize = Math.max(8f, Math.min(24f, primaryFont.getSize2D() * factor));
        
        primaryFont = primaryFont.deriveFont(newSize);
        monoFont = monoFont.deriveFont(newSize);
        buttonFont = buttonFont.deriveFont(newSize * 0.9f);
        labelFont = labelFont.deriveFont(newSize * 0.9f);
        
        applyFontTheme();
        repaint();
        
        statusLabel.setText("Font size adjusted to " + (int)newSize + "pt");
    }
    
    private void initializeSystem() {
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                publish("Initializing RAG system components...");
                
                // Initialize LLM Server
                publish("Starting LLM Server...");
                String modelPath = config.getProperty("rag.config.graniteModelPath");
                llmServer = new LLMServer(modelPath);
                
                // Wait a moment for server to fully start
                Thread.sleep(2000);
                
                // Initialize other components
                publish("Connecting to vector database...");
                vectorDB = new SolrVectorDB(config.getProperty("rag.config.solrUrl"));
                
                publish("Connecting to conversation database...");
                couchDB = new CouchDBClient(
                    config.getProperty("rag.config.couchDbUrl"),
                    config.getProperty("rag.config.couchDbDatabase"),
                    config.getProperty("rag.config.couchDbUsername"),
                    config.getProperty("rag.config.couchDbPassword")
                );
                
                publish("Initializing document processor...");
                documentProcessor = new EnhancedDocumentProcessor();
                
                publish("Initializing LLM client...");
                llmClient = new InternalLLMClient();
                
                publish("System initialization complete!");
                return null;
            }
            
            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    statusLabel.setText(message);
                }
            }
            
            @Override
            protected void done() {
                try {
                    get(); // Check for exceptions
                    systemInitialized = true;
                    sendButton.setEnabled(true);
                    statusLabel.setText("System ready - Select documents folder to begin");
                    refreshFileList();
                } catch (Exception e) {
                    statusLabel.setText("Initialization failed: " + e.getMessage());
                    JOptionPane.showMessageDialog(NotebookLMCloneGUI.this,
                        "Failed to initialize system: " + e.getMessage(),
                        "Initialization Error",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        
        worker.execute();
    }
    
    private void selectDocumentsFolder(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select Documents Folder");
        
        // Apply font to file chooser
        applyFontRecursively(chooser);
        
        if (currentDocumentsPath != null) {
            chooser.setCurrentDirectory(new File(currentDocumentsPath));
        }
        
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            currentDocumentsPath = chooser.getSelectedFile().getAbsolutePath();
            statusLabel.setText("Selected folder: " + currentDocumentsPath);
            indexDocumentsButton.setEnabled(systemInitialized);
            refreshFileList();
        }
    }
    
    private void indexDocuments(ActionEvent e) {
        if (!systemInitialized || currentDocumentsPath == null) {
            return;
        }
        
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                publish("Starting document indexing...");
                
                File documentsFolder = new File(currentDocumentsPath);
                File[] files = documentsFolder.listFiles();
                
                if (files == null || files.length == 0) {
                    publish("No files found in selected folder");
                    return null;
                }
                
                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(true);
                    progressBar.setMaximum(files.length);
                    progressBar.setValue(0);
                });
                
                int processed = 0;
                for (File file : files) {
                    if (file.isFile()) {
                        try {
                            publish("Processing: " + file.getName());
                            
                            // Process document using the existing document processor
                            List<DocumentChunk> chunks = documentProcessor.processFile(file.getAbsolutePath());
                            
                            // Store chunks in vector database
                            for (DocumentChunk chunk : chunks) {
                                vectorDB.storeDocument(chunk);
                            }
                            
                            processed++;
                            final int currentProgress = processed;
                            SwingUtilities.invokeLater(() -> progressBar.setValue(currentProgress));
                            
                        } catch (Exception ex) {
                            publish("Error processing " + file.getName() + ": " + ex.getMessage());
                        }
                    }
                }
                
                publish("Indexing complete! Processed " + processed + " documents.");
                return null;
            }
            
            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    statusLabel.setText(message);
                }
            }
            
            @Override
            protected void done() {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    refreshFileList();
                    statusLabel.setText("Ready - Documents indexed successfully");
                });
            }
        };
        
        worker.execute();
    }
    
    private void sendQuery(ActionEvent e) {
        String query = queryField.getText().trim();
        if (query.isEmpty() || !systemInitialized) {
            return;
        }
        
        // Add user query to chat
        appendToChat("You: " + query + "\n\n");
        queryField.setText("");
        queryField.setEnabled(false);
        sendButton.setEnabled(false);
        
        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                statusLabel.setText("Searching documents and generating response...");
                
                // Search for relevant documents
                List<DocumentChunk> relevantChunks = vectorDB.searchSimilarDocuments(query, 5);
                
                // Build context from relevant chunks
                StringBuilder contextBuilder = new StringBuilder();
                contextBuilder.append("Context from relevant documents:\n\n");
                
                for (DocumentChunk chunk : relevantChunks) {
                    contextBuilder.append("From: ").append(chunk.getFilePath()).append("\n");
                    contextBuilder.append(chunk.getContent()).append("\n\n");
                }
                
                // Create full prompt
                String fullPrompt = contextBuilder.toString() + 
                    "\nUser Question: " + query + 
                    "\n\nPlease provide a helpful answer based on the context above:";
                
                // Generate response using LLM
                String response = llmClient.generateTextResponse(fullPrompt);
                
                // Store conversation in CouchDB
                try {
                    couchDB.storeResponse(query, response);
                } catch (Exception ex) {
                    System.err.println("Failed to store conversation: " + ex.getMessage());
                }
                
                return response;
            }
            
            @Override
            protected void done() {
                try {
                    String response = get();
                    appendToChat("Assistant: " + response + "\n\n");
                    appendToChat("---\n\n");
                    statusLabel.setText("Ready");
                } catch (Exception ex) {
                    appendToChat("Error: " + ex.getMessage() + "\n\n");
                    statusLabel.setText("Error generating response");
                } finally {
                    queryField.setEnabled(true);
                    sendButton.setEnabled(true);
                    queryField.requestFocus();
                }
            }
        };
        
        worker.execute();
    }
    
    private void clearChat(ActionEvent e) {
        chatArea.setText("");
        statusLabel.setText("Chat cleared");
    }
    
    private void appendToChat(String text) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(text);
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
            
            // Auto-scroll to bottom
            JScrollBar vertical = chatScrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }
    
    private void refreshFileList() {
        SwingUtilities.invokeLater(() -> {
            fileListModel.clear();
            
            if (currentDocumentsPath != null) {
                File folder = new File(currentDocumentsPath);
                File[] files = folder.listFiles();
                
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile()) {
                            fileListModel.addElement(file.getName());
                        }
                    }
                }
            }
        });
    }
    
    private void shutdown() {
        int result = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to exit?",
            "Confirm Exit",
            JOptionPane.YES_NO_OPTION);
        
        if (result == JOptionPane.YES_OPTION) {
            // Cleanup resources
            if (llmClient != null) {
                try {
                    llmClient.close();
                } catch (Exception e) {
                    System.err.println("Error closing LLM client: " + e.getMessage());
                }
            }
            
            if (llmServer != null) {
                try {
                    llmServer.close();
                } catch (Exception e) {
                    System.err.println("Error closing LLM server: " + e.getMessage());
                }
            }
            
            System.exit(0);
        }
    }
    
    @Override
    protected void processWindowEvent(WindowEvent e) {
        if (e.getID() == WindowEvent.WINDOW_CLOSING) {
            shutdown();
        } else {
            super.processWindowEvent(e);
        }
    }
    
    // Custom Focus Traversal Policy for accessibility
    static class CustomFocusTraversalPolicy extends FocusTraversalPolicy {
        private final List<Component> order;

        public CustomFocusTraversalPolicy(List<Component> order) {
            this.order = order;
        }

        public Component getComponentAfter(Container a, Component c) {
            int i = (order.indexOf(c) + 1) % order.size();
            return order.get(i);
        }

        public Component getComponentBefore(Container a, Component c) {
            int i = order.indexOf(c) - 1;
            if (i < 0) i = order.size() - 1;
            return order.get(i);
        }

        public Component getFirstComponent(Container a) { 
            return order.isEmpty() ? null : order.get(0); 
        }
        
        public Component getLastComponent(Container a) { 
            return order.isEmpty() ? null : order.get(order.size() - 1); 
        }
        
        public Component getDefaultComponent(Container a) { 
            return order.isEmpty() ? null : order.get(0); 
        }
    }
    
    public static void main(String[] args) {
        // Set system look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeel());
        } catch (Exception e) {
            System.err.println("Could not set system look and feel: " + e.getMessage());
        }
        
        // Enable font anti-aliasing for better readability
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        
        SwingUtilities.invokeLater(() -> new NotebookLMCloneGUI());
    }
