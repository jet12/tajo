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

package org.apache.tajo.storage.pgsql;

import net.minidev.json.JSONObject;
import org.apache.tajo.catalog.MetadataProvider;
import org.apache.tajo.storage.TablespaceManager;
import org.apache.tajo.storage.mysql.JdbcTablespace;

import java.net.URI;

/**
 * <h3>URI Examples:</h3>
 * <ul>
 *   <li>jdbc:mysql//primaryhost,secondaryhost1,secondaryhost2/test?profileSQL=true</li>
 * </ul>
 */
public class PgSQLTablespace extends JdbcTablespace {
  private final String database;


  public PgSQLTablespace(String name, URI uri, JSONObject config) {
    super(name, uri, config);
    database = ((JSONObject)config.get(TablespaceManager.TABLESPACE_SPEC_CONFIGS_KEY)).getAsString("database");
  }

  public MetadataProvider getMetadataProvider() {
    return new PgSQLMetadataProvider(this, database);
  }
}
