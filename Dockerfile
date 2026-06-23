# Single-container build: compiles the backend AND the React UI (in web/), baking
# the UI into the jar's static/ so the running container serves everything on
# :8080. Build context is the repo root.

# --- build stage ---
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

# Maven wrapper + manifest first, for dependency-layer caching.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# Sources (backend + frontend). The frontend-maven-plugin builds web/ during the
# package phase and the result is copied into the jar.
COPY src src
COPY web web
RUN ./mvnw -B clean package -DskipTests

# --- runtime stage ---
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
