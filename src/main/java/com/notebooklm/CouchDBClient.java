package com.notebooklm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpHeaders;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Complete CouchDB client for managing conversation history and responses.
 * Handles database operations, authentication, and conversation management.
 */
public class CouchDBClient implements AutoCloseable {
    private final String couchUrl;
    private final String database;
    private final String username;
    private final String password;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CouchDBClient(String couchUrl, String database, String username, String password) {
        this.couchUrl = couchUrl.endsWith("/") ? couchUrl.substring(0, couchUrl.length() - 1) : couchUrl;
        this.database = database;
        this.username = username;
        this.password = password;
        this.objectMapper = new ObjectMapper();
        
        // Set up HTTP client with authentication
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
            AuthScope.ANY,
            new UsernamePasswordCredentials(username, password)
        );
        
        this.httpClient = HttpClients.custom()
            .setDefaultCredentialsProvider(credentialsProvider)
            .build();
        
        try {
            initializeDatabase();
            System.out.println("✓ CouchDB client initialized successfully");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize CouchDB client", e);
        }
    }

    /**
     * Initializes the database, creating it if it doesn't exist.
     */
    private void initializeDatabase() throws Exception {
        if (!databaseExists()) {
            createDatabase();
        }
        createDesignDocuments();
    }

    /**
     * Checks if the database exists.
     */
    private boolean databaseExists() throws Exception {
        HttpHead request = new HttpHead(couchUrl + "/" + database);
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            return response.getStatusLine().getStatusCode() == 200;
        }
    }

    /**
     * Creates the database.
     */
    private void createDatabase() throws Exception {
        HttpPut request = new HttpPut(couchUrl + "/" + database);
        String responseBody = executeRequest(request);
        System.out.println("✓ Created CouchDB database: " + database);
    }

    /**
     * Creates design documents for efficient querying.
     */
    private void createDesignDocuments() throws Exception {
        // Create design document for conversation queries
        ObjectNode designDoc = objectMapper.createObjectNode();
        designDoc.put("_id", "_design/conversations");
        
        ObjectNode views = objectMapper.createObjectNode();
        ObjectNode byTimestamp = objectMapper.createObjectNode();
        byTimestamp.put("map", "function(doc) { if(doc.type === 'conversation' && doc.timestamp) { emit(doc.timestamp, doc); } }");
        views.set("by_timestamp", byTimestamp);
        
        ObjectNode bySessionId = objectMapper.createObjectNode();
        bySessionId.put("map", "function(doc) { if(doc.type === 'conversation' && doc.sessionId) { emit(doc.sessionId, doc); } }");
        views.set("by_session_id", bySessionId);
        
        designDoc.set("views", views);
        
        try {
            storeDocument("_design/conversations", designDoc);
        } catch (Exception e) {
            // Design document might already exist, which is fine
            if (!e.getMessage().contains("409")) {
                throw e;
            }
        }
    }

    /**
     * Stores a conversation turn (question + response + sources).
     */
    public String storeConversation(String sessionId, String question, String response, 
                                  List<String> sources, String model) throws Exception {
        ObjectNode conversationDoc = objectMapper.createObjectNode();
        conversationDoc.put("_id", UUID.randomUUID().toString());
        conversationDoc.put("type", "conversation");
        conversationDoc.put("sessionId", sessionId);
        conversationDoc.put("question", question);
        conversationDoc.put("response", response);
        conversationDoc.put("model", model);
        conversationDoc.put("timestamp", Instant.now().toString());
        
        ArrayNode sourcesArray = objectMapper.createArrayNode();
        if (sources != null) {
            for (String source : sources) {
                sourcesArray.add(source);
            }
        }
        conversationDoc.set("sources", sourcesArray);
        
        String docId = conversationDoc.get("_id").asText();
        storeDocument(docId, conversationDoc);
        return docId;
    }

    /**
     * Retrieves conversation history for a session, ordered by timestamp.
     */
    public List<ConversationTurn> getConversationHistory(String sessionId, int limit) throws Exception {
        String viewUrl = String.format("%s/%s/_design/conversations/_view/by_session_id?key=\"%s\"&limit=%d&descending=true", 
            couchUrl, database, sessionId, limit);
        
        HttpGet request = new HttpGet(viewUrl);
        String responseBody = executeRequest(request);
        
        JsonNode response = objectMapper.readTree(responseBody);
        ArrayNode rows = (ArrayNode) response.get("rows");
        
        List<ConversationTurn> history = new ArrayList<>();
        for (JsonNode row : rows) {
            JsonNode doc = row.get("value");
            ConversationTurn turn = new ConversationTurn(
                doc.get("question").asText(),
                doc.get("response").asText(),
                doc.get("timestamp").asText(),
                parseSourcesList(doc.get("sources"))
            );
            history.add(turn);
        }
        
        return history;
    }

    /**
     * Retrieves recent conversations across all sessions.
     */
    public List<ConversationTurn> getRecentConversations(int limit) throws Exception {
        String viewUrl = String.format("%s/%s/_design/conversations/_view/by_timestamp?limit=%d&descending=true", 
            couchUrl, database, limit);
        
        HttpGet request = new HttpGet(viewUrl);
        String responseBody = executeRequest(request);
        
        JsonNode response = objectMapper.readTree(responseBody);
        ArrayNode rows = (ArrayNode) response.get("rows");
        
        List<ConversationTurn> conversations = new ArrayList<>();
        for (JsonNode row : rows) {
            JsonNode doc = row.get("value");
            ConversationTurn turn = new ConversationTurn(
                doc.get("question").asText(),
                doc.get("response").asText(),
                doc.get("timestamp").asText(),
                parseSourcesList(doc.get("sources"))
            );
            conversations.add(turn);
        }
        
        return conversations;
    }

    /**
     * Searches conversations by keyword.
     */
    public List<ConversationTurn> searchConversations(String keyword, int limit) throws Exception {
        String viewUrl = String.format("%s/%s/_design/conversations/_view/by_timestamp?limit=%d", 
            couchUrl, database, limit);
        
        HttpGet request = new HttpGet(viewUrl);
        String responseBody = executeRequest(request);
        
        JsonNode response = objectMapper.readTree(responseBody);
        ArrayNode rows = (ArrayNode) response.get("rows");
        
        List<ConversationTurn> matches = new ArrayList<>();
        String lowerKeyword = keyword.toLowerCase();
        
        for (JsonNode row : rows) {
            JsonNode doc = row.get("value");
            String question = doc.get("question").asText().toLowerCase();
            String responseText = doc.get("response").asText().toLowerCase();
            
            if (question.contains(lowerKeyword) || responseText.contains(lowerKeyword)) {
                ConversationTurn turn = new ConversationTurn(
                    doc.get("question").asText(),
                    doc.get("response").asText(),
                    doc.get("timestamp").asText(),
                    parseSourcesList(doc.get("sources"))
                );
                matches.add(turn);
            }
        }
        
        return matches;
    }

    /**
     * Stores a generic document in CouchDB.
     */
    private void storeDocument(String docId, ObjectNode document) throws Exception {
        HttpPut request = new HttpPut(couchUrl + "/" + database + "/" + docId);
        request.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(document), "UTF-8"));
        
        executeRequest(request);
    }

    /**
     * Retrieves a document by ID.
     */
    public JsonNode getDocument(String docId) throws Exception {
        HttpGet request = new HttpGet(couchUrl + "/" + database + "/" + docId);
        String responseBody = executeRequest(request);
        return objectMapper.readTree(responseBody);
    }

    /**
     * Deletes a document by ID.
     */
    public void deleteDocument(String docId) throws Exception {
        // First get the document to get its revision
        JsonNode doc = getDocument(docId);
        String rev = doc.get("_rev").asText();
        
        HttpDelete request = new HttpDelete(couchUrl + "/" + database + "/" + docId + "?rev=" + rev);
        executeRequest(request);
    }

    /**
     * Gets database information and statistics.
     */
    public DatabaseInfo getDatabaseInfo() throws Exception {
        HttpGet request = new HttpGet(couchUrl + "/" + database);
        String responseBody = executeRequest(request);
        JsonNode info = objectMapper.readTree(responseBody);
        
        return new DatabaseInfo(
            info.get("db_name").asText(),
            info.get("doc_count").asInt(),
            info.get("doc_del_count").asInt(),
            info.get("disk_size").asLong()
        );
    }

    /**
     * Executes an HTTP request and returns the response body.
     */
    private String executeRequest(HttpUriRequest request) throws Exception {
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            int statusCode = response.getStatusLine().getStatusCode();
            
            if (statusCode >= 400) {
                throw new IOException("CouchDB request failed: " + statusCode + " - " + responseBody);
            }
            
            return responseBody;
        }
    }

    /**
     * Parses a JSON array of sources into a List of strings.
     */
    private List<String> parseSourcesList(JsonNode sourcesNode) {
        List<String> sources = new ArrayList<>();
        if (sourcesNode != null && sourcesNode.isArray()) {
            for (JsonNode source : sourcesNode) {
                sources.add(source.asText());
            }
        }
        return sources;
    }

    @Override
    public void close() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }
        System.out.println("✓ CouchDB client closed");
    }

    /**
     * Represents a conversation turn (question + response).
     */
    public static class ConversationTurn {
        private final String question;
        private final String response;
        private final String timestamp;
        private final List<String> sources;

        public ConversationTurn(String question, String response, String timestamp, List<String> sources) {
            this.question = question;
            this.response = response;
            this.timestamp = timestamp;
            this.sources = sources != null ? sources : new ArrayList<>();
        }

        public String getQuestion() { return question; }
        public String getResponse() { return response; }
        public String getTimestamp() { return timestamp; }
        public List<String> getSources() { return sources; }
    }

    /**
     * Represents database information.
     */
    public static class DatabaseInfo {
        private final String name;
        private final int docCount;
        private final int deletedDocCount;
        private final long diskSize;

        public DatabaseInfo(String name, int docCount, int deletedDocCount, long diskSize) {
            this.name = name;
            this.docCount = docCount;
            this.deletedDocCount = deletedDocCount;
            this.diskSize = diskSize;
        }

        public String getName() { return name; }
        public int getDocCount() { return docCount; }
        public int getDeletedDocCount() { return deletedDocCount; }
        public long getDiskSize() { return diskSize; }
        
        @Override
        public String toString() {
            return String.format("Database: %s, Documents: %d, Size: %d bytes", name, docCount, diskSize);
        }
    }
}