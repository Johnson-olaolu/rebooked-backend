# Stage 1: Build Java application and React admin
FROM gradle:8.11.1-jdk21-alpine AS build

USER root

# Install Node.js and npm on Alpine
RUN apk add --no-cache nodejs npm

COPY --chown=gradle:gradle . /home/gradle/src

WORKDIR /home/gradle/src

RUN gradle build --no-daemon

# Stage 2: Run Spring Boot application
FROM amazoncorretto:21-alpine

WORKDIR /app

COPY --from=build /home/gradle/src/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar app.jar"]