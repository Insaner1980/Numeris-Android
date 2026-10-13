package com.emmah.numeris

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room3.useReaderConnection
import androidx.test.core.app.ApplicationProvider
import com.emmah.numeris.core.database.buildNumerisDatabase
import com.emmah.numeris.core.model.sourceIdFromStorage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseMigrationHostTest {
    @Test fun schemaOneUpgradeRemovesGa4AndPreservesEveryOtherTableAcrossReopen() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val path = context.getDatabasePath("numeris.db")
            context.deleteDatabase("numeris.db")
            path.parentFile!!.mkdirs()
            val schema =
                javaClass
                    .getResourceAsStream("/com.emmah.numeris.core.database.NumerisDatabase/1.json")!!
                    .bufferedReader()
                    .use {
                        Json
                            .parseToJsonElement(it.readText())
                            .jsonObject
                            .getValue("database")
                            .jsonObject
                    }
            val entities = schema.getValue("entities").jsonArray.map { it.jsonObject }
            val retainedTables =
                entities
                    .map { it.getValue("tableName").jsonPrimitive.content }
                    .filterNot { it.startsWith("google_analytics_") }
                    .toSet()
            createVersionOneDatabase(path, schema)
            try {
                repeat(2) {
                    val database = buildNumerisDatabase(context)
                    try {
                        assertNull(database.connectionDao().find("retired"))
                        assertEquals("fixture", database.connectionDao().find("fixture")?.source)
                        database.useReaderConnection { connection ->
                            val tables =
                                connection.usePrepared("SELECT name FROM sqlite_master WHERE type='table'") { rows ->
                                    buildSet { while (rows.step()) add(rows.getText(0)) }
                                }
                            assertTrue(tables.none { it.startsWith("google_analytics_") })
                            assertTrue(tables.containsAll(retainedTables))
                            retainedTables.forEach { table ->
                                connection.usePrepared("SELECT COUNT(*) FROM `$table`") { rows ->
                                    assertTrue(rows.step())
                                    assertEquals("Rows retained in $table", 1L, rows.getLong(0))
                                }
                            }
                            connection.usePrepared("PRAGMA user_version") { rows ->
                                assertTrue(rows.step())
                                assertEquals(2L, rows.getLong(0))
                            }
                        }
                    } finally {
                        database.close()
                    }
                }
                assertNull(sourceIdFromStorage("google_analytics"))
            } finally {
                context.deleteDatabase("numeris.db")
            }
        }

    private fun createVersionOneDatabase(
        path: java.io.File,
        schema: kotlinx.serialization.json.JsonObject,
    ) {
        val entities = schema.getValue("entities").jsonArray.map { it.jsonObject }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            entities.forEach { entity ->
                val table = entity.getValue("tableName").jsonPrimitive.content
                old.execSQL(
                    entity
                        .getValue("createSql")
                        .jsonPrimitive.content
                        .replace("\${TABLE_NAME}", table),
                )
                entity["indices"]?.jsonArray?.forEach { index ->
                    old.execSQL(
                        index.jsonObject
                            .getValue("createSql")
                            .jsonPrimitive.content
                            .replace("\${TABLE_NAME}", table),
                    )
                }
                val fields = entity.getValue("fields").jsonArray.map { it.jsonObject }
                val columns = fields.joinToString { "`${it.getValue("columnName").jsonPrimitive.content}`" }
                val values =
                    fields.joinToString {
                        if (it.getValue("affinity").jsonPrimitive.content == "TEXT") "'fixture'" else "1"
                    }
                old.execSQL("INSERT INTO `$table` ($columns) VALUES ($values)")
            }
            old.execSQL(
                "INSERT INTO connections(id,source,status) VALUES ('retired','google_analytics','configured')",
            )
            old.execSQL(
                "INSERT INTO sync_runs(id,source,startedAt,state,rowsWritten) " +
                    "VALUES ('retired','GoogleAnalytics','old','success',1)",
            )
            schema.getValue("setupQueries").jsonArray.forEach { old.execSQL(it.jsonPrimitive.content) }
            old.version = 1
        }
    }
}
