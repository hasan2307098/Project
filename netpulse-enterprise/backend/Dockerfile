# Step 1: Build Java application using Maven
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Step 2: Run application with lightweight JRE
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/target /app/target

EXPOSE 8080

CMD ["sh", "-c", "JAR=$(find target -maxdepth 1 -name '*.jar' ! -name 'original*' | head -n 1) && echo \"Starting $JAR\" && java -jar $JAR"]