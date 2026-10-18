# Build stage: compile and package the jar with Maven.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# Run stage: a small JRE image, running as a non-root user.
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --no-create-home appuser
COPY --from=build /workspace/target/ticket-booking.jar app.jar
USER appuser
EXPOSE 8080
# MaxRAMPercentage lets the JVM use most of the container's memory, which matters on small free-tier hosts.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "app.jar"]
