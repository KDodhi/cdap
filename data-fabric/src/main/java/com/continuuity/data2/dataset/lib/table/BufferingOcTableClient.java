package com.continuuity.data2.dataset.lib.table;

import com.continuuity.api.common.Bytes;
import com.continuuity.api.data.OperationException;
import com.continuuity.api.data.OperationResult;
import com.continuuity.api.data.batch.Split;
import com.continuuity.data.operation.KeyRange;
import com.continuuity.data.operation.StatusCode;
import com.continuuity.data.table.Scanner;
import com.continuuity.data2.RuntimeTable;
import com.continuuity.data2.dataset.api.DataSetClient;
import com.continuuity.data2.transaction.Transaction;
import com.continuuity.data2.transaction.TransactionAware;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/**
 * An abstract {@link TransactionAware} implementation of {@link OrderedColumnarTable} which keeps data in memory buffer
 * until transaction commits.
 * <p>
 * Subclasses should implement methods which deal with persistent store. This implementation merges data from persistent
 * store and in-memory buffer for read/write operations.
 * NOTE: this implementation does not allow storing null as a value of a column
 * NOTE: data fetched from persisted store should never have nulls in column values: this class doesn't check that and
 *       they could be exposed to user as nulls. At the same time since null value is not allowed by this implementation
 *       this could lead to un-expected results
 * <p>
 * This implementation assumes that the table has name and conflicts are resolved on row level.
 * <p>
 * NOTE: this implementation doesn't cache any data in-memory besides changes. I.e. if you do get of same data that is
 *       not in in-memory buffer twice, two times it will try to fetch it from persistent store.
 *       Given the snapshot isolation tx model, this can be improved in future implementations.
 * <p>
 * NOTE: current implementation persists changes only at the end of transaction. Beware of OOME. There should be better
 *       implementation for MapReduce case (YMMV though, for counters/aggregations this implementation looks sweet)
 * <p>
 * NOTE: Using {@link #get(byte[], byte[], byte[], int)} is generally always not efficient since it always hits the
 *       persisted store even if all needed data is in-memory buffer. See more info at method javadoc
 */
public abstract class BufferingOcTableClient implements OrderedColumnarTable, DataSetClient, TransactionAware {
  /** name of the table */
  private final String name;
  /** name length + name of the table: handy to have one cached */
  private final byte[] nameAsTxChangePrefix;

  // In-memory buffer that keeps not yet persisted data. It is row->(column->value) map. Value can be null which means
  // that the corresponded column was removed.
  private NavigableMap<byte[], NavigableMap<byte[], byte[]>> buff;

  /**
   * Creates an instance of {@link BufferingOcTableClient}
   * @param name table name
   */
  public BufferingOcTableClient(String name) {
    // for optimization purposes we don't allow table name of length greater than Byte.MAX_VALUE
    Preconditions.checkArgument(name.length() < Byte.MAX_VALUE,
                                "Too big table name: " + name + ", exceeds " + Byte.MAX_VALUE);
    this.name = name;
    // we want it to be of format length+value to avoid conflicts
    this.nameAsTxChangePrefix = Bytes.add(new byte[]{(byte) name.length()}, Bytes.toBytes(name));
    this.buff = Maps.newTreeMap(Bytes.BYTES_COMPARATOR);
  }

  /**
   * @return name of this table
   */
  protected String getName() {
    return name;
  }

  /**
   * Persists in-memory buffer. After this method returns we assume that data can be visible to other table clients
   * (of course other clients may choose still not to see it based on transaction isolation logic).
   * @param buff in-memory buffer to persist. Map is described as row->(column->value). Map can contain null values
   *             which means that the corresponded column was deleted
   * @throws Exception
   */
  protected abstract void persist(NavigableMap<byte[], NavigableMap<byte[], byte[]>> buff)
    throws Exception;

