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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.apache.tajo.catalog.Column;
import org.apache.tajo.catalog.Schema;
import sun.misc.Unsafe;

import java.util.List;

public class VecRowBlock {
  private static final Unsafe unsafe = UnsafeUtil.unsafe;

  private long fixedAreaPtr;
  private long fixedAreaSize = 0;
  private final int vectorSize;

  private final long [] nullVectorsAddrs;
  private final long [] vectorsAddrs;

  private long variableAreaSize = 0;
  private long [] currentPageAddrs;
  private long [] nextPtr;
  private List<Long> pageReferences;

  public VecRowBlock(Schema schema, int vectorSize) {
    this.vectorSize = vectorSize;

    nullVectorsAddrs = new long[schema.size()];
    vectorsAddrs = new long[schema.size()];

    boolean variableAreaNeededd = false;
    for (Column column : schema.getColumns()) {
      variableAreaNeededd |= !TypeUtil.isFixedSize(column.getDataType());
    }

    if (variableAreaNeededd) {
      currentPageAddrs = new long[schema.size()];
      nextPtr = new long[schema.size()];
      pageReferences = Lists.newArrayList();
    }

    init(schema, variableAreaNeededd);
  }

  public void allocateFixedArea() {
    fixedAreaPtr = unsafe.allocateMemory(fixedAreaSize);
    UnsafeUtil.unsafe.setMemory(fixedAreaPtr, fixedAreaSize, (byte) 0);
  }

  public long size() {
    return fixedAreaSize + variableAreaSize;
  }

  public long getFixedAreaSize() {
    return fixedAreaSize;
  }

  public long getVariableAreaSize() {
    return variableAreaSize;
  }

  public void init(Schema schema, boolean variableAreaInitNeeded) {
    long totalSize = 0;
    long eachNullHeaderBytes = computeNullHeaderSizePerColumn(vectorSize);
    long totalNullHeaderBytes = computeNullHeaderSize(vectorSize, schema.size());
    totalSize += totalNullHeaderBytes;

    for (int i = 0; i < schema.size(); i++) {
      Column column = schema.getColumn(i);
      totalSize += TypeUtil.sizeOf(column.getDataType()) * vectorSize;
    }
    fixedAreaSize = totalSize;
    allocateFixedArea();

    for (int i = 0; i < schema.size(); i++) {
      if (i == 0) {
        nullVectorsAddrs[i] = fixedAreaPtr;
      } else {
        nullVectorsAddrs[i] = nullVectorsAddrs[i - 1] + eachNullHeaderBytes;
      }
    }
    UnsafeUtil.bzero(nullVectorsAddrs[0], totalNullHeaderBytes);


    long perVecSize;
    for (int i = 0; i < schema.size(); i++) {
      if (i == 0) {
        vectorsAddrs[i] = nullVectorsAddrs[schema.size() - 1];
      } else {
        Column prevColumn = schema.getColumn(i - 1);
        perVecSize = (TypeUtil.sizeOf(prevColumn.getDataType()) * vectorSize);
        vectorsAddrs[i] = vectorsAddrs[i - 1] + perVecSize;
      }
    }

    if (variableAreaInitNeeded) {
      for (int i = 0; i < schema.size(); i++) {
        Column column = schema.getColumn(i);
        if (!TypeUtil.isFixedSize(column.getDataType())) {
          currentPageAddrs[i] = createPage();
          nextPtr[i] = currentPageAddrs[i];
        }
      }
    }
  }

  public long getValueVecPtr(int columnIdx) {
    return vectorsAddrs[columnIdx];
  }

  public long getNullVecPtr(int columnIdx) {
    return nullVectorsAddrs[columnIdx];
  }

  private static final int WORD_SIZE = SizeOf.SIZE_OF_LONG * 8;

  public void setNull(int columnIdx, int index) {
    Preconditions.checkArgument(index < vectorSize, "Index Out of Vectors");
    int chunkId = index / WORD_SIZE;
    long offset = index % WORD_SIZE;
    long address = nullVectorsAddrs[columnIdx] + (chunkId * 8);
    long nullFlagChunk = unsafe.getLong(address);
    nullFlagChunk = (nullFlagChunk | (1L << offset));
    unsafe.putLong(address, nullFlagChunk);
  }

  public int isNull(int columnIdx, int index) {
    Preconditions.checkArgument(index < vectorSize, "Index Out of Vectors");
    int chunkId = index / WORD_SIZE;
    long offset = index % WORD_SIZE;
    long address = nullVectorsAddrs[columnIdx] + (chunkId * 8);
    long nullFlagChunk = unsafe.getLong(address);
    return (int) ((nullFlagChunk >> offset) & 1L);
  }

  public short getInt2(int columnIdx, int index) {
    return unsafe.getShort(vectorsAddrs[columnIdx] + (index * SizeOf.SIZE_OF_SHORT));
  }

  public void putInt2(int columnIdx, int index, short val) {
    unsafe.putShort(vectorsAddrs[columnIdx] + (index * SizeOf.SIZE_OF_SHORT), val);
  }

  public void putInt4(int columnIdx, int index, int val) {
    unsafe.putInt(vectorsAddrs[columnIdx] + (index * SizeOf.SIZE_OF_INT), val);
  }

