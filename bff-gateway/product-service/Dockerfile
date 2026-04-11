FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget
WORKDIR /app
COPY build/libs/product-service-1.0.0.jar app.jar
EXPOSE 8092
ENTRYPOINT ["java", "-jar", "app.jar"]
