package com.quickbill.app.data

import com.google.gson.JsonParser
import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillDeletionTest {
    @Test
    fun deletingOneBillLeavesOtherBillsAndTheirNumbersIntact() = withDatabase { database ->
        val retained = database.insertBill(6000)
        val deleted = database.insertBill(12500)
        val retainedBill = database.rows("SELECT * FROM bills WHERE billNumber = $retained")
        val retainedLines = database.rows("SELECT * FROM bill_lines WHERE billNumber = $retained")

        database.prepareStatement(DELETE_BILL_SQL).use { statement ->
            statement.setLong(1, deleted)
            assertEquals(1, statement.executeUpdate())
        }

        assertEquals(retainedBill, database.rows("SELECT * FROM bills"))
        assertEquals(retainedLines, database.rows("SELECT * FROM bill_lines"))
        assertEquals(deleted + 1, database.insertBill(2500))
    }

    @Test
    fun clearHistoryLeavesFavouritesCustomersPaymentsAndShopIntact() = withDatabase { database ->
        database.execute("INSERT INTO favourites VALUES (1, 'Tea', 6000, 3, NULL)")
        database.execute("INSERT INTO customers VALUES (1, 'Rita', '9800000000')")
        database.execute("INSERT INTO payments VALUES (1, 1, 6500, 20)")
        database.execute("INSERT INTO shop_details VALUES ('Local Shop', '123456789', 1)")
        database.insertBill(6000)
        val latest = database.insertBill(12500, customerId = 1)
        val unrelatedTables = listOf("favourites", "customers", "payments", "shop_details")
        val snapshots = unrelatedTables.associateWith { database.rows("SELECT * FROM $it") }

        database.execute(CLEAR_HISTORY_SQL)

        assertTrue(database.rows("SELECT * FROM bills").isEmpty())
        assertTrue(database.rows("SELECT * FROM bill_lines").isEmpty())
        assertEquals(snapshots, unrelatedTables.associateWith { database.rows("SELECT * FROM $it") })
        assertEquals(latest + 1, database.insertBill(2500))
    }

    private fun withDatabase(test: (Connection) -> Unit) {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { database ->
            database.execute("PRAGMA foreign_keys = ON")
            val schema = checkNotNull(javaClass.getResourceAsStream("/com.quickbill.app.data.QuickBillDatabase/2.json"))
            schema.bufferedReader().use { reader ->
                val entities = JsonParser.parseReader(reader).asJsonObject
                    .getAsJsonObject("database").getAsJsonArray("entities")
                for (entry in entities) {
                    val entity = entry.asJsonObject
                    val sql = entity.get("createSql").asString.replace("\${TABLE_NAME}", entity.get("tableName").asString)
                    database.execute(sql)
                }
            }
            test(database)
        }
    }

    private fun Connection.insertBill(amountPaisa: Long, customerId: Long? = null): Long {
        execute(
            "INSERT INTO bills (shopName, shopPan, createdAtMillis, totalPaisa, customerId) " +
                "VALUES ('Local Shop', '123456789', 15, $amountPaisa, ${customerId ?: "NULL"})",
        )
        val billNumber = checkNotNull(rows("SELECT last_insert_rowid()").single().single()).toLong()
        execute("INSERT INTO bill_lines VALUES ($billNumber, 0, NULL, 'Tea', $amountPaisa, 1)")
        return billNumber
    }

    private fun Connection.execute(sql: String) {
        createStatement().use { statement -> statement.execute(sql) }
    }

    private fun Connection.rows(sql: String): List<List<String?>> = createStatement().use { statement ->
        statement.executeQuery(sql).use { result ->
            val columns = result.metaData.columnCount
            buildList {
                while (result.next()) add(List(columns) { column -> result.getString(column + 1) })
            }
        }
    }
}
