FROM eclipse-temurin:21-jdk-alpine
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
RUN apk update
RUN apk upgrade
RUN apk add --no-cache ffmpeg
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app.jar"]