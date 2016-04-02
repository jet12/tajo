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

import org.apache.tajo.catalog.json.CatalogGsonHelper;
import org.apache.tajo.catalog.proto.CatalogProtos.SchemaProto;
import org.apache.tajo.common.TajoDataTypes.Type;
import org.apache.tajo.exception.TajoRuntimeException;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class TestSchema {
	
	Schema schema;
	Column col1;
	Column col2;
	Column col3;

  public static final Schema nestedSchema1;
  public static final Schema nestedSchema2;
  public static final Schema nestedSchema3;

  static {
    // simple nested schema
    SchemaBuilder builder1 = new SchemaBuilder();
    builder1.add(new Column("s1", Type.INT8));

    Schema nestedRecordSchema = SchemaFactory.newV1();
    nestedRecordSchema.addColumn("s2", Type.FLOAT4);
    nestedRecordSchema.addColumn("s3", Type.TEXT);

    Column nestedField = new Column("s4", new TypeDesc(nestedRecordSchema));
    builder1.add(nestedField);

    builder1.add(new Column("s5", Type.FLOAT8));
    nestedSchema1 = builder1.buildV1();
    // two level nested schema
    //
    // s1
    //  |- s2
    //  |- s4
    //     |- s3
    //     |- s4
    //  |- s5
    //  |- s8
    //     |- s6
    //     |- s7
    SchemaBuilder builder2 = new SchemaBuilder();
    //nestedSchema2 = SchemaFactory.newV1();
    builder2.add(new Column("s1", Type.INT8));

    Schema nestedRecordSchema1 = SchemaFactory.newV1();
    nestedRecordSchema1.addColumn("s2", Type.FLOAT4);
    nestedRecordSchema1.addColumn("s3", Type.TEXT);

    Column nestedField1 = new Column("s4", new TypeDesc(nestedRecordSchema1));
    builder2.add(nestedField1);

    builder2.add(new Column("s5", Type.FLOAT8));

    Schema nestedRecordSchema2 = SchemaFactory.newV1();
    nestedRecordSchema2.addColumn("s6", Type.FLOAT4);
    nestedRecordSchema2.addColumn("s7", Type.TEXT);

    Column nestedField2 = new Column("s8", new TypeDesc(nestedRecordSchema2));
    builder2.add(nestedField2);
    nestedSchema2 = builder2.buildV1();

    // three level nested schema
    //
    // s1
    //  |- s2
    //  |- s3
    //      |- s4
    //      |- s7
    //          |- s5
    //              |- s6
    //      |- s8
    //  |- s9

    SchemaBuilder builder3 = new SchemaBuilder();

    builder3.add(new Column("s1", Type.INT8));
    builder3.add(new Column("s2", Type.INT8));

    Schema s5 = SchemaFactory.newV1();
    s5.addColumn("s6", Type.INT8);

    Schema s7 = SchemaFactory.newV1();
    s7.addColumn("s5", new TypeDesc(s5));

    Schema s3 = SchemaFactory.newV1();
    s3.addColumn("s4", Type.INT8);
    s3.addColumn("s7", new TypeDesc(s7));
    s3.addColumn("s8", Type.INT8);

    builder3.add(new Column("s3", new TypeDesc(s3)));
    builder3.add(new Column("s9", Type.INT8));
    nestedSchema3 = builder3.buildV1();
  }

	@Before
	public void setUp() throws Exception {
		schema = SchemaFactory.newV1();
		col1 = new Column("name", Type.TEXT);
		schema.addColumn(col1);
		col2 = new Column("age", Type.INT4);
		schema.addColumn(col2);
		col3 = new Column("addr", Type.TEXT);
		schema.addColumn(col3);
	}

	@Test
	public final void testSchemaSchema() {
		Schema schema2 = SchemaFactory.newV1(schema);
		
		assertEquals(schema, schema2);
	}

	@Test
	public final void testSchemaSchemaProto() {
		Schema schema2 = SchemaFactory.newV1(schema.getProto());
		
		assertEquals(schema, schema2);
	}

	@Test
	public final void testGetColumnString() {
		assertEquals(col1, schema.getColumn("name"));
		assertEquals(col2, schema.getColumn("age"));
		assertEquals(col3, schema.getColumn("addr"));
	}

	@Test
	public final void testAddField() {
		Schema schema = SchemaFactory.newV1();
		assertFalse(schema.containsByQualifiedName("studentId"));
		schema.addColumn("studentId", Type.INT4);
		assertTrue(schema.containsByQualifiedName("studentId"));
	}

	@Test
	public final void testEqualsObject() {
		Schema schema2 = SchemaFactory.newV1();
		schema2.addColumn("name", Type.TEXT);
		schema2.addColumn("age", Type.INT4);
		schema2.addColumn("addr", Type.TEXT);
		
		assertEquals(schema, schema2);
	}

	@Test
	public final void testGetProto() {
		SchemaProto proto = schema.getProto();
		
		assertEquals("name", proto.getFields(0).getName());
		assertEquals("age", proto.getFields(1).getName());
		assertEquals("addr", proto.getFields(2).getName());
	}
	
	@Test
	public final void testClone() throws CloneNotSupportedException {
	  Schema schema = SchemaFactory.newV1();
	  schema.addColumn("abc", Type.FLOAT8);
	  schema.addColumn("bbc", Type.FLOAT8);
	  
	  Schema schema2 = SchemaFactory.newV1(schema.getProto());
	  assertEquals(schema.getProto(), schema2.getProto());
	  assertEquals(schema.getColumn(0), schema2.getColumn(0));
	  assertEquals(schema.size(), schema2.size());
	  
	  Schema schema3 = (Schema) schema.clone();
	  assertEquals(schema.getProto(), schema3.getProto());
    assertEquals(schema.getColumn(0), schema3.getColumn(0));
    assertEquals(schema.size(), schema3.size());
	}
	
	@Test(expected = TajoRuntimeException.class)
	public final void testAddExistColumn() {
    Schema schema = SchemaFactory.newV1();
    schema.addColumn("abc", Type.FLOAT8);
    schema.addColumn("bbc", Type.FLOAT8);
    schema.addColumn("abc", Type.INT4);
	}

	@Test
	public final void testJson() {
		Schema schema2 = SchemaFactory.newV1(schema.getProto());
		String json = schema2.toJson();
		Schema fromJson = CatalogGsonHelper.fromJson(json, SchemaLegacy.class);
		assertEquals(schema2, fromJson);
    assertEquals(schema2.getProto(), fromJson.getProto());
	}

  @Test
  public final void testProto() {
    Schema schema2 = SchemaFactory.newV1(schema.getProto());
    SchemaProto proto = schema2.getProto();
    Schema fromProto = SchemaFactory.newV1(proto);
    assertEquals(schema2, fromProto);
  }

  @Test
  public final void testSetQualifier() {
    Schema schema2 = SchemaFactory.newV1(schema.getProto());
    schema2.setQualifier("test1");
    Column column = schema2.getColumn(1);
    assertEquals(1, schema2.getColumnIdByName("age"));
    assertEquals(column, schema2.getColumn("age"));
    assertEquals(column, schema2.getColumn("test1.age"));

    Schema schema3 = SchemaFactory.newV1();
    schema3.addColumn("tb1.col1", Type.INT4);
    schema3.addColumn("col2", Type.INT4);
    assertEquals("tb1", schema3.getColumn(0).getQualifier());
    assertEquals("tb1.col1", schema3.getColumn(0).getQualifiedName());
    assertEquals("col1", schema3.getColumn(0).getSimpleName());
    assertEquals("col2", schema3.getColumn(1).getQualifiedName());

    assertEquals(schema3.getColumn(0), schema3.getColumn("col1"));
    assertEquals(schema3.getColumn(0), schema3.getColumn("tb1.col1"));
    assertEquals(schema3.getColumn(1), schema3.getColumn("col2"));
    assertEquals(schema3.getColumn(1), schema3.getColumn("col2"));

    schema3.setQualifier("tb2");
    assertEquals("tb2", schema3.getColumn(0).getQualifier());
    assertEquals("tb2.col1", schema3.getColumn(0).getQualifiedName());
    assertEquals("col1", schema3.getColumn(0).getSimpleName());
    assertEquals("tb2.col2", schema3.getColumn(1).getQualifiedName());

    assertEquals(schema3.getColumn(0), schema3.getColumn("col1"));
    assertEquals(schema3.getColumn(0), schema3.getColumn("tb2.col1"));
    assertEquals(schema3.getColumn(1), schema3.getColumn("col2"));
    assertEquals(schema3.getColumn(1), schema3.getColumn("tb2.col2"));
  }

  @Test
  public void testNestedRecord1() {
    verifySchema(nestedSchema1);
  }

  @Test
  public void testNestedRecord2() {
    verifySchema(nestedSchema2);
  }

  @Test
  public void testNestedRecord3() {
    verifySchema(nestedSchema3);
  }

  @Test
  public void testNestedRecord4() {
    Schema root = SchemaFactory.newV1();

    Schema nf2DotNf1 = SchemaFactory.newV1();
    nf2DotNf1.addColumn("f1", Type.INT8);
    nf2DotNf1.addColumn("f2", Type.INT8);

    Schema nf2DotNf2 = SchemaFactory.newV1();
    nf2DotNf2.addColumn("f1", Type.INT8);
    nf2DotNf2.addColumn("f2", Type.INT8);

    Schema nf2 = SchemaFactory.newV1();
    nf2.addColumn("f1", Type.INT8);
    nf2.addColumn("nf1", new TypeDesc(nf2DotNf1));
    nf2.addColumn("nf2", new TypeDesc(nf2DotNf2));
    nf2.addColumn("f2", Type.INT8);

    root.addColumn("f1", Type.INT8);
    root.addColumn("nf1", Type.INT8);
    root.addColumn("nf2", new TypeDesc(nf2));
    root.addColumn("f2", Type.INT8);

    verifySchema(root);
  }

  public static void verifySchema(Schema s1) {
    assertEquals(s1, s1);

    SchemaProto proto = s1.getProto();
    assertEquals("Proto (de)serialized schema is different from the original: ", s1, SchemaFactory.newV1(proto));

    Schema cloned = null;
    try {
      cloned = (Schema) s1.clone();
    } catch (CloneNotSupportedException e) {
      fail("Clone is failed");
    }

    assertEquals("Cloned schema is different from the original one:", s1, cloned);
  }
}
