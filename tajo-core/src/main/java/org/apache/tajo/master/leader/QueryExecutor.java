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

package org.apache.tajo.master.leader;

import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.tajo.QueryId;
import org.apache.tajo.QueryIdFactory;
import org.apache.tajo.TajoConstants;
import org.apache.tajo.algebra.AlterTablespaceSetType;
import org.apache.tajo.annotation.Nullable;
import org.apache.tajo.catalog.*;
import org.apache.tajo.catalog.exception.*;
import org.apache.tajo.catalog.partition.PartitionMethodDesc;
import org.apache.tajo.catalog.proto.CatalogProtos;
import org.apache.tajo.catalog.statistics.TableStats;
import org.apache.tajo.client.QueryClient;
import org.apache.tajo.common.TajoDataTypes;
import org.apache.tajo.conf.TajoConf;
import org.apache.tajo.datum.DatumFactory;
import org.apache.tajo.engine.planner.physical.EvalExprExec;
import org.apache.tajo.engine.planner.physical.StoreTableExec;
import org.apache.tajo.engine.query.QueryContext;
import org.apache.tajo.ipc.ClientProtos;
import org.apache.tajo.master.NonForwardQueryResultScanner;
import org.apache.tajo.master.TajoMaster;
import org.apache.tajo.master.leader.prehook.DistributedQueryHook;
import org.apache.tajo.master.leader.prehook.DistributedQueryHookManager;
import org.apache.tajo.master.querymaster.Query;
import org.apache.tajo.master.querymaster.QueryInfo;
import org.apache.tajo.master.querymaster.QueryJobManager;
import org.apache.tajo.master.querymaster.QueryMasterTask;
import org.apache.tajo.master.session.Session;
import org.apache.tajo.plan.LogicalPlan;
import org.apache.tajo.plan.PlanningException;
import org.apache.tajo.plan.Target;
import org.apache.tajo.plan.expr.EvalNode;
import org.apache.tajo.plan.logical.*;
import org.apache.tajo.plan.util.PlannerUtil;
import org.apache.tajo.storage.*;
import org.apache.tajo.worker.TaskAttemptContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.apache.tajo.TajoConstants.DEFAULT_TABLESPACE_NAME;
import static org.apache.tajo.ipc.ClientProtos.SubmitQueryResponse;
import static org.apache.tajo.ipc.ClientProtos.SubmitQueryResponse.Builder;

public class QueryExecutor {
  private static final Log LOG = LogFactory.getLog(QueryExecutor.class);

  private final TajoMaster.MasterContext context;
  private final CatalogService catalog;
  private final StorageManager storageManager;
  private final DistributedQueryHookManager hookManager;

  public QueryExecutor(TajoMaster.MasterContext context, DistributedQueryHookManager hookManager) {
    this.context = context;
    this.catalog = context.getCatalog();
    this.storageManager = context.getStorageManager();
    this.hookManager = hookManager;
  }

  public void explainQuery(QueryContext queryContext, Session session, String query, LogicalPlan plan, Builder builder)
      throws IOException {

    String explainStr = PlannerUtil.buildExplainString(plan.getRootBlock().getRoot());
    Schema schema = new Schema();
    schema.addColumn("explain", TajoDataTypes.Type.TEXT);
    RowStoreUtil.RowStoreEncoder encoder = RowStoreUtil.createEncoder(schema);

    ClientProtos.SerializedResultSet.Builder serializedResBuilder = ClientProtos.SerializedResultSet.newBuilder();

    VTuple tuple = new VTuple(1);
    String[] lines = explainStr.split("\n");
    int bytesNum = 0;
    for (String line : lines) {
      tuple.put(0, DatumFactory.createText(line));
      byte [] encodedData = encoder.toBytes(tuple);
      bytesNum += encodedData.length;
      serializedResBuilder.addSerializedTuples(ByteString.copyFrom(encodedData));
    }
    serializedResBuilder.setSchema(schema.getProto());
    serializedResBuilder.setBytesNum(bytesNum);

    builder.setResultSet(serializedResBuilder.build());
    builder.setMaxRowNum(lines.length);
    builder.setResultCode(ClientProtos.ResultCode.OK);
    builder.setQueryId(QueryIdFactory.NULL_QUERY_ID.getProto());

    context.getQueryJobManager().createNewSimpleQuery(queryContext, session, query,
        (LogicalRootNode) plan.getRootBlock().getRoot());
  }

