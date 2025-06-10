package com.notebooklm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import java.io.IOException;

/**
 * An internal client to communicate with the self-hosted LLM server running on Tomcat.
 */
public class InternalLLMClient implements AutoCloseable {
    private static final String INTERNAL_GRANITE_URL = "http://localhost:8080/api/generate";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CloseableHttpClient httpClient;

    public InternalLLMClient() {
        this.httpClient = HttpClients.createDefault();
    }

    /**
     * Generates a text response from the full prompt.
     */
    public String generateTextResponse(String fullPrompt) throws Exception {
        HttpPost request = new HttpPost(INTERNAL_GRANITE_URL);

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("prompt", fullPrompt);

        StringEntity entity = new StringEntity(objectMapper.writeValueAsString(requestBody), "UTF-8");
        request.setEntity(entity);
        request.setHeader("Content-type", "application/json");

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode != 200) {
                throw new IOException("LLM server returned error: " + statusCode + " - " + responseBody);
            }

            JsonNode node = objectMapper.readTree(responseBody);
            if (node.has("error")) {
                throw new Exception("LLM Error: " + node.get("error").asText());
            }
            return node.get("response").asText();
        }
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }
}
