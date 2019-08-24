# ASN.1 Data Source for Apache Spark 2.x
A library for parsing and querying ASN.1 encoded data (Ber/Der) with Apache Spark, for Spark SQL and DataFrames.
## Requirements

This library requires Spark 2.0+

## Features
This package allows reading ASN.1 encoded files in local or distributed filesystem as [Spark DataFrames](https://spark.apache.org/docs/latest/sql-programming-guide.html).
When reading files the API accepts several options:
* `path`: location of files. Similar to Spark can accept standard Hadoop globbing expressions.
* `schemaFileType`: the type of the file that contain the schema (currently supports asn and json files).
* `schemaFilePath`: the path of the file that contain the schema definition (currently supports scala and java).
* `customDecoderLanguage`: the language in which the custom decoder is written.
* `customDecoder`: the fully qualified name of the user custom decoder.


### Scala API

schema inference is not yet supported, the path of the schema definition file and its type or explicit schema definition are necessary :

* asn schema definition:
```scala
import org.apache.spark.sql.SparkSession
import org.apache.spark.SparkConf

val conf = new SparkConf().setAppName("spark-custom-datasource")
val spark = SparkSession.builder().config(conf).master("local[*]").getOrCreate()
val asn1DataFrame = spark.read.format("asn1V1")
      .option("schemaFileType","asn")
      .option("schemaFilePath", "src/test/resources/simpleTypes.asn")
      .load("src/test/resources/simpleTypes.ber")
```

* json schema definition:
```scala
import org.apache.spark.sql.SparkSession
import org.apache.spark.SparkConf

val conf = new SparkConf().setAppName("spark-custom-datasource")
val spark = SparkSession.builder().config(conf).master("local[*]").getOrCreate()
val asn1DataFrame = spark.read.format("asn1V1")
      .option("schemaFileType","json")
      .option("schemaFilePath", "src/test/resources/simpleTypes.json")
      .load("src/test/resources/simpleTypes.ber")
```

* explicit schema definition:
You can manually specify the schema when reading data:
```scala
import org.apache.spark.sql.SparkSession
import org.apache.spark.SparkConf
import org.apache.spark.sql.types.{StructType, StructField, StringType, IntegerType}

val conf = new SparkConf().setAppName("spark-custom-datasource")
val spark = SparkSession.builder().config(conf).master("local[*]").getOrCreate()
val schema = StructType(
      StructField("recordNumber", IntegerType, false) ::
        StructField("callingNumber", StringType, true) ::
        StructField("calledNumber", StringType, true) ::
        StructField("startDate", StringType, true) ::
        StructField("startTime", StringType, true) ::
        StructField("duration", IntegerType, true) :: Nil
    )

val asn1DataFrame = spark.read.format("asn1V1")
      .schema(schema)
      .load("src/test/resources/simpleTypes.ber")
```

You can use your own decoding logic:
you need to extend the ScalaDecoder Trait and put the decoding logic that takes an encoded record and a schema,decode it and return it as a sequence
```scala
package customDecoding

import customDecoding.ScalaDecoder
import org.apache.hadoop.io.Text
import org.apache.spark.sql.types.StructType


object CustomScalaDecoder extends ScalaDecoder {
override def decode(record: Text, schema: StructType): Seq[Any] = {
//your own decoding logic
return null
}
}
```
After creating your Custom Decoder use the customDecoder feature to integrate it:
```scala
import org.apache.spark.sql.SparkSession
import org.apache.spark.SparkConf

val conf = new SparkConf().setAppName("spark-custom-datasource")
val spark = SparkSession.builder().config(conf).master("local[*]").getOrCreate()
val asn1DataFrame = spark.read.format("asn1V1")
      .option("schemaFileType","asn")
      .option("schemaFilePath", "src/test/resources/simpleTypes.asn")
      .option("customDecoder","customDecoding.CustomScalaDecoder")
      .option("customDecoderLanguage","scala")
      .load("src/test/resources/simpleTypes.ber")
```
