FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# 阿里云 Maven 镜像加速（国内必备）
COPY settings-docker.xml /root/.m2/settings.xml
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
