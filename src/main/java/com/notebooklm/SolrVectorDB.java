package com.notebooklm;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for interacting with Apache Solr for vector-based document storage and retrieval.
 * Supports both vector similarity search and traditional keyword search.
 */
public class SolrVectorDB implements AutoCloseable {
    private final SolrClient solrClient;
    private final String coreName;
    private final GraniteModelWrapper modelWrapper;

    public SolrVectorDB(String solrUrl, String coreName, GraniteModelWrapper modelWrapper) {
        this.coreName = coreName;
        this.modelWrapper = modelWrapper;
        this.solrClient = new Http2SolrClient.Builder(solrUrl).build();
        System.out.println("✓ Connected to Solr at " + solrUrl + "/" + coreName);
    }

    /**
     * Stores a document chunk with its vector embedding in Solr.
     */
    public void storeDocument(DocumentChunk chunk) throws SolrServerException, IOException {
        // Generate embedding for the chunk content
        List<Float> embedding = modelWrapper.generateEmbedding(chunk.getContent());
        
        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", chunk.getId());
        doc.addField("title", chunk.getTitle());
        doc.addField("content", chunk.getContent());
        doc.addField("file_path", chunk.getFilePath());
        doc.addField("vector", embedding);
        
        // Add any additional metadata
        Map<String, Object> metadata = chunk.getMetadata();
        if (metadata != null) {
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                String key = entry.getKey();
                // Prefix metadata fields to avoid conflicts
                if (!key.startsWith("meta_")) {
                    key = "meta_" + key;
                }
                doc.addField(key, entry.getValue());
            }
        }

