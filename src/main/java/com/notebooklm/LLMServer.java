package com.notebooklm;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import java.io.File;

public class LLMServer implements AutoCloseable {
    private final Tomcat tomcat;
    private final GraniteModelWrapper modelWrapper;

    public LLMServer(String modelPath) throws Exception {
        this.modelWrapper = new GraniteModelWrapper(modelPath); // Load the model
        this.tomcat = new Tomcat();
        tomcat.setPort(8080); // Use a standard local port
        tomcat.getConnector(); // Required for Tomcat 10+

        Context ctx = tomcat.addContext("", new File(".").getAbsolutePath());

        // Pass the model wrapper instance to the servlet
        InferenceServlet servlet = new InferenceServlet(modelWrapper);
        Tomcat.addServlet(ctx, "inferenceServlet", servlet);
        ctx.addServletMappingDecoded("/api/generate", "inferenceServlet");

        System.out.println("🚀 Starting embedded Tomcat server for LLM inference...");
        tomcat.start();
        System.out.println("✓ LLM Server ready at http://localhost:8080");
    }

    public void await() {
        tomcat.getServer().await();
    }

    @Override
    public void close() throws Exception {
        System.out.println("Shutting down LLM Server...");
        modelWrapper.close(); // Release model from memory
        tomcat.stop();
        tomcat.destroy();
        System.out.println("✓ LLM Server stopped.");
    }
}
