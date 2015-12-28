# USACE Flood Modeling Geoprocessing

A [Spark Job Server](https://github.com/spark-jobserver/spark-jobserver) job for USACE flood modeling.

## Usage

First, build the assembly JAR for this project:

```bash
$ git clone https://github.com/azavea/usace-flood-geoprocessing.git
$ cd usace-flood-geoprocessing
$ ./sbt assembly
```

To use a Docker based Scala build environment, you can use:

```bash
$ docker run \
    --rm \
    --volume ${HOME}/.ivy2:/root/.ivy2 \
    --volume ${PWD}:/usace-flood-geoprocessing \
    --workdir /usace-flood-geoprocessing \
    quay.io/azavea/scala:latest ./sbt assembly
```

Next, use the latest Spark Job Server (SJS) Docker image to launch an instance of SJS locally:

```bash
$ docker run \
    --detach \
    --volume ${PWD}/examples/conf/spark-jobserver.conf:/opt/spark-jobserver/spark-jobserver.conf:ro \
    --publish 8090:8090 \
    --name spark-jobserver \
    quay.io/azavea/spark-jobserver:latest
```

Now that the SJS service is running in the background, upload the assembly JAR and create a long-lived Spark context named `geoprocessing`:

```bash
$ curl --silent \
    --data-binary @summary/target/scala-2.10/usaceflood-geoprocessing-assembly-0.0.1.jar \
    'http://localhost:8090/jars/geoprocessing'
$ curl --silent --data "" \
    'http://localhost:8090/contexts/geoprocessing-context'
```

Once that process is complete, try submitting a job to the `geoprocessing-context`:

```bash
$ curl --silent \
    --data-binary "" \
    'http://localhost:8090/jobs?sync=true&context=geoprocessing-context&appName=geoprocessing&classPath=org.azavea.usaceflood.geoprocessing.SummaryJob'
```

## Deployments

Not yet deployed.
