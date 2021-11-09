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

import io.cdap.cdap.api.common.Bytes;
import io.cdap.cdap.proto.id.TopicId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class TopicSubscriberMetaKey implements MetricsMetaKey {

  private static final String keyFormat = "topic:%s:%s:subscriber:%s";
  private static final String printFormat = "TopicSubscriberMetaKey{ key=%s }";
  private static final Logger LOG = LoggerFactory.getLogger(TopicSubscriberMetaKey.class);

  private final byte[] key;

  TopicSubscriberMetaKey(TopicId topicId, String subscriberId) {
    String formattedKey = String.format(keyFormat, topicId.getNamespace(), topicId.getTopic(), subscriberId);
    LOG.info("Formatted key is {}", formattedKey);
    this.key = Bytes.toBytes(formattedKey);
  }

  @Override
  public byte[] getKey() {
    return key;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TopicSubscriberMetaKey)) {
      return false;
    }
    TopicSubscriberMetaKey that = (TopicSubscriberMetaKey) o;
    return Arrays.equals(key, that.key);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(key);
  }

  @Override
  public String toString() {
    return String.format(printFormat, Bytes.toString(key));
  }
}
