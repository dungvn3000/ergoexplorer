FROM maven:3-eclipse-temurin-21 as build
WORKDIR /myapp
COPY pom.xml pom.xml
COPY src src
COPY conf conf
RUN mvn package -DskipTests

FROM eclipse-temurin:21-jdk
WORKDIR /myapp
COPY --from=build /myapp/target/stork .
EXPOSE 8080
CMD ["bin/ergo_explorer", "--run", "prod"]
