/*
 * Copyright © 2018 Cask Data, Inc.
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

package co.cask.cdap.data2.spi;

import co.cask.cdap.data2.spi.table.TableId;

/**
 * Exception thrown when a field is not part of the table schema.
 */
public class InvalidFieldException extends RuntimeException {
  private final String fieldName;
  private final TableId tableId;

  public InvalidFieldException(TableId tableId, String fieldName) {
    super(String.format("Field %s is not part of the schema of table %s, or has wrong type",
                        fieldName, tableId.getName()));
    this.tableId = tableId;
    this.fieldName = fieldName;
  }

  public TableId getTableId() {
    return tableId;
  }

  public String getFieldName() {
    return fieldName;
  }
}
