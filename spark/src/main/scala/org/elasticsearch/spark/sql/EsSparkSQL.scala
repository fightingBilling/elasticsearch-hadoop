package org.elasticsearch.spark.sql

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.Map

import org.apache.spark.annotation.AlphaComponent
import org.apache.spark.annotation.DeveloperApi
import org.apache.spark.api.java.JavaRDD.fromRDD
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.SchemaRDD
import org.apache.spark.sql.api.java.JavaSQLContext
import org.apache.spark.sql.api.java.JavaSchemaRDD
import org.apache.spark.sql.api.java.{StructType => JStructType}
import org.elasticsearch.hadoop.cfg.ConfigurationOptions.ES_QUERY
import org.elasticsearch.hadoop.cfg.ConfigurationOptions.ES_RESOURCE_READ
import org.elasticsearch.hadoop.cfg.ConfigurationOptions.ES_RESOURCE_WRITE
import org.elasticsearch.hadoop.cfg.PropertiesSettings
import org.elasticsearch.spark.cfg.SparkSettingsManager

object EsSparkSQL {

  def esRDD(sc: SQLContext): SchemaRDD = esRDD(sc, Map.empty[String, String])
  def esRDD(sc: SQLContext, resource: String): SchemaRDD = esRDD(sc, Map(ES_RESOURCE_READ -> resource))
  def esRDD(sc: SQLContext, resource: String, query: String): SchemaRDD = esRDD(sc, Map(ES_RESOURCE_READ -> resource, ES_QUERY -> query))
  def esRDD(sc: SQLContext, map: Map[String, String]): SchemaRDD = {
    val rowRDD = new ScalaEsRowRDD(sc.sparkContext, map)
    val schema = MappingUtils.discoverMapping(rowRDD.esCfg)
    sc.applySchema(rowRDD, schema)
  }

  def esRDD(jsc: JavaSQLContext): JavaSchemaRDD = esRDD(jsc, Map.empty[String, String])
  def esRDD(jsc: JavaSQLContext, resource: String): JavaSchemaRDD = esRDD(jsc, Map(ES_RESOURCE_READ -> resource))
  def esRDD(jsc: JavaSQLContext, resource: String, query: String): JavaSchemaRDD = esRDD(jsc, Map(ES_RESOURCE_READ -> resource, ES_QUERY -> query))
  def esRDD(jsc: JavaSQLContext, map: Map[String, String]): JavaSchemaRDD = { 
    val rowRDD = new JavaEsRowRDD(jsc.sqlContext.sparkContext, map)
    val schema = DataTypeConversions.asJavaDataType(MappingUtils.discoverMapping(rowRDD.esCfg)).asInstanceOf[JStructType]
    jsc.applySchema(rowRDD, schema)
  }
  
  def saveToEs(srdd: SchemaRDD, resource: String) {
    saveToEs(srdd, Map(ES_RESOURCE_WRITE -> resource))
  }
  def saveToEs(srdd: SchemaRDD, resource: String, params: Map[String, String]) {
    saveToEs(srdd, collection.mutable.Map(params.toSeq: _*) += (ES_RESOURCE_WRITE -> resource))
  }
  def saveToEs(srdd: SchemaRDD, params: Map[String, String]) {
    val sparkCfg = new SparkSettingsManager().load(srdd.sparkContext.getConf)
    val cfg = new PropertiesSettings().load(sparkCfg.save())
    cfg.merge(params.asJava)
    
    srdd.sparkContext.runJob(srdd, new EsSchemaRDDWriter(srdd.schema, cfg.save()).write _)
  }
}