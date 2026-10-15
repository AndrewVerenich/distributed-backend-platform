FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget
WORKDIR /app
COPY build/libs/demo-upstream.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
