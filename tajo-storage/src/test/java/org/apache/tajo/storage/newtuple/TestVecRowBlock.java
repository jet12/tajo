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

package org.apache.tajo.storage.newtuple;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.hadoop.util.StringUtils;
import org.apache.tajo.catalog.Schema;
import org.apache.tajo.datum.DatumFactory;
import org.apache.tajo.storage.Tuple;
import org.apache.tajo.storage.VTuple;
import org.apache.tajo.storage.newtuple.map.MapAddInt8ColInt8ColOp;
import org.apache.tajo.util.FileUtil;
import org.junit.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.apache.tajo.common.TajoDataTypes.Type;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestVecRowBlock {
  @Test
  public void testPutInt() {
    Schema schema = new Schema();
    schema.addColumn("col1", Type.INT2);
    schema.addColumn("col2", Type.INT4);
    schema.addColumn("col3", Type.INT8);
    schema.addColumn("col4", Type.FLOAT4);
    schema.addColumn("col5", Type.FLOAT8);

    int vecSize = 4096;

    long allocateStart = System.currentTimeMillis();
    VecRowBlock rowBlock = new VecRowBlock(schema, vecSize);
    long allocatedEnd = System.currentTimeMillis();
    System.out.println(FileUtil.humanReadableByteCount(rowBlock.size(), true) + " bytes allocated "
        + (allocatedEnd - allocateStart) + " msec");

    long writeStart = System.currentTimeMillis();
    for (int i = 0; i < vecSize; i++) {
      rowBlock.putInt2(0, i, (short) 1);
      rowBlock.putInt4(1, i, i);
      rowBlock.putInt8(2, i, i);
      rowBlock.putFloat4(3, i, i);
      rowBlock.putFloat8(4, i, i);
    }
    long writeEnd = System.currentTimeMillis();
    System.out.println(writeEnd - writeStart + " write msec");

    long readStart = System.currentTimeMillis();
    for (int i = 0; i < vecSize; i++) {
      assertTrue(1 == rowBlock.getInt2(0, i));
      assertEquals(i, rowBlock.getInt4(1, i));
      assertEquals(i, rowBlock.getInt8(2, i));
      assertTrue(i == rowBlock.getFloat4(3, i));
      assertTrue(i == rowBlock.getFloat8(4, i));
    }
    long readEnd = System.currentTimeMillis();
    System.out.println(readEnd - readStart + " read msec");

    rowBlock.destroy();
  }

  @Test
  public void testAddTest() {
    Schema schema = new Schema();
    schema.addColumn("col1", Type.INT2);
    schema.addColumn("col2", Type.INT4);
    schema.addColumn("col3", Type.INT8);
    schema.addColumn("col4", Type.FLOAT4);
    schema.addColumn("col5", Type.FLOAT8);

    int vecSize = 4096;

    long allocateStart = System.currentTimeMillis();
    VecRowBlock vecRowBlock = new VecRowBlock(schema, vecSize);
    long allocateend = System.currentTimeMillis();
    System.out.println(FileUtil.humanReadableByteCount(vecRowBlock.size(), true) + " bytes allocated "
        + (allocateend - allocateStart) + " msec");

    long writeStart = System.currentTimeMillis();
    for (int i = 0; i < vecSize; i++) {
      vecRowBlock.putInt2(0, i, (short) 1);
      vecRowBlock.putInt4(1, i, i);
      vecRowBlock.putInt8(2, i, i);
      vecRowBlock.putFloat4(3, i, i);
      vecRowBlock.putFloat8(4, i, i);
    }
    long writeEnd = System.currentTimeMillis();
    System.out.println(writeEnd - writeStart + " write msec");

    long readStart = System.currentTimeMillis();
    for (int i = 0; i < vecSize; i++) {
      assertTrue(1 == vecRowBlock.getInt2(0, i));
      assertEquals(i, vecRowBlock.getInt4(1, i));
      assertEquals(i, vecRowBlock.getInt8(2, i));
      assertTrue(i == vecRowBlock.getFloat4(3, i));
      assertTrue(i == vecRowBlock.getFloat8(4, i));
    }
    long readEnd = System.currentTimeMillis();
    System.out.println(readEnd - readStart + " read msec");

    MapAddInt8ColInt8ColOp op = new MapAddInt8ColInt8ColOp();

    long result = UnsafeUtil.alloc(Type.INT8, vecSize);
    op.map(vecSize, result, vecRowBlock.getVecAddress(2), vecRowBlock.getVecAddress(2), 0, 0);

    for (int i = 0; i < vecSize; i++) {
      System.out.println(UnsafeUtil.getLong(result, i));
    }
    vecRowBlock.destroy();
    UnsafeUtil.free(result);
  }

  @Test
  public void testSetNullIsNull() {
    Schema schema = new Schema();
    schema.addColumn("col1", Type.INT2);
    schema.addColumn("col2", Type.INT4);
    schema.addColumn("col3", Type.INT8);
    schema.addColumn("col4", Type.FLOAT4);
    schema.addColumn("col5", Type.FLOAT8);

    int vecSize = 4096;

    long allocateStart = System.currentTimeMillis();
    VecRowBlock vecRowBlock = new VecRowBlock(schema, vecSize);
    long allocateend = System.currentTimeMillis();
    System.out.println(FileUtil.humanReadableByteCount(vecRowBlock.size(), true) + " bytes allocated "
        + (allocateend - allocateStart) + " msec");

    long writeStart = System.currentTimeMillis();
    for (int i = 0; i < vecSize; i++) {
      vecRowBlock.putInt2(0, i, (short) 1);
      vecRowBlock.putInt4(1, i, i);
      vecRowBlock.putInt8(2, i, i);
      vecRowBlock.putFloat4(3, i, i);
      vecRowBlock.putFloat8(4, i, i);
    }
    long writeEnd = System.currentTimeMillis();
    System.out.println(writeEnd - writeStart + " write msec");

    List<Integer> nullIndices = Lists.newArrayList();
    Random rnd = new Random(System.currentTimeMillis());

    for (int i = 0; i < 500; i++) {
      int idx = rnd.nextInt(vecSize);
      nullIndices.add(idx);
      vecRowBlock.setNull(idx % 5, idx);

      assertTrue(vecRowBlock.isNull(idx % 5, idx) == 1);
    }

    for (int idx : nullIndices) {
      assertTrue(vecRowBlock.isNull(idx % 5, idx) == 1);
    }
    vecRowBlock.destroy();
  }

  @Test
  public void testNullify() {
    Schema schema = new Schema();
    schema.addColumn("col1", Type.INT2);
    schema.addColumn("col2", Type.INT4);
    schema.addColumn("col3", Type.INT8);
    schema.addColumn("col4", Type.FLOAT4);
    schema.addColumn("col5", Type.FLOAT8);

    int vecSize = 10000000;

    long allocateStart = System.currentTimeMillis();
    VecRowBlock vecRowBlock = new VecRowBlock(schema, vecSize);

    long allocateend = System.currentTimeMillis();
    System.out.println(FileUtil.humanReadableByteCount(vecRowBlock.size(), true) + " bytes allocated "
        + (allocateend - allocateStart) + " msec");

    long writeStart = System.currentTimeMillis();
    for (int i = 0; i < vecSize; i++) {
      vecRowBlock.putInt2(0, i, (short) 1);
      vecRowBlock.putInt4(1, i, i);
      vecRowBlock.putInt8(2, i, i);
      vecRowBlock.putFloat4(3, i, i);
      vecRowBlock.putFloat8(4, i, i);
    }
    long writeEnd = System.currentTimeMillis();
    System.out.println(writeEnd - writeStart + " write msec");

    List<Integer> nullIdx = Lists.newArrayList();
    Random rnd = new Random(System.currentTimeMillis());

    for (int i = 0; i < 100; i++) {
      int idx = rnd.nextInt(vecSize);
      System.out.println(idx);
      nullIdx.add(idx);
      vecRowBlock.setNull(0, idx);
      assertTrue(vecRowBlock.isNull(0, idx) == 1);
    }

    for (int i = 0; i < 100; i++) {
      int idx = rnd.nextInt(vecSize);
      nullIdx.add(idx);
      vecRowBlock.setNull(1, idx);
      assertTrue(vecRowBlock.isNull(1, idx) == 1);
    }

    for (int i = 0; i < vecSize; i++) {
      if (nullIdx.contains(i)) {
        assertTrue(vecRowBlock.isNull(0, i) == 1 || vecRowBlock.isNull(0, i) == 1);
      } else {
        assertTrue(vecRowBlock.isNull(0, i) == 0 && vecRowBlock.isNull(1, i) == 0);
      }
    }


//    Set<Integer> col1Nulls = Sets.newHashSet();
//    Set<Integer> col2Nulls = Sets.newHashSet();
//    Set<Integer> finalNulls = Sets.newHashSet();
//    for (int i = 0; i < vecSize; i++) {
//      if (vecRowBlock.isNull(0, i) == 1) {
//        col1Nulls.add(i);
//        finalNulls.add(i);
//      }
//
//      if (vecRowBlock.isNull(1, i) == 1) {
//        col2Nulls.add(i);
//        finalNulls.add(i);
//      }
//    }
//
//    long nullVector = UnsafeUtil.alloc(vecSize / 8 + 1);
//    VectorUtil.nullify(vecSize, nullVector, vecRowBlock.getVecAddress(0), vecRowBlock.getVecAddress(1));
//
//    for (int idx : finalNulls) {
//      System.out.println(">>>>>>>>" + idx);
//      assertTrue(VectorUtil.isNull(nullVector, idx) == 1);
//    }

    vecRowBlock.destroy();
  }

  //@Test
  public void testPutIntInTuple() {
    Schema schema = new Schema();
    schema.addColumn("col1", Type.INT4);
    schema.addColumn("col2", Type.INT4);

    int vecSize = 100000000;

    long allocateStart = System.currentTimeMillis();
    Tuple[] tuples = new Tuple[vecSize];
    for (int i = 0; i < tuples.length; i++) {
      tuples[i] = new VTuple(2);
    }
    long allocateend = System.currentTimeMillis();
    System.out.println((allocateend - allocateStart) + " msec");

    long writeStart = System.currentTimeMillis();
    for (int i = 0; i < vecSize; i++) {
      tuples[i].put(0, DatumFactory.createInt4(i));
      tuples[i].put(1, DatumFactory.createInt4(i));
    }
    long writeEnd = System.currentTimeMillis();
    System.out.println(writeEnd - writeStart + " write msec");

    long readStart = System.currentTimeMillis();
    long sum = 0;
    for (int i = 0; i < vecSize; i++) {
      assertEquals(i, tuples[i].getInt4(0));
      assertEquals(i, tuples[i].getInt4(1));
    }
    System.out.println(sum);
    long readEnd = System.currentTimeMillis();
    System.out.println(readEnd - readStart + " read msec");
  }
}