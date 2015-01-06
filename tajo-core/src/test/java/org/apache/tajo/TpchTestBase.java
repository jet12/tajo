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

package org.apache.tajo;

import com.google.common.collect.Maps;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tajo.benchmark.TPCH;
import org.apache.tajo.util.KeyValueSet;
import org.apache.tajo.catalog.Schema;
import org.apache.tajo.storage.StorageConstants;
import org.apache.tajo.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.ResultSet;
import java.util.Map;

public class TpchTestBase {
  private static final Log LOG = LogFactory.getLog(TpchTestBase.class);

  String [] names;
  String [] resourceNames;
  String [] paths;
  String [][] tables;
  Schema[] schemas;
  Map<String, Integer> nameMap = Maps.newHashMap();
  protected TPCH tpch;
  protected LocalTajoTestingUtility util;

  public static final String TPCH_SAMPLE_DATA_RESOURCE = "tpch/sample_data/";

  private static TpchTestBase testBase;

  static {
    try {
      testBase = new TpchTestBase();
      testBase.setUp();
    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
    }
  }

  public TpchTestBase() throws IOException, URISyntaxException {
    names = new String[] {"customer", "lineitem", "nation", "orders", "part", "partsupp", "region", "supplier", "empty_orders"};
    paths = new String[names.length];
    for (int i = 0; i < names.length; i++) {
      nameMap.put(names[i], i);
    }

    tpch = new TPCH();
    tpch.loadSchemas();
    tpch.loadQueries();

    schemas = new Schema[names.length];
    for (int i = 0; i < names.length; i++) {
      schemas[i] = tpch.getSchema(names[i]);
    }
    resourceNames = new String[names.length];

    tables = new String[names.length][];
    for (int i = 0; i < names.length; i++) {
      resourceNames[i] = TPCH_SAMPLE_DATA_RESOURCE + names[i] + ".tbl";
    }
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  public void setUp() throws Exception {
    util = new LocalTajoTestingUtility();
    KeyValueSet opt = new KeyValueSet();
    opt.set(StorageConstants.TEXT_DELIMITER, StorageConstants.DEFAULT_FIELD_DELIMITER);
    util.setup(names, resourceNames, schemas, opt);
  }

  public static TpchTestBase getInstance() {
    return testBase;
  }

  public ResultSet execute(String query) throws Exception {
    return util.execute(query);
  }

  public TajoTestingCluster getTestingCluster() {
    return util.getTestingCluster();
  }

  public void tearDown() throws IOException {
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
    }
    util.shutdown();
  }
}