  public void executeSimpleQuery(QueryContext queryContext, Session session, String query, LogicalPlan plan,
                                 Builder responseBuilder) throws Exception {
    ScanNode scanNode = plan.getRootBlock().getNode(NodeType.SCAN);
    if (scanNode == null) {
      scanNode = plan.getRootBlock().getNode(NodeType.PARTITIONS_SCAN);
    }
    TableDesc desc = scanNode.getTableDesc();
    int maxRow = Integer.MAX_VALUE;
    if (plan.getRootBlock().hasNode(NodeType.LIMIT)) {
      LimitNode limitNode = plan.getRootBlock().getNode(NodeType.LIMIT);
      maxRow = (int) limitNode.getFetchFirstNum();
    }
    QueryId queryId = QueryIdFactory.newQueryId(context.getResourceManager().getSeedQueryId());

    NonForwardQueryResultScanner queryResultScanner =
        new NonForwardQueryResultScanner(context.getConf(), session.getSessionId(), queryId, scanNode, desc, maxRow);

    queryResultScanner.init();
    session.addNonForwardQueryResultScanner(queryResultScanner);

    responseBuilder.setQueryId(queryId.getProto());
    responseBuilder.setMaxRowNum(maxRow);
    responseBuilder.setTableDesc(desc.getProto());
    responseBuilder.setSessionVariables(session.getProto().getVariables());
    responseBuilder.setResultCode(ClientProtos.ResultCode.OK);

    context.getQueryJobManager().createNewSimpleQuery(queryContext, session, query,
        (LogicalRootNode) plan.getRootBlock().getRoot());
  }

  public void execNonFromQuery(QueryContext queryContext, Session session, String query,
                               LogicalPlan plan, Builder responseBuilder) throws Exception {

    LogicalRootNode rootNode = plan.getRootBlock().getRoot();
    Target[] targets = plan.getRootBlock().getRawTargets();
    if (targets == null) {
      throw new PlanningException("No targets");
    }
    final Tuple outTuple = new VTuple(targets.length);
    for (int i = 0; i < targets.length; i++) {
      EvalNode eval = targets[i].getEvalTree();
      outTuple.put(i, eval.eval(null, null));
    }
    boolean isInsert = rootNode.getChild() != null && rootNode.getChild().getType() == NodeType.INSERT;
    if (isInsert) {
      InsertNode insertNode = rootNode.getChild();
      insertNonFromQuery(context, queryContext, insertNode, responseBuilder);
    } else {
      Schema schema = PlannerUtil.targetToSchema(targets);
      RowStoreUtil.RowStoreEncoder encoder = RowStoreUtil.createEncoder(schema);
      byte[] serializedBytes = encoder.toBytes(outTuple);
      ClientProtos.SerializedResultSet.Builder serializedResBuilder = ClientProtos.SerializedResultSet.newBuilder();
      serializedResBuilder.addSerializedTuples(ByteString.copyFrom(serializedBytes));
      serializedResBuilder.setSchema(schema.getProto());
      serializedResBuilder.setBytesNum(serializedBytes.length);

      responseBuilder.setResultSet(serializedResBuilder);
      responseBuilder.setMaxRowNum(1);
      responseBuilder.setQueryId(QueryIdFactory.NULL_QUERY_ID.getProto());
      responseBuilder.setResultCode(ClientProtos.ResultCode.OK);
    }

    context.getQueryJobManager().createNewSimpleQuery(queryContext, session, query,
        (LogicalRootNode) plan.getRootBlock().getRoot());
  }

