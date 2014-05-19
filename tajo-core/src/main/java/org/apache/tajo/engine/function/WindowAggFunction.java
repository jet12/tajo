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

package org.apache.tajo.engine.function;

import org.apache.tajo.catalog.Column;
import org.apache.tajo.catalog.json.CatalogGsonHelper;
import org.apache.tajo.catalog.proto.CatalogProtos;
import org.apache.tajo.common.TajoDataTypes.DataType;
import org.apache.tajo.datum.Datum;
import org.apache.tajo.exception.InvalidOperationException;
import org.apache.tajo.storage.Tuple;

public abstract class WindowAggFunction<T extends Datum> extends AggFunction<T> {

  public WindowAggFunction(Column[] definedArgs) {
    super(definedArgs);
  }

  public abstract FunctionContext newContext();

  public void eval(FunctionContext ctx, Tuple params) {
    throw new InvalidOperationException("Window function does not support eval()");
  }

  public abstract void merge(FunctionContext ctx, Tuple part);

  public Datum getPartialResult(FunctionContext ctx) {
    throw new InvalidOperationException("Window function does not support getPartialResult()");
  }

  public DataType getPartialResultType() {
    throw new InvalidOperationException("Window function does not support getPartialResultType()");
  }

  public abstract T terminate(FunctionContext ctx);

  @Override
  public String toJson() {
    return CatalogGsonHelper.toJson(this, WindowAggFunction.class);
  }

  @Override
  public CatalogProtos.FunctionType getFunctionType() {
    return CatalogProtos.FunctionType.WINDOW;
  }
}
