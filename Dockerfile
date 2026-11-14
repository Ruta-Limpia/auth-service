# Imagen para ejecutar auth-service. El jar llega ya compilado y probado
# (lo arma el pipeline o ./mvnw clean package), asi la imagen queda liviana.
FROM eclipse-temurin:25-jre

RUN groupadd --system spring && useradd --system --gid spring --no-create-home spring

WORKDIR /app
COPY target/auth-service-*.jar app.jar

USER spring
EXPOSE 8081

ENV JAVA_OPTS="-XX:MaxRAMPercentage=60"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
