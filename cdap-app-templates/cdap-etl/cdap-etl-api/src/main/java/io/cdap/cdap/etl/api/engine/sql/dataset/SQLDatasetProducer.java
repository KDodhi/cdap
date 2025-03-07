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

package io.cdap.cdap.etl.api.engine.sql.dataset;

/**
 * SQL Dataset which exposes a method to get a producer that can be used to extract
 * records from the SQL engine.
 *
 * @param <T> the type of records this producer will produce
 */
public interface SQLDatasetProducer<T> {
  SQLDatasetDescription getDescription();
  RecordCollection<T> produce(SQLDataset dataset);
}
