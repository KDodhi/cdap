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

package io.cdap.cdap.etl.api.sql.engine.dataset;

import io.cdap.cdap.etl.api.engine.sql.dataset.RecordCollection;
import org.apache.spark.api.java.JavaRDD;

/**
 * Representation of the record collection for Spark records.
 * @param <T> type of the records stored in this collection.
 */
public interface SparkRecordCollection<T> extends RecordCollection<T> {
  JavaRDD<T> getRDD();
}
