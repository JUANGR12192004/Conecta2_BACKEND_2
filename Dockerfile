# -----------------------
# Etapa 1 - Compilar el JAR
# -----------------------
FROM maven:3.9.5-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw || true
COPY src ./src
RUN ./mvnw -B clean package -DskipTests

# -----------------------
# Etapa 2 - Ejecutar el JAR
# -----------------------
FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh","-c","java -jar /app/app.jar"]
