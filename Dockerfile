FROM openjdk:8-alpine

COPY target/uberjar/github-project-watch.jar /github-project-watch/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/github-project-watch/app.jar"]
