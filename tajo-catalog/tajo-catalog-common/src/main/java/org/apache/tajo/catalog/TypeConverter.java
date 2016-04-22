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

package org.apache.tajo.catalog;

import com.google.common.collect.ImmutableList;
import org.apache.tajo.common.TajoDataTypes;
import org.apache.tajo.exception.NotImplementedException;
import org.apache.tajo.exception.TajoRuntimeException;
import org.apache.tajo.exception.UnsupportedException;
import org.apache.tajo.schema.IdentifierPolicy;
import org.apache.tajo.schema.Schema;
import org.apache.tajo.type.*;

import static org.apache.tajo.catalog.CatalogUtil.newDataTypeWithLen;
import static org.apache.tajo.type.Type.*;

public class TypeConverter {

  public static Type convert(TypeDesc type) {
    if (type.getDataType().getType() == TajoDataTypes.Type.RECORD) {
      ImmutableList.Builder<Schema.Field> fields = ImmutableList.builder();
      for (Column c : type.getNestedSchema().getRootColumns()) {
        fields.add(FieldConverter.convert(c));
      }
      return Struct(fields.build());
    } else {
      return convert(type.dataType);
    }
  }

  public static Type convert(TajoDataTypes.DataType legacyType) {
    switch (legacyType.getType()) {
    case NCHAR:
    case CHAR:
      return Char(legacyType.getLength());
    case NVARCHAR:
    case VARCHAR:
      return Varchar(legacyType.getLength());
    case NUMERIC:
      return Numeric(legacyType.getLength());
    case PROTOBUF:
      return new Protobuf(legacyType.getCode());
    default:
      return convert(legacyType.getType());
    }
  }

  /**
   * This is for base types.
   *
   * @param legacyBaseType legacy base type
   * @return Type
   */
  public static Type convert(TajoDataTypes.Type legacyBaseType) {
    switch (legacyBaseType) {
    case BOOLEAN:
      return Bool;
    case INT1:
      return Int1;
    case INT2:
      return Int2;
    case INT4:
      return Int4;
    case INT8:
      return Int8;
    case FLOAT4:
      return Float4;
    case FLOAT8:
      return Float8;
    case DATE:
      return Date;
    case TIME:
      return Time;
    case TIMESTAMP:
      return Timestamp;
    case INTERVAL:
      return Interval;
    case CHAR:
      return Char(1); // default len = 1
    case TEXT:
      return Text;
    case BLOB:
      return Blob;
    case INET4:
      return Inet4;
    case RECORD:
      // for better exception
      throw new TajoRuntimeException(new NotImplementedException("record projection"));
    case NULL_TYPE:
      return Null;
    case ANY:
      return Any;
    default:
      throw new TajoRuntimeException(new UnsupportedException(legacyBaseType.name()));
    }
  }

  public static TypeDesc convert(Schema.Field src) {
    return convert(src.type());
  }

  public static TypeDesc convert(Type type) {
    switch (type.baseType()) {
    case CHAR:
      Char charType = (Char) type;
      return new TypeDesc(newDataTypeWithLen(TajoDataTypes.Type.CHAR, charType.length()));
    case VARCHAR:
      Varchar varcharType = (Varchar) type;
      return new TypeDesc(newDataTypeWithLen(TajoDataTypes.Type.VARCHAR, varcharType.length()));
    case NUMERIC:
      Numeric numericType = (Numeric) type;
      return new TypeDesc(newDataTypeWithLen(TajoDataTypes.Type.NUMERIC, numericType.precision()));
    case PROTOBUF:
      Protobuf protobuf = (Protobuf) type;
      return new TypeDesc(CatalogUtil.newDataType(TajoDataTypes.Type.PROTOBUF, protobuf.getMessageName()));
    case RECORD:
      Struct struct = (Struct) type;
      ImmutableList.Builder<Column> fields = ImmutableList.builder();
      for (Schema.Field t: struct.fields()) {
        fields.add(new Column(t.name().raw(IdentifierPolicy.DefaultPolicy()), convert(t)));
      }
      return new TypeDesc(SchemaBuilder.builder().addAll(fields.build()).build());
    default:
      return new TypeDesc(CatalogUtil.newSimpleDataType(type.baseType()));
    }
  }
}
