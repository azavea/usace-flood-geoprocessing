# USACE Flood Modeling Geoprocessing

This repository contains two projects for facilitating the [USACE Flood Model web app](https://github.com/azavea/usace-flood-model):

 1. [`ingest`](#ingest): For taking input elevation data and processing it into a format usable by [GeoTrellis](https://github.com/geotrellis/geotrellis)
 2. [`server`](#server): A [Spray](https://github.com/spray/spray) based web service that performs geoprocessing operations

Both projects are run using [Apache Spark](http://spark.apache.org/).

## `ingest`

This project takes input elevation data and processes it into RDDs that will be read by GeoTrellis. The input data was originally in DEM files supplied by the client. Using [GDal](http://www.gdal.org/), these were stitched together, then clipped to the relevant area of interest, and tiled (see [azavea/usace-flood-model#2](https://github.com/azavea/usace-flood-model/issues/2)).

The input data is in EPSG:4269. The `server` provides two main services: calculation of inundated percent area based on a given polygon, and rendering the corresponding tiles. The area calculation must be done in the original projection, while rendering must be done using WebMercator. Thus, we have two tile sets, one in each projection.

### Build

To build the ingest JAR, execute the following:

```bash
$ ./sbt "project ingest" assembly
```

which should result in `ingest/target/scala-2.10/usaceflood-ingest-assembly-<version>-SNAPSHOT.jar`.

### Usage

We ingest the two tile sets one by one. For the purposes of calculating area, we want the original projection in as high a resolution as possible. Assuming the tiles are available in `~/data/tiles-4269`, we run the following script (available in `ingest-4269.sh`):

```bash
#!/bin/bash

JAR=ingest/target/scala-2.10/usaceflood-ingest-assembly-<version>-SNAPSHOT.jar

SOURCE=~/data/tiles-4269
DEST=~/data/catalog

spark-submit \
    --driver-memory=2G \
    --executor-memory=2G \
    --master "local[*]" \
    --class com.azavea.usaceflood.ingest.BaseLayerIngest \
    $JAR $SOURCE $DEST
```

For the purposes of rendering tiles, we want the WebMercator projection and we want to pyramid the tiles so as to render the right zoom levels. Assuming the reprojected tiles are available in `~/data/tiles-webm`, we run the following script:

```bash
#!/bin/bash

JAR=ingest/target/scala-2.10/usaceflood-ingest-assembly-<version>-SNAPSHOT.jar

SOURCE=~/data/tiles-webm
DEST=~/data/catalog

spark-submit \
    --driver-memory=4G \
    --executor-memory=4G \
    --master "local[*]" \
    --class com.azavea.usaceflood.ingest.ZoomedLayerIngest \
    $JAR $SOURCE $DEST
```

Note that the second operation requires more memory to complete. Both ingest operations can take minutes to hours to complete. While they are running, their progress can be observed at http://localhost:4040/.

The final output in `~/data/catalog` will look like this:

```bash
$ ls -1 ~/data/catalog
attributes
usace-mississippi-dem-10m-epsg4269
usace-mississippi-dem-xyz
```

Where the `usace-mississippi-dem-10m-epsg4269` directory contains ingested rasters for calculation, and the `usace-mississippi-dem-xyz` directory contains ingested rasters for rendering. The `attributes` directory contains JSON metadata used to read the files, with references to the full path of the rasters. This full path must be changed if the files are moved to another location.

These final ingested rasters are available on the fileshare: `/projects/USArmyCorps_WISDM/data/flood_model/catalog`. The full path in the `attributes` metadata there is `/flood-data/catalog` which is the target path inside the deployment VM.

## `server`

[![Build Status](https://travis-ci.org/azavea/usace-flood-geoprocessing.png?branch=master)](https://travis-ci.org/azavea/usace-flood-geoprocessing)

This project is a Spray based web service that performs calculations for percent inundation given a polygon, and rendering tiles of inundated areas. It runs locally on port `8090`.

### API Endpoints

The project serves the following endpoints:

#### `/ping`

Used to test that the API is working.

```http
$ http :8090/ping
HTTP/1.1 200 OK
Content-Length: 2
Content-Type: text/plain; charset=UTF-8
Date: Mon, 18 Jan 2016 17:50:55 GMT
Server: spray-can/1.3.3

OK
```

#### `/elevation`

Used to get minimum and maximum elevation in meters for any given polygon. Accepts a JSON blob as a POST parameter with a single key of `multiPolygon`.

```http
$ http :8090/elevation multiPolygon:=@eastOmaha.json
HTTP/1.1 200 OK
Access-Control-Allow-Headers: Origin, X-Requested-With, Content-Type, Accept, Accept-Encoding, Accept-Language, Host, Referer, User-Agent, Access-Control-Request-Method, Access-Control-Request-Headers
Access-Control-Allow-Methods: GET, POST, OPTIONS, DELETE
Access-Control-Allow-Origin: *
Content-Length: 52
Content-Type: application/json; charset=UTF-8
Date: Mon, 18 Jan 2016 19:05:21 GMT
Server: spray-can/1.3.3

{
    "maxElevation": 314.0,
    "minElevation": 293.0
}
```

#### `/flood-percentages`

Used to get percentage flooding at given levels (in meters) for any given polygon. Accepts a JSON blob as a POST parameter with keys of `multiPolygon`, `floodLevels` which should be an array of floats, and `minElevation` which should be a single float value. Returns an array of floats, each between 0 and 1, corresponding to the percent of the polygon flooded at that level.

```http
$ http :8090/flood-percentages multiPolygon:=@eastOmaha.json minElevation:=293.0 'floodLevels:=[1, 5, 10]'
HTTP/1.1 200 OK
Access-Control-Allow-Headers: Origin, X-Requested-With, Content-Type, Accept, Accept-Encoding, Accept-Language, Host, Referer, User-Agent, Access-Control-Request-Method, Access-Control-Request-Headers
Access-Control-Allow-Methods: GET, POST, OPTIONS, DELETE
Access-Control-Allow-Origin: *
Content-Length: 63
Content-Type: application/json; charset=UTF-8
Date: Mon, 18 Jan 2016 19:09:06 GMT
Server: spray-can/1.3.3

[
    0.0002697455808725861,
    0.4814468172064984,
    0.9873832635128231
]
```

#### `/flood-tiles/{zoom}/{x}/{y}.png`

Used to get a rendered PNG tile at a given flood level for any given polygon. In addition to the `zoom`, `x`, and `y` path parameters, accepts a JSON blob as a POST parameter with keys of `multiPolygon`, `floodLevel`, `minElevation` and `maxElevation`, the last three of which all should be single float values. We must specify both minimum and maximum elevation here to allow the use of the proper color ramp which applies to the entire polygon, not just the elevation values within the tile being requested. Also, since the output is an image, we must specify an `Accept:image/png` header.

```http
$ http --download --output eastOmaha-12-956-1531.png :8090/flood-tiles/12/956/1531.png Accept:image/png multiPolygon:=@eastOmaha.json floodLevel:=10 minElevation:=293 maxElevation:=314
HTTP/1.1 200 OK
Content-Length: 4446
Content-Type: image/png
Date: Mon, 18 Jan 2016 19:16:52 GMT
Server: spray-can/1.3.3

Downloading 4.34 kB to "eastOmaha-12-956-1531.png"
Done. 4.34 kB in 0.00062s (6.80 MB/s)
```

And the produced image:

![eastOmaha-12-956-1531.png](https://cloud.githubusercontent.com/assets/1430060/12365387/7ec69ac6-bba2-11e5-8463-1724b24e739b.png)

### Build

To build the server JAR, execute the following:

```bash
$ ./sbt "project server" assembly
```

which should result in `server/target/scala-2.10/usaceflood-server-assembly-<version>-SNAPSHOT.jar`.

### Usage

The server expects three environment variables to be set:

| Environment Variable Name | Default Value | Description |
| ------------------------- | ------------- | ----------- |
| `RDD_CATALOG_PATH`        | `/flood-data/catalog` | The path of `catalog` produced by the ingestion |
| `DEM_10M_LAYER`           | `usace-mississippi-dem-10m-epsg4269` | The directory containing detailed 10m resolution tiles in EPSG:4269 |
| `DEM_XYZ_LAYER`           | `usace-mississippi-dem-xyz` | The directory containing pyramided tiles in WebMercator |

The provisioning of the VM does set these variables, and the default values are set to what they should be inside the VM.

For running the server locally, assuming the catalog is available in `~/data/catalog`, and all the JSON files under `~/data/catalog/attributes` have the correct absolute path set, execute:

```bash
$ export RDD_CATALOG_PATH=~/data/catalog
$ spark-submit server/target/scala-2.10/usaceflood-server-assembly-<version>-SNAPSHOT.jar
```

As Spark loads the JAR it will output a number of messages. Once the following are shown, the server is ready to accept requests:

```
[INFO] [01/18/2016 15:26:41.235] [usaceflood-server-akka.actor.default-dispatcher-3] [akka://usaceflood-server/user/IO-HTTP/listener-0] Bound to /0.0.0.0:8090
[INFO] [01/18/2016 15:26:41.237] [usaceflood-server-akka.actor.default-dispatcher-6] [akka://usaceflood-server/deadLetters] Message [akka.io.Tcp$Bound] from Actor[akka://usaceflood-server/user/IO-HTTP/listener-0#1169292696] to Actor[akka://usaceflood-server/deadLetters] was not delivered. [1] dead letters encountered. This logging can be turned off or adjusted with configuration settings 'akka.log-dead-letters' and 'akka.log-dead-letters-during-shutdown'.
```

The server can be stopped with <kbd>Ctrl</kbd> + <kbd>C</kbd>.

### Deployment

To test locally, after the JAR is built, it should be placed under `/opt/geoprocessing/` inside the VM.

Deployments to GitHub Releases are handled via [Travis-CI](https://travis-ci.org/azavea/usace-flood-geoprocessing). The following `git-flow` commands signal to Travis that we want to create a release. The `version` variable should be updated in `project/Version.scala.`

``` bash
$ git flow release start 0.1.0
$ vim CHANGELOG.md
$ vim project/Version.scala
$ git commit -m "0.1.0"
$ git flow release publish 0.1.0
$ git flow release finish 0.1.0
```

You should now check the `develop` and `master` branches on Github to make sure that they look correct.  In particular, they should both contain the changes that you made to `CHANGELOG.md`.  If they do not, then the following two steps may also be required:
```bash
$ git push origin develop:develop
$ git push origin master:master
```

To actually kick off the deployment, ensure that the newly created Git tags are pushed remotely with `git push --tags`.
