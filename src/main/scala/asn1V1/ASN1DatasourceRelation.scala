package asn1V1

import customDecoding.DynamicScalaDecoderObjectLoader
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.io.{LongWritable, Text}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.sources._
import org.apache.spark.sql.types._
import org.apache.spark.sql.{Row, SQLContext}
import util.Util
import hadoopIO.AsnInputFormat
import reader.{AsnSchemaParser, JsonSchemaParser}


case class ASN1DatasourceRelation(override val sqlContext: SQLContext, schemaFileType: String, path: String
                                  , userSchema: StructType, schemaFilePath: String, customDecoder: String
                                  , customDecoderLanguage: String, precisionFactor: String, mainTag: String)
  extends BaseRelation with TableScan with PrunedScan with Serializable {


  var currentSchema: StructType = _
  var initialSchema: StructType = _
  var tagNumber: Int = _
  var tagByte: String = _

  {
    if (userSchema != null) {
      currentSchema = StructType(Asn1Parser.flatten(userSchema))
      initialSchema = userSchema
    } else {
      initialSchema = inferSchema(schemaFilePath)
      currentSchema = StructType(Asn1Parser.flatten(initialSchema))
    }

    if (mainTag.equals("sequence")) {
      tagNumber = 16
      tagByte = "48"
    } else if (mainTag.equals("set")) {
      tagNumber = 17
      tagByte = "48"
    } else {
      throw new IllegalArgumentException("Illegal tag '" + mainTag + "'")
    }

  }


  override def schema: StructType = currentSchema


  override def buildScan(): RDD[Row] = {
    val conf: Configuration = new Configuration(sqlContext.sparkContext.hadoopConfiguration)
    val FileRDD: RDD[(LongWritable, Text)] = sqlContext.sparkContext
      .newAPIHadoopFile(path, classOf[AsnInputFormat], classOf[LongWritable], classOf[Text], conf)
    val encodedRecordsRDD: RDD[Text] = FileRDD.map(x => x._2)
    val decodedRecordsRDD = encodedRecordsRDD.map((encodedLine: Text) => {
      Asn1Parser.decodeRecord(encodedLine, currentSchema, true, tagNumber)
    })
    decodedRecordsRDD.map(s => Row.fromSeq(s))
  }


  override def buildScan(requiredColumns: Array[String]): RDD[Row] = {
    val conf: Configuration = new Configuration(sqlContext.sparkContext.hadoopConfiguration)
    conf.set("precisionFactor", precisionFactor)
    conf.set("tagByte", tagByte.toString)
    val FileRDD: RDD[(LongWritable, Text)] = sqlContext.sparkContext
      .newAPIHadoopFile(path, classOf[AsnInputFormat], classOf[LongWritable], classOf[Text], conf)
    val encodedLinesRDD: RDD[Text] = FileRDD.map(x => x._2)
    val decodedLinesRDD = encodedLinesRDD.map(encodedLine => {
      if (customDecoder.equals("none")) {
        try {
          Asn1Parser.decodeRecord(encodedLine, initialSchema, true, tagNumber)
        }
        catch {
          case _: Exception => {
            Seq()
          }
        }
      } else {
        if (customDecoderLanguage.equals("java")) {
          val customDecoderClassInstance = Class.forName(customDecoder).newInstance
          val decodeMethod = customDecoderClassInstance.getClass.getMethod("decode", encodedLine.getClass, initialSchema.getClass)
          decodeMethod.invoke(customDecoderClassInstance, encodedLine, initialSchema).asInstanceOf[Seq[Any]]
        } else if (customDecoderLanguage.equals("scala")) {
          DynamicScalaDecoderObjectLoader.getDecoderObject(customDecoder).decode(encodedLine, initialSchema)
        } else {
          throw new IllegalArgumentException("Illegal decoder language '" + customDecoderLanguage + "'")
        }
      }
    })
    val filteredDecodedLinesRDD = decodedLinesRDD
      .map(s => Util.rearrangeSequence(requiredColumns, s, currentSchema))
      .map(s => s.filter(_ != " "))
      .filter(!_.isEmpty)
      .map(s => Row.fromSeq(s))
    filteredDecodedLinesRDD
  }

  private def inferSchema(path: String): StructType = {
    var inferredSchema: StructType = null
    if (schemaFileType == "asn") {
      inferredSchema = AsnSchemaParser.getParsedSchema(path)
    } else if (schemaFileType == "json") {
      inferredSchema = JsonSchemaParser.JsonSourceFileToStructType(path)
    } else {
      throw new IllegalArgumentException("Illegal schema file extension '" + schemaFileType + "'")
    }
    inferredSchema
  }


}