  /**
   * Fetches data from persisted store.
   * NOTE: persisted store can also be in-memory, it is called "persisted" to distinguish from in-memory buffer.
   * @param row row key
   * @param column column name
   * @return value if it exists, otherwise null
   * @throws Exception
   */
  // even though not used at the moment, the implementation can be improved to use it for more efficient code
  @SuppressWarnings("unused")
  protected abstract byte[] getPersisted(byte[] row, byte[] column)
    throws Exception;

  /**
   * Fetches column->value pairs for set of columns from persistent store.
   * NOTE: persisted store can also be in-memory, it is called "persisted" to distinguish from in-memory buffer.
   * @param row row key defines the row to fetch columns from
   * @param columns set of columns to fetch
   * @return map of column->value pairs, never null.
   * @throws Exception
   */
  protected abstract NavigableMap<byte[], byte[]> getPersisted(byte[] row, byte[][] columns)
    throws Exception;

  /**
   * Fetches column->value pairs for range of columns from persistent store.
   * NOTE: persisted store can also be in-memory, it is called "persisted" to distinguish from in-memory buffer.
   * NOTE: Using this method is generally always not efficient since it always hits the
   *       persisted store even if all needed data is in-memory buffer. Since columns set is not strictly defined the
   *       implementation always looks up for more columns in persistent store.
   * @param row row key defines the row to fetch columns from
   * @param startColumn first column in a range, inclusive
   * @param stopColumn last column in a range, exclusive
   * @param limit max number of columns to fetch
   * @return map of column->value pairs, never null.
   * @throws Exception
   */
  protected abstract NavigableMap<byte[], byte[]> getPersisted(byte[] row,
                                                               byte[] startColumn, byte[] stopColumn,
                                                               int limit)
    throws Exception;

  /**
   * Scans range of rows from persistent store.
   * NOTE: persisted store can also be in-memory, it is called "persisted" to distinguish from in-memory buffer.
   * @param startRow key of the first row in a range, inclusive
   * @param stopRow key of the last row in a range, exclusive
   * @return instance of {@link Scanner}, never null
   * @throws Exception
   */
  protected abstract  Scanner scanPersisted(byte[] startRow, byte[] stopRow) throws Exception;

  @Override
  public void close() {
    // releasing resources
    buff = null;
  }

  @Override
  public void startTx(Transaction tx) {
    // starting with fresh buffer when tx starts
    buff.clear();
  }

  @Override
  public Collection<byte[]> getTxChanges() {
    // we resolve conflicts on row level of individual table
    List<byte[]> changes = new ArrayList<byte[]>(buff.size());
    for (byte[] changedRow : buff.keySet()) {
      changes.add(Bytes.add(nameAsTxChangePrefix, changedRow));
    }
    return changes;
  }

  @Override
  public boolean commitTx() throws Exception{
    persist(buff);
    buff.clear();
    return true;
  }

  @Override
  public boolean rollbackTx() throws Exception {
    // ANDREAS: so we never undo any writes. we just add txid to the excludes? That means we accumulate a lot of
    // invalid transactions, hence exclude lists become really large.
    buff.clear();
    return true;
  }

  @Override
  public OperationResult<Map<byte[], byte[]>> get(byte[] row, byte[][] columns) throws Exception {
    NavigableMap<byte[], byte[]> result = getRowMap(row, columns);
    return createOperationResult(result);
  }

