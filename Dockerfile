FROM eclipse-temurin:21-jre-alpine
RUN apk update && apk upgrade && apk add --no-cache ffmpeg
COPY backend/build/docker/lib/ /app/lib/
COPY backend/build/docker/app/app.jar /app/app.jar
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -cp /app/app.jar:/app/lib/* io.sebi.ApplicationKt"]
