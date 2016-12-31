/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cloudera.fce.curtis.sparkudfexamples.scalaudaf


import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.sql._
import org.apache.spark.sql.expressions.{MutableAggregationBuffer, UserDefinedAggregateFunction}
import org.apache.spark.sql.types._
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.SQLContext._

object UDAF {

  //
  // A UDAF that sums sales over $500
  //
  private class ScalaAggregateFunction extends UserDefinedAggregateFunction {

    // an aggregation function can take multiple arguments in general. but
    // this one just takes one
    def inputSchema: StructType =
      new StructType().add("sales", DoubleType)
    // the aggregation buffer can also have multiple values in general but
    // this one just has one: the partial sum
    def bufferSchema: StructType =
      new StructType().add("sumLargeSales", DoubleType)
    // returns just a double: the sum
    def dataType: DataType = DoubleType
    // always gets the same result
    def deterministic: Boolean = true

    // each partial sum is initialized to zero
    def initialize(buffer: MutableAggregationBuffer): Unit = {
      buffer.update(0, 0.0)
    }

    // an individual sales value is incorporated by adding it if it exceeds 500.0
    def update(buffer: MutableAggregationBuffer, input: Row): Unit = {
      val sum = buffer.getDouble(0)
      if (!input.isNullAt(0)) {
        val sales = input.getDouble(0)
        if (sales > 500.0) {
          buffer.update(0, sum+sales)
        }
      }
    }

    // buffers are merged by adding the single values in them
    def merge(buffer1: MutableAggregationBuffer, buffer2: Row): Unit = {
      buffer1.update(0, buffer1.getDouble(0) + buffer2.getDouble(0))
    }

    // the aggregation buffer just has one value: so return it
    def evaluate(buffer: Row): Any = {
      buffer.getDouble(0)
    }
  }

  def main (args: Array[String]) {

    val conf = new SparkConf().setAppName("Scala UDF Example")
    val sc = new SparkContext(conf)
    val sqlContext = new SQLContext(sc)
    import sqlContext.implicits._
    
    // create an RDD of tuples with some data
    val custs = Seq(
      (1, "Widget Co", 120000.00, 0.00, "AZ"),
      (2, "Acme Widgets", 410500.00, 500.00, "CA"),
      (3, "Widgetry", 200.00, 200.00, "CA"),
      (4, "Widgets R Us", 410500.00, 0.0, "CA"),
      (5, "Ye Olde Widgete", 500.00, 0.0, "MA")
    )
    val customerRows = sc.parallelize(custs, 4)
    val customerDF = customerRows.toDF("id", "name", "sales", "discount", "state")

    val mysum = new ScalaAggregateFunction()

    customerDF.printSchema()

    // val results = customerDF.groupBy("state").agg(mysum($"sales").as("bigsales"))

    // results.printSchema()
    // results.show()
    customerDF.registerTempTable("testDF") 
    sqlContext.udf.register("CURTIS", new ScalaAggregateFunction)
    sqlContext.sql("SELECT state, CURTIS(sales) as bigsales FROM testDF GROUP BY state").show()
  }

}


/*
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.SQLContext._

object ScalaUDAFSample {
  def main(args: Array[String]) {
    val conf = new SparkConf().setAppName("Scala UDF Example")
    val sc = new SparkContext(conf)
    val sqlContext = new SQLContext(sc)

    val testDF = sqlContext.read.json("udfTestInput.json")
    testDF.registerTempTable("testDF")

    // sqlContext.registerFunction(
    sqlContext.udf.register("CURTIS", (f: Double) => ((f*9.0/5.0)+32.0))
    sqlContext.sql("SELECT CURTIS(numVal) AS Fahrenheit FROM testDF").show()
    sc.stop()
  }
}
*/