  @Override
  public OperationResult<Map<byte[], byte[]>> get(byte[] row, byte[] startColumn, byte[] stopColumn, int limit)
    throws Exception {
    // checking if the row was deleted inside this tx
    NavigableMap<byte[], byte[]> buffCols = buff.get(row);
    boolean rowDeleted = buffCols == null && buff.containsKey(row);
    // ANDREAS: can this ever happen?
    if (rowDeleted) {
      return EMPTY_RESULT;
    }

    // NOTE: since we cannot tell the exact column set, we always have to go to persisted store.
    //       potential improvement: do not fetch columns available in in-mem buffer (we know them at this point)
    NavigableMap<byte[], byte[]> persistedCols = getPersisted(row, startColumn, stopColumn, limit);

    // adding server cols, and then overriding with buffered values
    NavigableMap<byte[], byte[]> result = Maps.newTreeMap(Bytes.BYTES_COMPARATOR);
    if (persistedCols != null) {
      result.putAll(persistedCols);
    }

    if (buffCols != null) {
      buffCols = getRange(buffCols, startColumn, stopColumn, limit);
      // null valued columns in in-memory buffer are deletes, so we need to delete them from the result list
      addOrDelete(result, buffCols);
    }

    // applying limit
    result = head(result, limit);

    return createOperationResult(result);
  }

  /**
   * NOTE: if value is null corresponded column is deleted. It will not be in result set when reading.
   *
   * Also see {@link OrderedColumnarTable#put(byte[], byte[][], byte[][])}.
   */
  @Override
  public void put(byte[] row, byte[][] columns, byte[][] values) throws Exception {
    NavigableMap<byte[], byte[]> colVals = buff.get(row);
    if (colVals == null) {
      colVals = Maps.newTreeMap(Bytes.BYTES_COMPARATOR);
      buff.put(row, colVals);
      // ANDREAS: is this thread-safe?
    }
    for (int i = 0; i < columns.length; i++) {
      colVals.put(columns[i], values[i]);
    }
  }

  @Override
  public void delete(byte[] row, byte[][] columns) throws Exception {
    // same as writing null for every column
    // ANDREAS: shouldn't this be DELETE_MARKER?
    put(row, columns, new byte[columns.length][]);
  }

  @Override
  public Map<byte[], Long> increment(byte[] row, byte[][] columns, long[] amounts) throws Exception {
    // Logic:
    // * fetching current values
    // * updating values
    // * updating in-memory store
    // * returning updated values as result
    // NOTE: there is more efficient way to do it, but for now we want more simple implementation, not over-optimizing
    NavigableMap<byte[], byte[]> rowMap = getRowMap(row, columns);
    byte[][] updatedValues = new byte[columns.length][];

    NavigableMap<byte[], Long> result = Maps.newTreeMap(Bytes.BYTES_COMPARATOR);
    for (int i = 0; i < columns.length; i++) {
      byte[] column = columns[i];
      byte[] val = rowMap.get(column);
      // converting to long
      long longVal;
      if (val == null) {
        longVal = 0L;
      } else {
        if (val.length != Bytes.SIZEOF_LONG) {
          throw new OperationException(StatusCode.ILLEGAL_INCREMENT,
                                       "Attempted to increment a value that is not convertible to long," +
                                         " row: " + Bytes.toStringBinary(row) +
                                         " column: " + Bytes.toStringBinary(column));
        }
        longVal = Bytes.toLong(val);
      }
      longVal += amounts[i];
      updatedValues[i] = Bytes.toBytes(longVal);
      result.put(column, longVal);
    }

    put(row, columns, updatedValues);

    return result;
  }

  @Override
  public boolean compareAndSwap(byte[] row, byte[] column, byte[] expectedValue, byte[] newValue) throws Exception {
    // NOTE: there is more efficient way to do it, but for now we want more simple implementation, not over-optimizing
    byte[][] columns = new byte[][]{column};
    byte[] currentValue = getRowMap(row, columns).get(column);
    if (Arrays.equals(expectedValue, currentValue)) {
      put(row, columns, new byte[][]{newValue});
      return true;
    }

    return false;
  }

  /**
   * Fallback implementation of getSplits, {@link SplitsUtil#primitiveGetSplits(int, byte[], byte[])}.
   * Ideally should be overridden by subclasses
   */
  @Override
  public List<Split> getSplits(int numSplits, byte[] start, byte[] stop) {
    List<KeyRange> keyRanges = SplitsUtil.primitiveGetSplits(numSplits, start, stop);
    return Lists.transform(keyRanges, new Function<KeyRange, Split>() {
      @Nullable
      @Override
      public Split apply(@Nullable KeyRange input) {
        return new RuntimeTable.TableSplit(input == null ? null : input.getStart(),
                                           input == null ? null : input.getStop());
      }
    });
  }

