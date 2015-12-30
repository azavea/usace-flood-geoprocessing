#!/bin/bash

JAR=ingest/target/scala-2.10/usaceflood-ingest-assembly-0.1.0-SNAPSHOT.jar

SOURCE=../data/tiles-4269
DEST=../data/catalog

spark-submit \
    --driver-memory=2G \
    --executor-memory=2G \
    --master "local[*]" \
    --class com.azavea.usaceflood.ingest.BaseLayerIngest \
    $JAR $SOURCE $DEST
