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
package org.apache.tajo.jdbc;

import com.google.common.collect.Lists;
import com.google.protobuf.ServiceException;
import org.apache.tajo.catalog.Schema;
import org.apache.tajo.client.TajoClient;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class TajoStatement implements Statement {
  private JdbcConnection conn;
  private TajoClient tajoClient;
  private int fetchSize = 200;

  /**
   * We need to keep a reference to the result set to support the following:
   * <code>
   * statement.execute(String sql);
   * statement.getResultSet();
   * </code>.
   */
  private ResultSet resultSet = null;

  /**
   * Add SQLWarnings to the warningChain if needed.
   */
  private SQLWarning warningChain = null;

  /**
   * Keep state so we can fail certain calls made after close().
   */
  private boolean isClosed = false;

  public TajoStatement(JdbcConnection conn, TajoClient tajoClient) {
    this.conn = conn;
    this.tajoClient = tajoClient;
  }

  @Override
  public void addBatch(String sql) throws SQLException {
    throw new SQLFeatureNotSupportedException("addBatch not supported");
  }

  @Override
  public void cancel() throws SQLException {
    throw new SQLFeatureNotSupportedException("cancel not supported");
  }

  @Override
  public void clearBatch() throws SQLException {
    throw new SQLFeatureNotSupportedException("clearBatch not supported");
  }

  @Override
  public void clearWarnings() throws SQLException {
    warningChain = null;
  }

  @Override
  public void close() throws SQLException {
    if (resultSet != null) {
      resultSet.close();
    }
    resultSet = null;
    isClosed = true;
  }

  public void closeOnCompletion() throws SQLException {
     // JDK 1.7
     throw new SQLFeatureNotSupportedException("closeOnCompletion not supported");
  }

  @Override
  public boolean execute(String sql) throws SQLException {
    resultSet = executeQuery(sql);

    return resultSet != null;
  }

  @Override
  public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
    throw new SQLFeatureNotSupportedException("execute not supported");
  }

  @Override
  public boolean execute(String sql, int[] columnIndexes) throws SQLException {
    throw new SQLFeatureNotSupportedException("execute not supported");
  }

  @Override
  public boolean execute(String sql, String[] columnNames) throws SQLException {
    throw new SQLFeatureNotSupportedException("execute not supported");
  }

  @Override
  public int[] executeBatch() throws SQLException {
    throw new SQLFeatureNotSupportedException("executeBatch not supported");
  }

  @Override
  public ResultSet executeQuery(String sql) throws SQLException {
    if (isClosed) {
      throw new SQLFeatureNotSupportedException("Can't execute after statement has been closed");
    }

    try {
      if (isSetVariableQuery(sql)) {
        return setSessionVariable(tajoClient, sql);
      } else if (isUnSetVariableQuery(sql)) {
        return unSetSessionVariable(tajoClient, sql);
      } else {
        return tajoClient.executeQueryAndGetResult(sql);
      }
    } catch (Exception e) {
      throw new SQLFeatureNotSupportedException(e.getMessage(), e);
    }
  }

  public static boolean isSetVariableQuery(String sql) {
    if (sql == null || sql.trim().isEmpty()) {
      return false;
    }

    return sql.trim().toLowerCase().startsWith("set");
  }

  public static boolean isUnSetVariableQuery(String sql) {
    if (sql == null || sql.trim().isEmpty()) {
      return false;
    }

    return sql.trim().toLowerCase().startsWith("unset");
  }

  public static ResultSet setSessionVariable(TajoClient client, String sql) throws SQLException {
    int index = sql.toLowerCase().indexOf("set");
    if (index < 0) {
      throw new SQLException("SET statement should be started 'SET' keyword: " + sql);
    }

    String[] tokens = sql.substring(index + 3).trim().split(" ");
    if (tokens.length != 2) {
      throw new SQLException("SET statement should be <KEY> <VALUE>: " + sql);
    }
    Map<String, String> variable = new HashMap<String, String>();
    variable.put(tokens[0].trim(), tokens[1].trim());
    try {
      client.updateSessionVariables(variable);
    } catch (ServiceException e) {
      throw new SQLException(e.getMessage(), e);
    }

    return new TajoMemoryResultSet(null, new Schema(), null, 0);
  }

  public static ResultSet unSetSessionVariable(TajoClient client, String sql) throws SQLException {
    int index = sql.toLowerCase().indexOf("unset");
    if (index < 0) {
      throw new SQLException("UNSET statement should be started 'UNSET' keyword: " + sql);
    }

    String key = sql.substring(index + 5).trim();
    if (key.isEmpty()) {
      throw new SQLException("UNSET statement should be <KEY>: " + sql);
    }
    try {
      client.unsetSessionVariables(Lists.newArrayList(key));
    } catch (ServiceException e) {
      throw new SQLException(e.getMessage(), e);
    }

    return new TajoMemoryResultSet(null, new Schema(), null, 0);
  }

  @Override
  public int executeUpdate(String sql) throws SQLException {
    try {
      tajoClient.executeQuery(sql);

      return 1;
    } catch (Exception ex) {
      throw new SQLFeatureNotSupportedException(ex.toString());
    }
  }

  @Override
  public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
    throw new SQLFeatureNotSupportedException("executeUpdate not supported");
  }

  @Override
  public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
    throw new SQLFeatureNotSupportedException("executeUpdate not supported");
  }

  @Override
  public int executeUpdate(String sql, String[] columnNames) throws SQLException {
    throw new SQLFeatureNotSupportedException("executeUpdate not supported");
  }

  @Override
  public Connection getConnection() throws SQLException {
    return conn;
  }

  @Override
  public int getFetchDirection() throws SQLException {
    throw new SQLFeatureNotSupportedException("getFetchDirection not supported");
  }

  @Override
  public int getFetchSize() throws SQLException {
    return fetchSize;
  }

  @Override
  public ResultSet getGeneratedKeys() throws SQLException {
    throw new SQLFeatureNotSupportedException("getGeneratedKeys not supported");
  }

  @Override
  public int getMaxFieldSize() throws SQLException {
    throw new SQLFeatureNotSupportedException("getMaxFieldSize not supported");
  }

  @Override
  public int getMaxRows() throws SQLException {
    throw new SQLFeatureNotSupportedException("getMaxRows not supported");
  }

  @Override
  public boolean getMoreResults() throws SQLException {
    throw new SQLFeatureNotSupportedException("getMoreResults not supported");
  }

  @Override
  public boolean getMoreResults(int current) throws SQLException {
    throw new SQLFeatureNotSupportedException("getMoreResults not supported");
  }

  @Override
  public int getQueryTimeout() throws SQLException {
    throw new SQLFeatureNotSupportedException("getQueryTimeout not supported");
  }

  @Override
  public ResultSet getResultSet() throws SQLException {
    return resultSet;
  }

  @Override
  public int getResultSetConcurrency() throws SQLException {
    throw new SQLFeatureNotSupportedException("getResultSetConcurrency not supported");
  }

  @Override
  public int getResultSetHoldability() throws SQLException {
    throw new SQLFeatureNotSupportedException("getResultSetHoldability not supported");
  }

  @Override
  public int getResultSetType() throws SQLException {
    throw new SQLFeatureNotSupportedException("getResultSetType not supported");
  }

  @Override
  public int getUpdateCount() throws SQLException {
    return 0;
  }

  @Override
  public SQLWarning getWarnings() throws SQLException {
    return warningChain;
  }

  @Override
  public boolean isClosed() throws SQLException {
    return isClosed;
  }

  public boolean isCloseOnCompletion() throws SQLException {
    // JDK 1.7
    throw new SQLFeatureNotSupportedException("isCloseOnCompletion not supported");
  }

  @Override
  public boolean isPoolable() throws SQLException {
    throw new SQLFeatureNotSupportedException("isPoolable not supported");
  }

  @Override
  public void setCursorName(String name) throws SQLException {
    throw new SQLFeatureNotSupportedException("setCursorName not supported");
  }

  /**
   * Not necessary.
   */
  @Override
  public void setEscapeProcessing(boolean enable) throws SQLException {}

  @Override
  public void setFetchDirection(int direction) throws SQLException {
    throw new SQLFeatureNotSupportedException("setFetchDirection not supported");
  }

  @Override
  public void setFetchSize(int rows) throws SQLException {
    fetchSize = rows;
  }

  @Override
  public void setMaxFieldSize(int max) throws SQLException {
    throw new SQLFeatureNotSupportedException("setMaxFieldSize not supported");
  }

  @Override
  public void setMaxRows(int max) throws SQLException {
    throw new SQLFeatureNotSupportedException("setMaxRows not supported");
  }

  @Override
  public void setPoolable(boolean poolable) throws SQLException {
    throw new SQLFeatureNotSupportedException("setPoolable not supported");
  }

  @Override
  public void setQueryTimeout(int seconds) throws SQLException {
    throw new SQLFeatureNotSupportedException("setQueryTimeout not supported");
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    throw new SQLFeatureNotSupportedException("isWrapperFor not supported");
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    throw new SQLFeatureNotSupportedException("unwrap not supported");
  }

}
