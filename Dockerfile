# --- ETAPA DE CONSTRUCCIÓN ---
FROM maven:3.8-openjdk-17 AS builder

# Establecer directorio de trabajo
WORKDIR /app

# Copiar archivo de configuración de Maven
COPY pom.xml .

# Descargar dependencias (en una capa separada para aprovechar la caché)
RUN mvn dependency:go-offline -B

# Copiar código fuente
COPY src ./src

# Construir la aplicación
RUN mvn package -DskipTests

# --- ETAPA FINAL ---
FROM openjdk:17-slim

# Establecer directorio de trabajo
WORKDIR /app

# Copiar el JAR generado desde la etapa de construcción
COPY --from=builder /app/target/*.jar app.jar

# Exponer puerto (ajustar según tu aplicación)
EXPOSE 9000

# Comando para iniciar la aplicación
CMD ["java", "-jar", "app.jar"]