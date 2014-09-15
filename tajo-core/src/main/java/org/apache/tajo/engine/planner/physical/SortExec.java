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

package org.apache.tajo.engine.planner.physical;

import org.apache.tajo.SessionVars;
import org.apache.tajo.storage.BaseTupleComparator;
import org.apache.tajo.worker.TaskAttemptContext;
import org.apache.tajo.catalog.Schema;
import org.apache.tajo.catalog.SortSpec;
import org.apache.tajo.storage.Tuple;

import java.io.IOException;
import java.util.Comparator;

public abstract class SortExec extends UnaryPhysicalExec {
  private final Comparator<Tuple> unsafeComparator;
  private final SortSpec [] sortSpecs;

  public SortExec(TaskAttemptContext context, Schema inSchema,
                  Schema outSchema, PhysicalExec child, SortSpec [] sortSpecs) {
    super(context, inSchema, outSchema, child);
    this.sortSpecs = sortSpecs;

    BaseTupleComparator comp = new BaseTupleComparator(inSchema, sortSpecs);
    if (context.getQueryContext().getBool(SessionVars.CODEGEN)) {
      this.unsafeComparator = context.getSharedResource().getUnSafeComparator(inSchema, comp);
    } else {
      this.unsafeComparator = comp;
    }
  }

  public SortSpec[] getSortSpecs() {
    return sortSpecs;
  }

  public Comparator<Tuple> getUnSafeComparator() {
    return unsafeComparator;
  }

  public Comparator<Tuple> getComparator() {
    return unsafeComparator;
  }

  @Override
  abstract public Tuple next() throws IOException;
}
