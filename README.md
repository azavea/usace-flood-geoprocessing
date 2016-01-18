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

which should result in `ingest/target/scala-2.10/usaceflood-ingest-assembly-SNAPSHOT.jar`.

### Usage

We ingest the two tile sets one by one. For the purposes of calculating area, we want the original projection in as high a resolution as possible. Assuming the tiles are available in `~/data/tiles-4269`, we run the following script (available in `ingest-4269.sh`):

```bash
#!/bin/bash

JAR=ingest/target/scala-2.10/usaceflood-ingest-assembly-SNAPSHOT.jar

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

JAR=ingest/target/scala-2.10/usaceflood-ingest-assembly-SNAPSHOT.jar

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
