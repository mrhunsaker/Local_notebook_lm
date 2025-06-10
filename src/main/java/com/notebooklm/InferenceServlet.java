package com.notebooklm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;

public class InferenceServlet extends HttpServlet {
    private final GraniteModelWrapper modelWrapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InferenceServlet(GraniteModelWrapper modelWrapper) {
        this.modelWrapper = modelWrapper;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            String body = req.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
            JsonNode requestNode = objectMapper.readTree(body);
            String prompt = requestNode.get("prompt").asText();

            // Perform inference using the model wrapper
            String generatedText = modelWrapper.generateResponse(prompt);

            // Create JSON response
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            ObjectNode responseNode = objectMapper.createObjectNode();
            responseNode.put("response", generatedText);

            resp.getWriter().write(objectMapper.writeValueAsString(responseNode));
            resp.setStatus(HttpServletResponse.SC_OK);

        } catch (Exception e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to generate response: " + e.getMessage());
        }
    }
}
