package com.quickbill.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [FavouriteItem::class, ShopDetails::class, Bill::class, BillLine::class, Customer::class, Payment::class],
    version = 2,
    exportSchema = true,
)
abstract class QuickBillDatabase : RoomDatabase() {
    abstract fun dao(): QuickBillDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE `customers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `phone` TEXT
                    )""".trimIndent(),
                )
                db.execSQL("ALTER TABLE `bills` ADD COLUMN `customerId` INTEGER REFERENCES `customers` (`id`)")
                db.execSQL("ALTER TABLE `bills` ADD COLUMN `customerName` TEXT")
                db.execSQL("CREATE INDEX `index_bills_customerId` ON `bills` (`customerId`)")
                db.execSQL(
                    """CREATE TABLE `payments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `customerId` INTEGER NOT NULL,
                        `amountPaisa` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        FOREIGN KEY (`customerId`) REFERENCES `customers` (`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )""".trimIndent(),
                )
                db.execSQL("CREATE INDEX `index_payments_customerId` ON `payments` (`customerId`)")
            }
        }

        fun create(context: Context): QuickBillDatabase = Room.databaseBuilder(
            context.applicationContext,
            QuickBillDatabase::class.java,
            "quick-bill.db",
        ).addMigrations(MIGRATION_1_2).build()
    }
}

@Dao
interface QuickBillDao {
    @Query("SELECT * FROM favourites ORDER BY timesUsed DESC, id ASC")
    fun observeFavourites(): Flow<List<FavouriteItem>>

    @Insert
    suspend fun insertFavourite(favourite: FavouriteItem): Long

    @Query("UPDATE favourites SET name = :name, unitPricePaisa = :unitPricePaisa WHERE id = :id")
    suspend fun updateFavourite(id: Long, name: String, unitPricePaisa: Long): Int

    @Query("DELETE FROM favourites WHERE id = :id")
    suspend fun deleteFavourite(id: Long): Int

    @Query("UPDATE favourites SET photoUri = :uri WHERE id = :id")
    suspend fun setFavouritePhoto(id: Long, uri: String?): Int

    @Query("UPDATE favourites SET timesUsed = timesUsed + :quantity WHERE id = :id")
    suspend fun recordUse(id: Long, quantity: Int)

    @Query("SELECT * FROM shop_details WHERE id = 1")
    fun observeShop(): Flow<ShopDetails?>

    @Query("SELECT * FROM shop_details WHERE id = 1")
    suspend fun getShop(): ShopDetails?

    @Upsert
    suspend fun saveShop(details: ShopDetails)

    @Query("SELECT * FROM bills ORDER BY billNumber DESC")
    fun observeBills(): Flow<List<Bill>>

    @Transaction
    @Query("SELECT * FROM bills WHERE createdAtMillis >= :startMillis AND createdAtMillis < :endMillis")
    fun observeDailyBills(startMillis: Long, endMillis: Long): Flow<List<DailySalesBill>>

    @Query("SELECT * FROM bills WHERE billNumber = :billNumber")
    suspend fun getBill(billNumber: Long): Bill?

    @Query("SELECT * FROM bill_lines WHERE billNumber = :billNumber ORDER BY position ASC")
    suspend fun getBillLines(billNumber: Long): List<BillLine>

    @Insert
    suspend fun insertBill(bill: Bill): Long

    @Insert
    suspend fun insertBillLines(lines: List<BillLine>)

    @Insert
    suspend fun insertCustomer(customer: Customer): Long

    @Query("UPDATE customers SET name = :name, phone = :phone WHERE id = :id")
    suspend fun updateCustomer(id: Long, name: String, phone: String?): Int

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomer(id: Long): Customer?

    @Transaction
    @Query("SELECT * FROM customers")
    fun observeCustomerCredit(): Flow<List<CustomerCreditRows>>

    @Transaction
    @Query("SELECT * FROM customers WHERE id = :id")
    fun observeCustomerCredit(id: Long): Flow<CustomerCreditRows?>

    @Transaction
    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerCredit(id: Long): CustomerCreditRows?

    @Insert
    suspend fun insertPayment(payment: Payment): Long
}
