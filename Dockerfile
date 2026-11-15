# Imagen de auth-service en dos etapas:
# 1) "build" compila con Maven y separa el jar en capas
# 2) la imagen final solo lleva el JRE y las capas, sin codigo fuente ni Maven

FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /build

# primero solo lo que define las dependencias, asi esta capa queda en cache
# mientras no cambie el pom.xml
COPY mvnw pom.xml ./
COPY .mvn/ .mvn/
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw \
    && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# los tests ya corren en el pipeline, aqui solo se empaqueta
RUN ./mvnw -B -q package -Dmaven.test.skip=true \
    && cp target/auth-service-*.jar auth-service.jar \
    && java -Djarmode=tools -jar auth-service.jar extract --layers --destination extracted


FROM eclipse-temurin:25-jre-alpine

RUN addgroup -S spring && adduser -S -G spring -H spring
WORKDIR /app

# de la capa que menos cambia a la que mas cambia
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

USER spring
EXPOSE 8081

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60"
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8081/actuator/health > /dev/null || exit 1

ENTRYPOINT ["java", "-jar", "auth-service.jar"]
