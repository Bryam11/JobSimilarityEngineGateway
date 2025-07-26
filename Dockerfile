# Etapa de construcción con Maven y JDK 17
FROM maven:3.9.6-eclipse-temurin-17 AS build

# Crear directorio de trabajo
WORKDIR /app

# Copiar pom.xml y descargar dependencias
COPY pom.xml .
RUN mvn dependency:go-offline

# Copiar el resto del código fuente
COPY src ./src

# Construir el proyecto sin correr pruebas
RUN mvn clean package -DskipTests

# Etapa de ejecución: imagen ligera con JDK 17
FROM eclipse-temurin:17-jre-alpine

# Puerto expuesto en Cloud Run (ajusta si usas otro puerto)
EXPOSE 8080

# Copiar el .jar compilado desde la etapa de build
COPY --from=build /app/target/*.jar app.jar

# Comando de ejecución
ENTRYPOINT ["java", "-jar", "/app.jar"]
