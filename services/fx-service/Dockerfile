FROM eclipse-temurin:21.0.8_9-jdk
WORKDIR /app
RUN useradd --system --uid 1001 carddemo && chown -R carddemo:carddemo /app
COPY target/fx-service-*.jar app.jar
USER carddemo
EXPOSE 8094
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
