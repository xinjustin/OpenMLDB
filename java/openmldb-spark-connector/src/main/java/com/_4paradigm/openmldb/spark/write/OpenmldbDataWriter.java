/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com._4paradigm.openmldb.spark.write;

import com._4paradigm.openmldb.sdk.Schema;
import com._4paradigm.openmldb.sdk.SdkOption;
import com._4paradigm.openmldb.sdk.SqlException;
import com._4paradigm.openmldb.sdk.impl.SqlClusterExecutor;
import com.google.common.base.Preconditions;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.connector.write.DataWriter;
import org.apache.spark.sql.connector.write.WriterCommitMessage;

import java.io.IOException;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;

public class OpenmldbDataWriter implements DataWriter<InternalRow> {
    private final int partitionId;
    private final long taskId;
    private PreparedStatement preparedStatement = null;

    public OpenmldbDataWriter(OpenmldbWriteConfig config, int partitionId, long taskId) {
        try {
            SdkOption option = new SdkOption();
            option.setZkCluster(config.zkCluster);
            option.setZkPath(config.zkPath);
            SqlClusterExecutor executor = new SqlClusterExecutor(option);
            String dbName = config.dbName;
            String tableName = config.tableName;

            Schema schema = executor.getTableSchema(dbName, tableName);
            // create insert placeholder
            StringBuilder insert = new StringBuilder("insert into " + tableName + " values(?");
            for (int i = 1; i < schema.getColumnList().size(); i++) {
                insert.append(",?");
            }
            insert.append(");");
            preparedStatement = executor.getInsertPreparedStmt(dbName, insert.toString());
        } catch (SQLException | SqlException e) {
            e.printStackTrace();
        }

        this.partitionId = partitionId;
        this.taskId = taskId;
    }

    @Override
    public void write(InternalRow record) throws IOException {
        try {
            // record to openmldb row
            ResultSetMetaData metaData = preparedStatement.getMetaData();
            Preconditions.checkState(record.numFields() == metaData.getColumnCount());
            addRow(record, preparedStatement);
            preparedStatement.addBatch();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IOException(e.getMessage());
        }
    }

    private void addRow(InternalRow record, PreparedStatement preparedStatement) throws SQLException {
        // idx in preparedStatement starts from 1
        ResultSetMetaData metaData = preparedStatement.getMetaData();
        for (int i = 0; i < record.numFields(); i++) {
            // sqlType
            int type = metaData.getColumnType(i + 1);
            if (record.isNullAt(i)) {
                preparedStatement.setNull(i + 1, type);
                continue;
            }
            switch (type) {
                case Types.BOOLEAN:
                    preparedStatement.setBoolean(i + 1, record.getBoolean(i));
                    break;
                case Types.SMALLINT:
                    preparedStatement.setShort(i + 1, record.getShort(i));
                    break;
                case Types.INTEGER:
                    preparedStatement.setInt(i + 1, record.getInt(i));
                    break;
                case Types.BIGINT:
                    preparedStatement.setLong(i + 1, record.getLong(i));
                    break;
                case Types.FLOAT:
                    preparedStatement.setFloat(i + 1, record.getFloat(i));
                    break;
                case Types.DOUBLE:
                    preparedStatement.setDouble(i + 1, record.getDouble(i));
                    break;
                case Types.VARCHAR:
                    preparedStatement.setString(i + 1, record.getString(i));
                    break;
                case Types.DATE:
                    preparedStatement.setDate(i + 1, new Date(record.getInt(i)));
                    break;
                case Types.TIMESTAMP:
                    // record.getLong(i) gets us timestamp, and sql timestamp unit is ms.
                    preparedStatement.setTimestamp(i + 1, new Timestamp(record.getLong(i) / 1000));
                    break;
                default:
                    throw new RuntimeException("unsupported sql type " + type);
            }
        }
    }

    @Override
    public WriterCommitMessage commit() throws IOException {
        try {
            preparedStatement.executeBatch();
            preparedStatement.close();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IOException(e.getMessage());
        }
        // TODO(hw): need to return new WriterCommitMessageImpl(partitionId, taskId); ?
        return null;
    }

    @Override
    public void abort() throws IOException {
        try {
            preparedStatement.close();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {
        try {
            preparedStatement.close();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IOException(e.getMessage());
        }
    }
}
