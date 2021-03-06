/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.sqlite.databaseConnection.jdbc

import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.model.RowIdName
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteTable
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.SequentialTaskExecutor
import java.sql.Connection
import java.sql.JDBCType
import java.util.concurrent.Executor

/**
 * Implementation of [DatabaseConnection] for a local Sqlite file using the JDBC driver.
 *
 * This class has a [SequentialTaskExecutor] with one thread, that should be used to make sure that
 * operations are executed sequentially, to avoid concurrency issues with the JDBC objects.
 */
class JdbcDatabaseConnection(
  private val connection: Connection,
  private val sqliteFile: VirtualFile,
  pooledExecutor: Executor
) : DatabaseConnection {
  companion object {
    private val logger: Logger = Logger.getInstance(JdbcDatabaseConnection::class.java)
  }

  val sequentialTaskExecutor = FutureCallbackExecutor.wrap(
    SequentialTaskExecutor.createSequentialApplicationPoolExecutor("Sqlite JDBC service", pooledExecutor)
  )

  override fun close(): ListenableFuture<Unit> = sequentialTaskExecutor.executeAsync {
    connection.close()
    logger.info("Successfully closed database: ${sqliteFile.path}")
  }

  override fun readSchema(): ListenableFuture<SqliteSchema> = sequentialTaskExecutor.executeAsync {
    val tables = connection.metaData.getTables(null, null, null, null)
    val sqliteTables = mutableListOf<SqliteTable>()
    while (tables.next()) {
      val columns = readColumnDefinitions(connection, tables.getString("TABLE_NAME"))
      // if the db has an integer primary key there's no need to use rowid.
      // otherwise we need to find the correct alias to use for the rowid column.
      val hasIntegerPrimaryKey = columns.any { it.inPrimaryKey && it.type == JDBCType.INTEGER }
      val rowIdName = when {
        hasIntegerPrimaryKey -> null
        columns.none { it.name == RowIdName._ROWID_.stringName } -> RowIdName._ROWID_
        columns.none { it.name == RowIdName.ROWID.stringName } -> RowIdName.ROWID
        columns.none { it.name == RowIdName.OID.stringName } -> RowIdName.OID
        else -> null
      }

      sqliteTables.add(
        SqliteTable(
          tables.getString("TABLE_NAME"),
          columns,
          rowIdName,
          isView = tables.getString("TABLE_TYPE") == "VIEW"
        )
      )
    }

    SqliteSchema(sqliteTables).apply { logger.info("Successfully read database schema: ${sqliteFile.path}") }
  }

  private fun readColumnDefinitions(connection: Connection, tableName: String): List<SqliteColumn> {
    val columnsSet = connection.metaData.getColumns(null, null, tableName, null)
    val keyColumnsNames = connection.getColumnNamesInPrimaryKey(tableName)

    return columnsSet.map {
      val columnName = columnsSet.getString("COLUMN_NAME")
      SqliteColumn(
        columnName,
        JDBCType.valueOf(columnsSet.getInt("DATA_TYPE")),
        keyColumnsNames.contains(columnName)
      )
    }.toList()
  }

  override fun execute(sqliteStatement: SqliteStatement): ListenableFuture<SqliteResultSet?> {
    return sequentialTaskExecutor.executeAsync {
      val preparedStatement = connection.resolvePreparedStatement(sqliteStatement)
      val hasResultSet = preparedStatement.execute().also {
        logger.info("SQL statement \"${sqliteStatement.sqliteStatementText}\" executed with success.")
      }

      if (hasResultSet) {
        JdbcSqliteResultSet(this, connection, sqliteStatement)
      } else {
        null
      }
    }
  }
}
