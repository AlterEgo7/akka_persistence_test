FROM hseeberger/scala-sbt:8u171_2.12.6_1.2.1 as builder
WORKDIR /app
ADD . /app
RUN sbt assembly

FROM openjdk:8-jre-alpine
COPY --from=builder /app/target/scala-2.12/akka-sample-persistence-scala-assembly-0.1.0-SNAPSHOT.jar .
CMD java -jar akka-sample-persistence-scala-assembly-0.1.0-SNAPSHOT.jar
