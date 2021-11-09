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

package io.cdap.cdap.metrics.process;

import io.cdap.cdap.metrics.store.MetricDatasetFactory;
import io.cdap.cdap.proto.id.TopicId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class DefaultMetadataHandler implements MetadataHandler {

  private final ConcurrentMap<MetricsMetaKey, TopicProcessMeta> topicProcessMetaMap;
  private MetricsConsumerMetaTable metaTable;
  private final MetricDatasetFactory metricDatasetFactory;
  private final String metricsPrefixForDelayMetrics;
  private final MetricsMetaKeyProvider keyProvider;
  private List<TopicId> metricsTopics;

  public DefaultMetadataHandler(MetricDatasetFactory metricDatasetFactory, String metricsPrefixForDelayMetrics,
                                MetricsMetaKeyProvider keyProvider) {
    this.topicProcessMetaMap = new ConcurrentHashMap<>();
    this.metricDatasetFactory = metricDatasetFactory;
    this.metricsPrefixForDelayMetrics = metricsPrefixForDelayMetrics;
    this.keyProvider = keyProvider;
  }

  public void initCache(List<TopicId> metricsTopics) {
    this.metaTable = getMetaTable();
    this.metricsTopics = metricsTopics;
    Map<TopicId, MetricsMetaKey> keyMap = keyProvider.getKeys(metricsTopics);
    for (Map.Entry<TopicId, MetricsMetaKey> keyEntry : keyMap.entrySet()) {
      MetricsMetaKey topicIdMetaKey = keyEntry.getValue();
      TopicProcessMeta topicProcessMeta = metaTable.getTopicProcessMeta(topicIdMetaKey);
      if (topicProcessMeta == null || topicProcessMeta.getMessageId() == null) {
        continue;
      }
      String oldestTsMetricName = String.format("%s.topic.%s.oldest.delay.ms",
                                                metricsPrefixForDelayMetrics, keyEntry.getKey());
      String latestTsMetricName = String.format("%s.topic.%s.latest.delay.ms",
                                                metricsPrefixForDelayMetrics, keyEntry.getKey());
      topicProcessMetaMap.put(topicIdMetaKey,
                              new TopicProcessMeta(topicProcessMeta.getMessageId(),
                                                   topicProcessMeta.getOldestMetricsTimestamp(),
                                                   topicProcessMeta.getLatestMetricsTimestamp(),
                                                   topicProcessMeta.getMessagesProcessed(),
                                                   topicProcessMeta.getLastProcessedTimestamp(),
                                                   oldestTsMetricName, latestTsMetricName));
    }
  }

  /**
   * @param key
   * @param value
   */
  public void updateCache(MetricsMetaKey key, TopicProcessMeta value) {
    topicProcessMetaMap.put(key, value);
  }

  /**
   * Persist the cache
   *
   * @param topicProcessMetaMap
   */
  public void saveCache(Map<MetricsMetaKey, TopicProcessMeta> topicProcessMetaMap) {
    if (topicProcessMetaMap.isEmpty()) {
      return;
    }
    metaTable.saveMetricsProcessorStats(topicProcessMetaMap);
  }

  private MetricsConsumerMetaTable getMetaTable() {
    //TODO - see if we can use retry library and throw exception
    while (metaTable == null) {
      try {
        metaTable = metricDatasetFactory.createConsumerMeta();
      } catch (Exception e) {
        //LOG.warn("Cannot access consumer metaTable, will retry in 1 sec.");
        try {
          TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
    return metaTable;
  }

  /**
   * Get {@link TopicProcessMeta} for {@link MetricsMetaKey}
   *
   * @param key {@link MetricsMetaKey}
   * @return {@link TopicProcessMeta}
   */
  public TopicProcessMeta getTopicProcessMeta(MetricsMetaKey key) {
    return topicProcessMetaMap.get(key);
  }

  /**
   * Return a copy of the current cache
   *
   * @return {@link Map<MetricsMetaKey, TopicProcessMeta>}
   */
  public Map<MetricsMetaKey, TopicProcessMeta> getCache() {
    //return a copy of current map
    return new HashMap<>(topicProcessMetaMap);
  }
}
