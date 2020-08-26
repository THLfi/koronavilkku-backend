FROM openjdk:11
# need mvwn from parent folder to build
COPY pom.xml /usr/src/base/pom.xml
COPY mvnw /usr/src/base/exposure-notification/mvnw
COPY .mvn /usr/src/base/exposure-notification/.mvn
COPY ./exposure-notification /usr/src/base/exposure-notification
WORKDIR /usr/src/base/exposure-notification
# prepare for windows file endings
RUN apt-get update && apt-get install dos2unix
RUN dos2unix mvnw
RUN ./mvnw clean package
RUN cp target/*.jar app.jar
ENTRYPOINT ["java", "-Dspring.profiles.active=dev", "-jar","app.jar"]