        UpdateResponse response = solrClient.add(coreName, doc);
        if (response.getStatus() != 0) {
            throw new IOException("Failed to store document in Solr: " + response.toString());
        }
    }

    /**
     * Batch stores multiple document chunks.
     */
    public void storeDocuments(List<DocumentChunk> chunks) throws SolrServerException, IOException {
        List<SolrInputDocument> docs = new ArrayList<>();
        
        for (DocumentChunk chunk : chunks) {
            List<Float> embedding = modelWrapper.generateEmbedding(chunk.getContent());
            
            SolrInputDocument doc = new SolrInputDocument();
            doc.addField("id", chunk.getId());
            doc.addField("title", chunk.getTitle());
            doc.addField("content", chunk.getContent());
            doc.addField("file_path", chunk.getFilePath());
            doc.addField("vector", embedding);
            
            // Add metadata
            Map<String, Object> metadata = chunk.getMetadata();
            if (metadata != null) {
                for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                    String key = entry.getKey();
                    if (!key.startsWith("meta_")) {
                        key = "meta_" + key;
                    }
                    doc.addField(key, entry.getValue());
                }
            }
            
            docs.add(doc);
        }

        UpdateResponse response = solrClient.add(coreName, docs);
        if (response.getStatus() != 0) {
            throw new IOException("Failed to batch store documents in Solr: " + response.toString());
        }
        
        // Commit the changes
        solrClient.commit(coreName);
        System.out.println("✓ Stored " + chunks.size() + " document chunks in Solr");
    }

    /**
     * Performs a hybrid search combining vector similarity and keyword search.
     */
    public List<SearchResult> search(String query, int topK) throws SolrServerException, IOException {
        // Generate embedding for the query
        List<Float> queryEmbedding = modelWrapper.generateEmbedding(query);
        
        SolrQuery solrQuery = new SolrQuery();
        
        // Combine vector search with keyword search using boost
        StringBuilder queryBuilder = new StringBuilder();
        
        // Vector similarity search (primary)
        queryBuilder.append("{!knn f=vector topK=").append(topK * 2).append("}");
        queryBuilder.append("[");
        for (int i = 0; i < queryEmbedding.size(); i++) {
            if (i > 0) queryBuilder.append(",");
            queryBuilder.append(queryEmbedding.get(i));
        }
        queryBuilder.append("]");
        
        // Add keyword search boost for exact matches
        queryBuilder.append(" OR (content:\"").append(escapeQuery(query)).append("\")^2.0");
        queryBuilder.append(" OR (title:\"").append(escapeQuery(query)).append("\")^3.0");
        
        solrQuery.setQuery(queryBuilder.toString());
        solrQuery.setRows(topK);
        solrQuery.setFields("id", "title", "content", "file_path", "score", "meta_*");
        
        QueryResponse response = solrClient.query(coreName, solrQuery);
        SolrDocumentList results = response.getResults();
        
        List<SearchResult> searchResults = new ArrayList<>();
        for (SolrDocument doc : results) {
            SearchResult result = new SearchResult();
            result.setId((String) doc.getFieldValue("id"));
            result.setTitle((String) doc.getFieldValue("title"));
            result.setContent((String) doc.getFieldValue("content"));
            result.setFilePath((String) doc.getFieldValue("file_path"));
            result.setScore(doc.getFieldValue("score") != null ? 
                          ((Number) doc.getFieldValue("score")).floatValue() : 0.0f);
            
            // Extract metadata
            Map<String, Object> metadata = new HashMap<>();
            for (String fieldName : doc.getFieldNames()) {
                if (fieldName.startsWith("meta_")) {
                    String originalKey = fieldName.substring(5); // Remove "meta_" prefix
                    metadata.put(originalKey, doc.getFieldValue(fieldName));
                }
            }
            result.setMetadata(metadata);
            
            searchResults.add(result);
        }
        
        System.out.println("Found " + searchResults.size() + " results for query: " + 
                          query.substring(0, Math.min(50, query.length())));
        
        return searchResults;
    }

    /**
     * Performs a pure keyword search without vector similarity.
     */
    public List<SearchResult> keywordSearch(String query, int topK) throws SolrServerException, IOException {
        SolrQuery solrQuery = new SolrQuery();
        
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("content:\"").append(escapeQuery(query)).append("\"");
        queryBuilder.append(" OR title:\"").append(escapeQuery(query)).append("\"^2.0");
        
        solrQuery.setQuery(queryBuilder.toString());
        solrQuery.setRows(topK);
        solrQuery.setFields("id", "title", "content", "file_path", "score", "meta_*");
        
        QueryResponse response = solrClient.query(coreName, solrQuery);
        SolrDocumentList results = response.getResults();
        
        List<SearchResult> searchResults = new ArrayList<>();
        for (SolrDocument doc : results) {
            SearchResult result = new SearchResult();
            result.setId((String) doc.getFieldValue("id"));
            result.setTitle((String) doc.getFieldValue("title"));
            result.setContent((String) doc.getFieldValue("content"));
            result.setFilePath((String) doc.getFieldValue("file_path"));
            result.setScore(doc.getFieldValue("score") != null ? 
                          ((Number) doc.getFieldValue("score")).floatValue() : 0.0f);
            
            // Extract metadata
            Map<String, Object> metadata = new HashMap<>();
            for (String fieldName : doc.getFieldNames()) {
                if (fieldName.startsWith("meta_")) {
                    String originalKey = fieldName.substring(5);
                    metadata.put(originalKey, doc.getFieldValue(fieldName));
                }
            }
            result.setMetadata(metadata);
            
            searchResults.add(result);
        }
        
        return searchResults;
    }

    /**
     * Clears all documents from the Solr core.
     */
    public void clearAll() throws SolrServerException, IOException {
        solrClient.deleteByQuery(coreName, "*:*");
        solrClient.commit(coreName);
        System.out.println("✓ Cleared all documents from Solr core: " + coreName);
    }

    /**
     * Deletes documents by file path.
     */
    public void deleteByFilePath(String filePath) throws SolrServerException, IOException {
        solrClient.deleteByQuery(coreName, "file_path:\"" + escapeQuery(filePath) + "\"");
        solrClient.commit(coreName);
        System.out.println("✓ Deleted documents for file: " + filePath);
    }

    /**
     * Gets the total number of documents in the core.
     */
    public long getDocumentCount() throws SolrServerException, IOException {
        SolrQuery query = new SolrQuery("*:*");
        query.setRows(0);
        QueryResponse response = solrClient.query(coreName, query);
        return response.getResults().getNumFound();
    }

    /**
     * Commits any pending changes to the Solr index.
     */
    public void commit() throws SolrServerException, IOException {
        solrClient.commit(coreName);
    }

    /**
     * Escapes special characters in Solr queries.
     */
    private String escapeQuery(String query) {
        return query.replaceAll("([+\\-!(){}\\[\\]^\"~*?:\\\\/])", "\\\\$1");
    }

    @Override
    public void close() throws IOException {
        if (solrClient != null) {
            solrClient.close();
            System.out.println("✓ Closed Solr connection");
        }
    }

    /**
     * Inner class representing a search result.
     */
    public static class SearchResult {
        private String id;
        private String title;
        private String content;
        private String filePath;
        private float score;
        private Map<String, Object> metadata;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }

        public float getScore() { return score; }
        public void setScore(float score) { this.score = score; }

        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

        @Override
        public String toString() {
            return String.format("SearchResult{id='%s', title='%s', filePath='%s', score=%.3f}", 
                               id, title, filePath, score);
        }
    }
}