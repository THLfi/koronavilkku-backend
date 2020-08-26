FROM openjdk:11
# need mvwn from parent folder to build
COPY pom.xml /usr/src/base/pom.xml
COPY mvnw /usr/src/base/publish-token/mvnw
COPY .mvn /usr/src/base/publish-token/.mvn
COPY ./publish-token /usr/src/base/publish-token
WORKDIR /usr/src/base/publish-token
# prepare for windows file endings
RUN apt-get update && apt-get install dos2unix
RUN dos2unix mvnw
RUN ./mvnw clean package
RUN cp target/*.jar app.jar
ENTRYPOINT ["java", "-Dspring.profiles.active=dev", "-jar","app.jar"]
