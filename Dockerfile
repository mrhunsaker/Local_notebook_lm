# ---- Build Stage ----
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom and source
COPY pom.xml .
COPY src ./src
COPY models ./models
COPY llama.cpp ./llama.cpp
COPY scripts ./scripts

# Build the native components and fat JAR
RUN mvn clean package -Pnative-build -DskipTests

# ---- Runtime Stage ----
FROM eclipse-temurin:17-jre

WORKDIR /app

# Install minimal X11 for GUI support (Debian-based)
RUN apt-get update && apt-get install -y libxext6 libxrender1 libxtst6 libxi6 libfreetype6 libfontconfig1 && rm -rf /var/lib/apt/lists/*

# Copy built JAR and native libs
COPY --from=build /app/target/integrated-rag-system-self-contained-2.0.0.jar .
COPY --from=build /app/llama.cpp/build ./llama.cpp/build
COPY --from=build /app/models ./models
COPY --from=build /app/source-documents ./source-documents
COPY --from=build /app/src/main/resources ./src/main/resources

# Expose port for embedded Tomcat (if needed)
EXPOSE 8080

# Set environment variables for Java library path
ENV JAVA_OPTS="-Djava.library.path=/app/llama.cpp/build -Xmx8g"

# Default to launching the GUI
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -cp integrated-rag-system-self-contained-2.0.0.jar com.notebooklm.NotebookLMCloneGUI"]
