FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget
WORKDIR /app
COPY build/libs/event-bridge-1.0.0.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
