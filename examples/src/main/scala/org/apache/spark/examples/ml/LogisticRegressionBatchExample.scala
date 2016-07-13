/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// scalastyle:off println
package org.apache.spark.examples.ml

import scala.collection.mutable
import scala.language.reflectiveCalls

import scopt.OptionParser

import org.apache.spark.examples.mllib.AbstractParams
import org.apache.spark.ml.{Pipeline, PipelineStage}
import org.apache.spark.ml.classification.{ LogisticRegression, LogisticRegressionModel, LogisticRegressionBatch }
import org.apache.spark.ml.feature.StringIndexer
import org.apache.spark.sql.{DataFrame, SparkSession}

/**
 * An example runner for logistic regression with elastic-net (mixing L1/L2) regularization.
 * Run with
 * {{{
 * bin/run-example ml.LogisticRegressionExample [options]
 * }}}
 * A synthetic dataset can be found at `data/mllib/sample_libsvm_data.txt` which can be
 * trained by
 * {{{
 * bin/run-example ml.LogisticRegressionExample --regParam 0.3 --elasticNetParam 0.8 \
 *   data/mllib/sample_libsvm_data.txt
 * }}}
 * If you use it as a template to create your own app, please use `spark-submit` to submit your app.
 */
object LogisticRegressionBatchExample {

  case class Params(
    input: String = null,
    testInput: String = "",
    dataFormat: String = "libsvm",
    regParam: Double = 0.0,
    elasticNetParam: Double = 0.0,
    maxIter: Int = 100,
    fitIntercept: Boolean = true,
    tol: Double = 1E-6,
    numRDDpart: Int = 2,
    batchSize: Int = 2,
    fracTest: Double = 0.2) extends AbstractParams[Params]

  def main(args: Array[String]) {
    val defaultParams = Params()

    val parser = new OptionParser[Params]("LogisticRegressionBatchExample") {
      head("LogisticRegressionExample: an example Logistic Regression Batch with Elastic-Net app.")
      opt[Double]("regParam")
        .text(s"regularization parameter, default: ${defaultParams.regParam}")
        .action((x, c) => c.copy(regParam = x))
      opt[Double]("elasticNetParam")
        .text(s"ElasticNet mixing parameter. For alpha = 0, the penalty is an L2 penalty. " +
        s"For alpha = 1, it is an L1 penalty. For 0 < alpha < 1, the penalty is a combination of " +
        s"L1 and L2, default: ${defaultParams.elasticNetParam}")
        .action((x, c) => c.copy(elasticNetParam = x))
      opt[Int]("maxIter")
        .text(s"maximum number of iterations, default: ${defaultParams.maxIter}")
        .action((x, c) => c.copy(maxIter = x))
      opt[Int]("batchSize")
        .text(s"batch Size, default: ${defaultParams.batchSize}")
        .action((x, c) => c.copy(batchSize = x))
      opt[Int]("numRDDpart")
        .text(s"number of RDD partitions, default: ${defaultParams.numRDDpart}")
        .action((x, c) => c.copy(numRDDpart = x))
      opt[Boolean]("fitIntercept")
        .text(s"whether to fit an intercept term, default: ${defaultParams.fitIntercept}")
        .action((x, c) => c.copy(fitIntercept = x))
      opt[Double]("tol")
        .text(s"the convergence tolerance of iterations, Smaller value will lead " +
        s"to higher accuracy with the cost of more iterations, default: ${defaultParams.tol}")
        .action((x, c) => c.copy(tol = x))
      opt[Double]("fracTest")
        .text(s"fraction of data to hold out for testing. If given option testInput, " +
        s"this option is ignored. default: ${defaultParams.fracTest}")
        .action((x, c) => c.copy(fracTest = x))
      opt[String]("testInput")
        .text(s"input path to test dataset. If given, option fracTest is ignored." +
        s" default: ${defaultParams.testInput}")
        .action((x, c) => c.copy(testInput = x))
      opt[String]("dataFormat")
        .text("data format: libsvm (default), dense (deprecated in Spark v1.1)")
        .action((x, c) => c.copy(dataFormat = x))
      arg[String]("<input>")
        .text("input path to labeled examples")
        .required()
        .action((x, c) => c.copy(input = x))
      checkConfig { params =>
        if (params.fracTest < 0 || params.fracTest >= 1) {
          failure(s"fracTest ${params.fracTest} value incorrect; should be in [0,1).")
        } else {
          success
        }
      }
    }

    parser.parse(args, defaultParams).map { params =>
      run(params)
    }.getOrElse {
      sys.exit(1)
    }
  }

  def run(params: Params) {
    val spark = SparkSession
      .builder
      .appName(s"LogisticRegressionBatchExample with $params")
      .getOrCreate()

    println(s"LogisticRegressionBatchExample with parameters:\n$params")

    // Load training and test data and cache it.
    val (training: DataFrame, test: DataFrame) = DecisionTreeExample.loadDatasets(params.input,
      params.dataFormat, params.testInput, "classification", params.fracTest)

    if (params.batchSize > 0) {
		var batch = new Array[LogisticRegression](params.batchSize )

		var i = 0
		for (i <- 0 until batch.size) {
		batch(i) = new LogisticRegression()
			.setFeaturesCol("features")
			.setLabelCol("label")
			.setRegParam(params.regParam+(0.1-params.regParam)*i/batch.size)     
			.setElasticNetParam(params.elasticNetParam)
			.setMaxIter(params.maxIter)
			.setTol(params.tol)
			.setFitIntercept(params.fitIntercept)
		}

		val start_time = System.currentTimeMillis()
		val lor_batch = new LogisticRegressionBatch(batch)
		val lor_return = lor_batch.train(training, params.numRDDpart).collect() 
      
		printf("*****%d batch took %d ms\n", lor_return.size, System.currentTimeMillis() - start_time);
		lor_batch.summarize(training,lor_return) 
		}

    spark.stop()
  }
}
// scalastyle:on println
