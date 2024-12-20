FROM eclipse-temurin:21-jdk-alpine
RUN apk update && apk upgrade && apk add --no-cache ffmpeg
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app.jar"]