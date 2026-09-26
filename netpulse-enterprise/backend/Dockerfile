# Step 1: Build Java application using Maven
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Step 2: Run application with lightweight JRE
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080

# Execute with explicit classpath and main class entry point
CMD ["java", "-cp", "app.jar", "com.netpulse.backend.BackendApplication"]