  private void insertNonFromQuery(TajoMaster.MasterContext context, QueryContext queryContext,
                                         InsertNode insertNode, Builder responseBuilder)
      throws Exception {
    CatalogService catalog = context.getCatalog();
    String nodeUniqName = insertNode.getTableName() == null ? insertNode.getPath().getName() : insertNode.getTableName();
    String queryId = nodeUniqName + "_" + System.currentTimeMillis();

    FileSystem fs = TajoConf.getWarehouseDir(context.getConf()).getFileSystem(context.getConf());
    Path stagingDir = QueryMasterTask.initStagingDir(context.getConf(), fs, queryId.toString());

    Path stagingResultDir = new Path(stagingDir, TajoConstants.RESULT_DIR_NAME);
    fs.mkdirs(stagingResultDir);

    TableDesc tableDesc = null;
    Path finalOutputDir = null;
    if (insertNode.getTableName() != null) {
      tableDesc = catalog.getTableDesc(insertNode.getTableName());
      finalOutputDir = tableDesc.getPath();
    } else {
      finalOutputDir = insertNode.getPath();
    }

    TaskAttemptContext taskAttemptContext =
        new TaskAttemptContext(queryContext, null, null, (CatalogProtos.FragmentProto[]) null, stagingDir);
    taskAttemptContext.setOutputPath(new Path(stagingResultDir, "part-01-000000"));

    EvalExprExec evalExprExec = new EvalExprExec(taskAttemptContext, (EvalExprNode) insertNode.getChild());
    StoreTableExec exec = new StoreTableExec(taskAttemptContext, insertNode, evalExprExec);
    try {
      exec.init();
      exec.next();
    } finally {
      exec.close();
    }

    if (insertNode.isOverwrite()) { // INSERT OVERWRITE INTO
      // it moves the original table into the temporary location.
      // Then it moves the new result table into the original table location.
      // Upon failed, it recovers the original table if possible.
      boolean movedToOldTable = false;
      boolean committed = false;
      Path oldTableDir = new Path(stagingDir, TajoConstants.INSERT_OVERWIRTE_OLD_TABLE_NAME);
      try {
        if (fs.exists(finalOutputDir)) {
          fs.rename(finalOutputDir, oldTableDir);
          movedToOldTable = fs.exists(oldTableDir);
        } else { // if the parent does not exist, make its parent directory.
          fs.mkdirs(finalOutputDir.getParent());
        }
        fs.rename(stagingResultDir, finalOutputDir);
        committed = fs.exists(finalOutputDir);
      } catch (IOException ioe) {
        // recover the old table
        if (movedToOldTable && !committed) {
          fs.rename(oldTableDir, finalOutputDir);
        }
      }
    } else {
      FileStatus[] files = fs.listStatus(stagingResultDir);
      for (FileStatus eachFile: files) {
        Path targetFilePath = new Path(finalOutputDir, eachFile.getPath().getName());
        if (fs.exists(targetFilePath)) {
          targetFilePath = new Path(finalOutputDir, eachFile.getPath().getName() + "_" + System.currentTimeMillis());
        }
        fs.rename(eachFile.getPath(), targetFilePath);
      }
    }

    if (insertNode.hasTargetTable()) {
      TableStats stats = tableDesc.getStats();
      long volume = Query.getTableVolume(context.getConf(), finalOutputDir);
      stats.setNumBytes(volume);
      stats.setNumRows(1);

      catalog.dropTable(insertNode.getTableName());
      catalog.createTable(tableDesc);

      responseBuilder.setTableDesc(tableDesc.getProto());
    } else {
      TableStats stats = new TableStats();
      long volume = Query.getTableVolume(context.getConf(), finalOutputDir);
      stats.setNumBytes(volume);
      stats.setNumRows(1);

      // Empty TableDesc
      List<CatalogProtos.ColumnProto> columns = new ArrayList<CatalogProtos.ColumnProto>();
      CatalogProtos.TableDescProto tableDescProto = CatalogProtos.TableDescProto.newBuilder()
          .setTableName(nodeUniqName)
          .setMeta(CatalogProtos.TableProto.newBuilder().setStoreType(CatalogProtos.StoreType.CSV).build())
          .setSchema(CatalogProtos.SchemaProto.newBuilder().addAllFields(columns).build())
          .setStats(stats.getProto())
          .build();

      responseBuilder.setTableDesc(tableDescProto);
    }

    // If queryId == NULL_QUERY_ID and MaxRowNum == -1, TajoCli prints only number of inserted rows.
    responseBuilder.setMaxRowNum(-1);
    responseBuilder.setQueryId(QueryIdFactory.NULL_QUERY_ID.getProto());
    responseBuilder.setResultCode(ClientProtos.ResultCode.OK);
  }

  public void executeDistributedQuery(QueryContext queryContext, Session session,
                                      LogicalPlan plan,
                                      String query,
                                      String jsonExpr,
                                      Builder builder) throws Exception {
    context.getSystemMetrics().counter("Query", "numDMLQuery").inc();

    LogicalRootNode rootNode = plan.getRootBlock().getRoot();

    hookManager.doHooks(context, queryContext, plan);

    QueryJobManager queryJobManager = this.context.getQueryJobManager();
    QueryInfo queryInfo;

    queryInfo = queryJobManager.createNewQueryJob(session, queryContext, query, jsonExpr, rootNode);

    if(queryInfo == null) {
      builder.setQueryId(QueryIdFactory.NULL_QUERY_ID.getProto());
      builder.setResultCode(ClientProtos.ResultCode.ERROR);
      builder.setErrorMessage("Fail starting QueryMaster.");
    } else {
      builder.setIsForwarded(true);
      builder.setQueryId(queryInfo.getQueryId().getProto());
      builder.setResultCode(ClientProtos.ResultCode.OK);
      if(queryInfo.getQueryMasterHost() != null) {
        builder.setQueryMasterHost(queryInfo.getQueryMasterHost());
      }
      builder.setQueryMasterPort(queryInfo.getQueryMasterClientPort());

      LOG.info("Query is forwarded to " + queryInfo.getQueryMasterHost() + ":" + queryInfo.getQueryMasterPort());
    }
  }
}