  @Override
  public Scanner scan(byte[] startRow, byte[] stopRow) throws Exception {
    // todo: merge with in-memory buffer
    return scanPersisted(startRow, stopRow);
  }

  private NavigableMap<byte[], byte[]> getRowMap(byte[] row, byte[][] columns) throws Exception {
    NavigableMap<byte[], byte[]> result = Maps.newTreeMap(Bytes.BYTES_COMPARATOR);
    // checking if the row was deleted inside this tx
    NavigableMap<byte[], byte[]> buffCols = buff.get(row);
    boolean rowDeleted = buffCols == null && buff.containsKey(row);
    if (rowDeleted) {
      return EMPTY_ROW_MAP;
    }

    // if nothing locally, return all from server
    if (buffCols == null) {
      return getPersisted(row, columns);
    }

    // otherwise try to fetch data from in-memory buffer. If not all present - fetch leftover from persisted
    List<byte[]> colsToFetchFromPersisted = Lists.newArrayList();
    // try to fetch from local buffer first and then from server if it is not in buffer
    for (byte[] column : columns) {
      if (!buffCols.containsKey(column)) {
        colsToFetchFromPersisted.add(column);
        continue;
      }

      byte[] val = buffCols.get(column);
      // if val == null it is a delete, skipping it (not placing in result and not fetching from persisted)
      if (val != null) {
        result.put(column, val);
      }
    }

    // fetching from server those that were not found in in-mem buffer
    if (colsToFetchFromPersisted.size() > 0) {
      NavigableMap<byte[], byte[]> persistedCols =
        getPersisted(row, colsToFetchFromPersisted.toArray(new byte[colsToFetchFromPersisted.size()][]));
      if (persistedCols != null) {
        result.putAll(persistedCols);
      }
    }
    return result;
  }

  private OperationResult<Map<byte[], byte[]>> createOperationResult(NavigableMap<byte[], byte[]> result) {
    if (result == null || result.isEmpty()) {
      return EMPTY_RESULT;
    }
    return new OperationResult<Map<byte[], byte[]>>(result);
  }

  // utilities useful for underlying implementations

  protected static NavigableMap<byte[], byte[]> getRange(NavigableMap<byte[], byte[]> rowMap,
                                                         byte[] startColumn, byte[] stopColumn,
                                                         int limit) {
    NavigableMap<byte[], byte[]> result;
    if (startColumn == null && stopColumn == null) {
      result = rowMap;
    } else if (startColumn == null) {
      result = rowMap.headMap(stopColumn, false);
    } else if (stopColumn == null) {
      result = rowMap.tailMap(startColumn, true);
    } else {
      result = rowMap.subMap(startColumn, true, stopColumn, false);
    }
    return head(result, limit);
  }

  protected static NavigableMap<byte[], byte[]> head(NavigableMap<byte[], byte[]> map, int count) {
    if (count > 0 && map.size() > count) {
      // todo: is there better way to do it?
      byte [] lastToInclude = null;
      int i = 0;
      for (Map.Entry<byte[], byte[]> entry : map.entrySet()) {
        lastToInclude = entry.getKey();
        if (++i >= count) {
          break;
        }
      }
      map = map.headMap(lastToInclude, true);
    }

    return map;
  }

  protected static void addOrDelete(NavigableMap<byte[], byte[]> dest, NavigableMap<byte[], byte[]> src) {
    // value == null means column was deleted
    for (Map.Entry<byte[], byte[]> keyVal : src.entrySet()) {
      if (keyVal.getValue() == null) {
        dest.remove(keyVal.getKey());
      } else {
        dest.put(keyVal.getKey(), keyVal.getValue());
      }
    }
  }
}
