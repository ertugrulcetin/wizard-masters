FROM openjdk:17-alpine

RUN apk update && apk add htop
RUN apk update && apk add procps

# Create necessary directories
RUN mkdir -p /resources/keys

COPY target/uberjar/enion-backend.jar /app.jar
COPY resources/keys/firestore.json /resources/keys/firestore.json

EXPOSE 8080

ENTRYPOINT exec java -server \
                     -Xmx1500m \
                     -jar \
                     /app.jar
