/*
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

package org.apache.tajo.client;

import com.google.common.collect.Maps;
import org.apache.tajo.error.Errors;
import org.apache.tajo.error.Errors.ResultCode;
import org.apache.tajo.ipc.ClientProtos.ResponseState;

import java.sql.SQLException;
import java.util.Map;

public class SQLExceptionUtil {

  private static final Map<ResultCode, String> SQLSTATES = Maps.newHashMap();

  static {
    SQLSTATES.put(ResultCode.SYNTAX_ERROR, "42601");
  }

  public static void throwIfError(ResponseState state) throws SQLException {
    if (ClientErrorUtil.isError(state)) {
      throw convert(state);
    }
  }

  public static SQLException convert(ResponseState state) throws SQLException {
    return new SQLException(
        state.getMessage(),
        SQLSTATES.get(state.getReturnCode()),
        state.getReturnCode().getNumber());
  }
}