  public int getInt4(int columnIdx, int index) {
    return unsafe.getInt(vectorsAddrs[columnIdx] + (index * SizeOf.SIZE_OF_INT));
  }

  public void putInt8(int columnIdx, int index, int val) {
    unsafe.putInt(vectorsAddrs[columnIdx] + (index * SizeOf.SIZE_OF_LONG), val);
  }

  public int getInt8(int columnIdx, int index) {
    return unsafe.getInt(vectorsAddrs[columnIdx] + (index * SizeOf.SIZE_OF_LONG));
  }

  public void putFloat4(int columnIdx, int index, float val) {
    unsafe.putFloat(vectorsAddrs[columnIdx] + (index * SizeOf.SIZE_OF_FLOAT), val);
  }

  public float getFloat4(int columnIdx, int index) {
    return unsafe.getFloat(vectorsAddrs[columnIdx] + (index * SizeOf.SIZE_OF_FLOAT));
  }

  public void putFloat8(int columnIdx, int index, double val) {
    unsafe.putDouble(vectorsAddrs[columnIdx] + (index * SizeOf.SIZE_OF_DOUBLE), val);
  }

  public double getFloat8(int columnIdx, int index) {
    return unsafe.getDouble(vectorsAddrs[columnIdx] + (index * SizeOf.SIZE_OF_DOUBLE));
  }

  public void putText(int columnIdx, int index, byte [] val) {
    putText(columnIdx, index, val, 0, val.length);
  }

  public void putText(int columnIdx, int index, byte [] val, int offset, int length) {
    long currentPage = currentPageAddrs[columnIdx];

    long ptr = currentPage;
    long nextPtr = this.nextPtr[columnIdx];
    long usedMemory = nextPtr - ptr;

    if (PAGE_SIZE - usedMemory < val.length) { // create newly page
      ptr = createPage();
      currentPage = ptr;
      currentPageAddrs[columnIdx] = currentPage;
      nextPtr = currentPage;
    }

    unsafe.putShort(nextPtr, (short) val.length);
    nextPtr += SizeOf.SIZE_OF_SHORT;
    UnsafeUtil.bzero(nextPtr, length);
    unsafe.copyMemory(val, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, null, nextPtr, length);
    unsafe.putAddress(vectorsAddrs[columnIdx] + (Unsafe.ADDRESS_SIZE * index), nextPtr - SizeOf.SIZE_OF_SHORT);
    this.nextPtr[columnIdx] = nextPtr + length;
  }

  public int getText(int columnIdx, int index, int offset, int length, byte [] val) {
    long ptr = vectorsAddrs[columnIdx];
    long dataPtr = unsafe.getAddress(ptr + ((index) * Unsafe.ADDRESS_SIZE));

    int strLen = unsafe.getShort(dataPtr);
    dataPtr += SizeOf.SIZE_OF_SHORT;
    int actualLen = strLen < length ? strLen : length;
    unsafe.copyMemory(null, dataPtr, val, unsafe.ARRAY_BYTE_BASE_OFFSET + offset, actualLen);
    return actualLen;
  }

  public int getText(int columnIdx, int index, byte [] buf) {
    return getText(columnIdx, index, 0, buf.length, buf);
  }

  public String getString(int columnIdx, int index) {
    long ptr = vectorsAddrs[columnIdx];
    long dataPtr = unsafe.getAddress(ptr + ((index) * Unsafe.ADDRESS_SIZE));

    int strLen = unsafe.getShort(dataPtr);
    dataPtr += SizeOf.SIZE_OF_SHORT;

    byte [] bytes = new byte[strLen];
    unsafe.copyMemory(null, dataPtr, bytes, unsafe.ARRAY_BYTE_BASE_OFFSET, strLen);
    return new String(bytes);
  }

  public static final int PAGE_SIZE = 4096;

  /**
   * [next address, 0x0 last page. Otherwise, has next page (8 bytes) ] [PAGE payload (4096 by default) ]
   *
   * @return
   */
  private long createPage() {
    long pagePtr = unsafe.allocateMemory(PAGE_SIZE);
    pageReferences.add(pagePtr);
    variableAreaSize += PAGE_SIZE;
    return pagePtr;
  }

  public void free() {
    if (pageReferences != null) {
      for (long ref : pageReferences) {
        unsafe.freeMemory(ref);
      }
    }
    unsafe.freeMemory(fixedAreaPtr);
  }

  public static long allocateNullVector(int vectorSize) {
    long nBytes = computeNullHeaderSizePerColumn(vectorSize);
    long ptr = UnsafeUtil.alloc(nBytes);
    UnsafeUtil.bzero(ptr, nBytes);
    return ptr;
  }

  public static long computeNullHeaderSizePerColumn(int vectorSize) {
    return (long) Math.ceil(vectorSize / SizeOf.SIZE_OF_BYTE);
  }

  public static long computeNullHeaderSize(int vectorSize, int columnNum) {
    long eachNullHeaderBytes = (long) Math.ceil(vectorSize / SizeOf.SIZE_OF_BYTE);
    return eachNullHeaderBytes * columnNum;
  }
}
