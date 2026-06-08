FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY miniapp-delivery/delivery-backend/pom.xml ./delivery-backend/pom.xml
COPY miniapp-delivery/delivery-backend/src ./delivery-backend/src
RUN mvn -f delivery-backend/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/delivery-backend/target/*.jar app.jar
EXPOSE 9081
ENV JAVA_OPTS="-Xms256m -Xmx768m"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
