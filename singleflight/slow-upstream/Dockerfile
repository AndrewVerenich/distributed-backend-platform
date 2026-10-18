FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget
WORKDIR /app
COPY build/libs/slow-upstream.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
