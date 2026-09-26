# Step 1: Build Java application using Maven
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Step 2: Run application with lightweight JRE
FROM eclipse-temurin:17-jre
WORKDIR /app

# Exclude original-*.jar and copy the shaded executable jar
COPY --from=builder /app/target/!(*original*).jar app.jar 2>/dev/null || true

# If the shell glob didn't match, copy target jars safely
COPY --from=builder /app/target /app/target

EXPOSE 8080

# Find the fat jar (excluding original) and execute it directly
CMD ["sh", "-c", "JAR_FILE=$(ls target/*.jar | grep -v 'original' | head -n 1) && java -jar $JAR_FILE"]