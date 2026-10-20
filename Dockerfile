# Stage 1: build the React app
FROM node:22-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Stage 2: build the Spring Boot JAR with the React build inside static/
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /backend
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY backend/src src
COPY --from=frontend /frontend/dist src/main/resources/static
RUN ./mvnw -B -q -DskipTests package && cp target/festpass-*.jar app.jar

# Stage 3: small runtime image with just the JAR
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 festpass
USER festpass
COPY --from=backend /backend/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
