FROM eclipse-temurin:17-jre
ARG JAR_FILE=target/*.jar
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50 -XX:MinRAMPercentage=25"
COPY ${JAR_FILE} app.jar
EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app.jar"]
