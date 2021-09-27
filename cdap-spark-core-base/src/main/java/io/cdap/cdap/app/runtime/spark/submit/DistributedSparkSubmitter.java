/*
 * Copyright © 2017 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.cdap.app.runtime.spark.submit;

import com.google.common.collect.ImmutableList;
import io.cdap.cdap.app.runtime.Arguments;
import io.cdap.cdap.app.runtime.spark.SparkRuntimeContext;
import io.cdap.cdap.app.runtime.spark.SparkRuntimeContextConfig;
import io.cdap.cdap.app.runtime.spark.SparkRuntimeEnv;
import io.cdap.cdap.app.runtime.spark.SparkRuntimeUtils;
import io.cdap.cdap.app.runtime.spark.distributed.SparkExecutionService;
import io.cdap.cdap.common.conf.CConfiguration;
import io.cdap.cdap.common.conf.Constants;
import io.cdap.cdap.internal.app.runtime.workflow.BasicWorkflowToken;
import io.cdap.cdap.internal.app.runtime.workflow.WorkflowProgramInfo;
import io.cdap.cdap.proto.id.ProgramRunId;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.twill.filesystem.LocationFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * A {@link SparkSubmitter} to submit Spark job that runs on cluster.
 */
public class DistributedSparkSubmitter extends AbstractSparkSubmitter {

  private static final Logger LOG = LoggerFactory.getLogger(DistributedSparkSubmitter.class);

  private final CConfiguration cConf;
  private final Configuration hConf;
  private final String schedulerQueueName;
  private final SparkExecutionService sparkExecutionService;
  private final long tokenRenewalInterval;

  public DistributedSparkSubmitter(CConfiguration cConf, Configuration hConf, LocationFactory locationFactory,
                                   String hostname, SparkRuntimeContext runtimeContext,
                                   @Nullable String schedulerQueueName) {
    this.cConf = cConf;
    this.hConf = hConf;
    this.schedulerQueueName = schedulerQueueName;
    ProgramRunId programRunId = runtimeContext.getProgram().getId().run(runtimeContext.getRunId().getId());
    WorkflowProgramInfo workflowInfo = runtimeContext.getWorkflowInfo();
    BasicWorkflowToken workflowToken = workflowInfo == null ? null : workflowInfo.getWorkflowToken();
    this.sparkExecutionService = new SparkExecutionService(locationFactory, hostname, programRunId, workflowToken);

    Arguments systemArgs = runtimeContext.getProgramOptions().getArguments();
    this.tokenRenewalInterval = systemArgs.hasOption(SparkRuntimeContextConfig.CREDENTIALS_UPDATE_INTERVAL_MS)
      ? Long.parseLong(systemArgs.getOption(SparkRuntimeContextConfig.CREDENTIALS_UPDATE_INTERVAL_MS))
      : -1L;
  }

  @Override
  protected Map<String, String> getSubmitConf() {
    Map<String, String> config = new HashMap<>();
    if (schedulerQueueName != null && !schedulerQueueName.isEmpty()) {
      config.put("spark.yarn.queue", schedulerQueueName);
    }
    if (tokenRenewalInterval > 0) {
      config.put("spark.yarn.token.renewal.interval", Long.toString(tokenRenewalInterval));
    }
    config.put("spark.yarn.appMasterEnv.CDAP_LOG_DIR",  ApplicationConstants.LOG_DIR_EXPANSION_VAR);
    config.put("spark.executorEnv.CDAP_LOG_DIR", ApplicationConstants.LOG_DIR_EXPANSION_VAR);

    //--conf spark.kubernetes.container.image=gcr.io/ashau-dev0/spark:latest
    //       --conf spark.kubernetes.authenticate.driver.serviceAccountName=spark
    config.put("spark.kubernetes.container.image", "gcr.io/ardekani-cdf-sandbox2/cdap-spark:latest");
    config.put("spark.kubernetes.container.image.pullPolicy", "Always");
    // this was a service account I manually created
    config.put("spark.kubernetes.authenticate.driver.serviceAccountName", "spark");
    config.put("spark.kubernetes.executor.deleteOnTermination", "false");
    config.put("spark.executorEnv.ARTIFACT_FECTHER_PORT", cConf.get(Constants.Spark.Driver.PORT));

    try {
      File templateFile = new File("podTemplate");
      String podTemplateBase = ""
        + "spec:\n"
        + "  volumes:\n"
        + "  - configMap:\n"
        + "      name: cdap-cdap-cconf\n"
        + "    name: cdap-cconf\n"
        + "  containers:\n"
        + "  - args:\n"
        + "    volumeMounts:\n"
        + "    - mountPath: /etc/cdap/conf\n"
        + "      name: cdap-cconf";
      try (FileWriter writer = new FileWriter(templateFile)) {
        writer.write(podTemplateBase);
      }
      config.put("spark.kubernetes.driver.podTemplateFile", templateFile.getAbsolutePath());
    } catch (Exception e) {
      System.err.println("ashau - error writing pod template file");
      e.printStackTrace(System.err);
    }

    return config;
  }

  @Override
  protected void addMaster(Map<String, String> configs, ImmutableList.Builder<String> argBuilder) {
    argBuilder
      //.add("--master").add("yarn")
      // how to get the kubernetes ip? Passed from KubeMasterEnvironment somehow?
      .add("--master").add("k8s://https://35.233.147.34")
      .add("--deploy-mode").add("cluster");
  }

  @Override
  protected List<String> beforeSubmit() {
    // Add all Hadoop configurations to the SparkRuntimeEnv, prefix with "spark.hadoop.". This is
    // how Spark YARN client get hold of Hadoop configurations if those configurations are not in classpath,
    // which is true in CM cluster due to private hadoop conf directory (SPARK-13441) and YARN-4727
    for (Map.Entry<String, String> entry : hConf) {
      SparkRuntimeEnv.setProperty("spark.hadoop." + entry.getKey(), hConf.get(entry.getKey()));
    }

    sparkExecutionService.startAndWait();
    SparkRuntimeEnv.setProperty("spark.yarn.appMasterEnv." + SparkRuntimeUtils.CDAP_SPARK_EXECUTION_SERVICE_URI,
                                sparkExecutionService.getBaseURI().toString());
    return Collections.emptyList();
  }

  @Override
  protected void triggerShutdown() {
    // Just stop the execution service and block on that.
    // It will wait until the "completed" call from the Spark driver.
    sparkExecutionService.stopAndWait();
  }

  @Override
  protected void onCompleted(boolean succeeded) {
    if (succeeded) {
      sparkExecutionService.stopAndWait();
    } else {
      sparkExecutionService.shutdownNow();
    }
  }
}
