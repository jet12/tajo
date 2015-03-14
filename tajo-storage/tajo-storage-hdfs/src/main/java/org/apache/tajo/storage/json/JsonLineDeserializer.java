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

package org.apache.tajo.storage.json;


import io.netty.buffer.ByteBuf;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.apache.tajo.catalog.*;
import org.apache.tajo.common.TajoDataTypes.Type;
import org.apache.tajo.common.exception.NotImplementedException;
import org.apache.tajo.datum.DatumFactory;
import org.apache.tajo.datum.NullDatum;
import org.apache.tajo.datum.TextDatum;
import org.apache.tajo.storage.Tuple;
import org.apache.tajo.storage.text.TextLineDeserializer;
import org.apache.tajo.storage.text.TextLineParsingError;

import java.io.IOException;
import java.util.Iterator;

public class JsonLineDeserializer extends TextLineDeserializer {
  private JSONParser parser;
  private Type[] types;
  private String [] columnNames;
  private String [] projectedPaths;

  public JsonLineDeserializer(Schema schema, TableMeta meta, Column [] projected) {
    super(schema, meta);

    projectedPaths = new String[projected.length];
    for (int i = 0; i < projected.length; i++) {
      this.projectedPaths[i] = projected[i].getSimpleName();
    }
  }

  @Override
  public void init() {
    types = SchemaUtil.toTypes(schema);
    columnNames = SchemaUtil.toSimpleNames(schema);

    parser = new JSONParser(JSONParser.MODE_JSON_SIMPLE | JSONParser.IGNORE_CONTROL_CHAR);
  }

