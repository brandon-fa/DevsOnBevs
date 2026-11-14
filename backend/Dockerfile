# Little Isles backend. The build context is the repository root, because the server
# also needs config/ and database/ (docker-compose.yml sets this up):
#   docker build -f backend/Dockerfile .

# 1. Compile the server and collect its runtime libraries (Gson, SQLite JDBC).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend/pom.xml .
# Download dependencies in their own layer, so code changes don't download them again.
RUN mvn -B -q dependency:go-offline
COPY backend/src src
RUN mvn -B -q compile dependency:copy-dependencies -DincludeScope=runtime

# 2. Run it on a JRE, from a directory laid out the way the server expects:
#    it looks for config/ and database/ in its working directory.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/classes classes
COPY --from=build /build/target/dependency lib
COPY config config
COPY database database
RUN useradd --system littleisle && chown -R littleisle database
USER littleisle
EXPOSE 8080
CMD ["java", "-cp", "classes:lib/*", "com.littleisles.Main"]
