/*
 * Copyright © 2021 Cask Data, Inc.
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

package io.cdap.cdap.metrics.process.gcp;

import io.cdap.cdap.api.metrics.MetricValues;
import io.cdap.cdap.api.metrics.MetricsWriter;
import io.cdap.cdap.api.metrics.MetricsWriterContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;

public class GoogleCloudMonitoringWriter implements MetricsWriter {

  private static final Logger LOG = LoggerFactory.getLogger(GoogleCloudMonitoringWriter.class);

  @Override
  public void write(Collection<MetricValues> metricValues) {
    LOG.info("Getting metric values in GoogleCloudMonitoringWriter");
  }

  @Override
  public void initialize(MetricsWriterContext metricsWriterContext) {

  }

  @Override
  public String getID() {
    return "google_cloud_monitoring_writer";
  }

  @Override
  public void close() throws IOException {

  }
}