  private static String makePath(String [] path, int depth) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i <= depth; i++) {
      sb.append(path[i]);
      if (i < depth) {
        sb.append(NestedPathUtil.PATH_DELIMITER);
      }
    }

    return sb.toString();
  }

  private void getValue(JSONObject object, String fullPath, String [] path, int depth, int actualIdx, Tuple output) throws IOException {
    String fieldName = path[depth];

    if (!object.containsKey(fieldName)) {
      output.put(actualIdx, NullDatum.get());
    }

    String name;
    if (depth > 0) {
      name = makePath(path, depth);
    } else {
      name = path[0];
    }

    switch (schema.getColumn(name).getDataType().getType()) {
    case BOOLEAN:
      String boolStr = object.getAsString(fieldName);
      if (boolStr != null) {
        output.put(actualIdx, DatumFactory.createBool(boolStr.equals("true")));
      } else {
        output.put(actualIdx, NullDatum.get());
      }
      break;
    case CHAR:
      String charStr = object.getAsString(fieldName);
      if (charStr != null) {
        output.put(actualIdx, DatumFactory.createChar(charStr));
      } else {
        output.put(actualIdx, NullDatum.get());
      }
      break;
    case INT1:
    case INT2:
      Number int2Num = object.getAsNumber(fieldName);
      if (int2Num != null) {
        output.put(actualIdx, DatumFactory.createInt2(int2Num.shortValue()));
      } else {
        output.put(actualIdx, NullDatum.get());
      }
      break;
    case INT4:
      Number int4Num = object.getAsNumber(fieldName);
      if (int4Num != null) {
        output.put(actualIdx, DatumFactory.createInt4(int4Num.intValue()));
      } else {
        output.put(actualIdx, NullDatum.get());
      }
      break;
    case INT8:
      Number int8Num = object.getAsNumber(fieldName);
      if (int8Num != null) {
        output.put(actualIdx, DatumFactory.createInt8(int8Num.longValue()));
      } else {
        output.put(actualIdx, NullDatum.get());
      }
      break;
    case FLOAT4:
      Number float4Num = object.getAsNumber(fieldName);
      if (float4Num != null) {
        output.put(actualIdx, DatumFactory.createFloat4(float4Num.floatValue()));
      } else {
        output.put(actualIdx, NullDatum.get());
      }
      break;
    case FLOAT8:
      Number float8Num = object.getAsNumber(fieldName);
      if (float8Num != null) {
        output.put(actualIdx, DatumFactory.createFloat8(float8Num.doubleValue()));
      } else {
        output.put(actualIdx, NullDatum.get());
      }
      break;
    case TEXT:
      String textStr = object.getAsString(fieldName);
      if (textStr != null) {
        output.put(actualIdx, DatumFactory.createText(textStr));
      } else {
        output.put(actualIdx, NullDatum.get());
      }
      break;
    case TIMESTAMP:
      String timestampStr = object.getAsString(fieldName);
      if (timestampStr != null) {
        output.put(actualIdx, DatumFactory.createTimestamp(timestampStr));
      } else {
        output.put(actualIdx, NullDatum.get());
      }
      break;
    case TIME:
      String timeStr = object.getAsString(fieldName);
      if (timeStr != null) {
        output.put(actualIdx, DatumFactory.createTime(timeStr));
      } else {
        output.put(actualIdx, NullDatum.get());
      }
      break;
    case DATE:
      String dateStr = object.getAsString(fieldName);
      if (dateStr != null) {
        output.put(actualIdx, DatumFactory.createDate(dateStr));
      } else {
        output.put(actualIdx, NullDatum.get());
      }
      break;
    case BIT:
    case BINARY:
    case VARBINARY:
    case BLOB: {
      Object jsonObject = object.get(fieldName);

      if (jsonObject == null) {
        output.put(actualIdx, NullDatum.get());
        break;
      }
      if (jsonObject instanceof String) {
        output.put(actualIdx, DatumFactory.createBlob((String) jsonObject));
      } else if (jsonObject instanceof JSONArray) {
        JSONArray jsonArray = (JSONArray) jsonObject;
        byte[] bytes = new byte[jsonArray.size()];
        Iterator<Object> it = jsonArray.iterator();
        int arrayIdx = 0;
        while (it.hasNext()) {
          bytes[arrayIdx++] = ((Long) it.next()).byteValue();
        }
        if (bytes.length > 0) {
          output.put(actualIdx, DatumFactory.createBlob(bytes));
        } else {
          output.put(actualIdx, NullDatum.get());
        }
        break;
      } else {
        throw new IOException("Unknown json object: " + object.getClass().getSimpleName());
      }
      break;
    }
    case INET4:
      String inetStr = object.getAsString(fieldName);
      if (inetStr != null) {
        output.put(actualIdx, DatumFactory.createInet4(inetStr));
      } else {
        output.put(actualIdx, NullDatum.get());
      }
      break;

    case RECORD:
      JSONObject nestedObject = (JSONObject) object.get(fieldName);
      if (nestedObject != null) {
        getValue(nestedObject, fullPath, path, depth + 1, actualIdx, output);
      } else {
        output.put(actualIdx, NullDatum.get());
      }
      break;

    case NULL_TYPE:
      output.put(actualIdx, NullDatum.get());
      break;

    default:
      throw new NotImplementedException(types[actualIdx].name() + " is not supported.");
    }
  }

  @Override
  public void deserialize(ByteBuf buf, Tuple output) throws IOException, TextLineParsingError {
    byte[] line = new byte[buf.readableBytes()];
    buf.readBytes(line);

    JSONObject object;
    try {
      object = (JSONObject) parser.parse(line);
    } catch (ParseException pe) {
      throw new TextLineParsingError(new String(line, TextDatum.DEFAULT_CHARSET), pe);
    } catch (ArrayIndexOutOfBoundsException ae) {
      // truncated value
      throw new TextLineParsingError(new String(line, TextDatum.DEFAULT_CHARSET), ae);
    }

    for (int i = 0; i < projectedPaths.length; i++) {
      int actualIdx = i;
      String fieldName = projectedPaths[i];

      String [] paths = fieldName.split(NestedPathUtil.PATH_DELIMITER);

      getValue(object, fieldName, paths, 0, actualIdx, output);
    }
  }

  @Override
  public void release() {
  }
}
