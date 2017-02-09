FROM quay.io/azavea/spark:2.1.0

ENV VERSION 0.1.5

COPY server/target/scala-2.11/usaceflood-assembly-${VERSION}.jar /opt/geoprocessing/

WORKDIR /opt/geoprocessing

EXPOSE 8090

ENTRYPOINT ["/bin/bash", "-c", "spark-submit /opt/geoprocessing/usaceflood-assembly-${VERSION}.jar"]
