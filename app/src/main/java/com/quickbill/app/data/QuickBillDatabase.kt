package com.quickbill.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [FavouriteItem::class, ShopDetails::class, Bill::class, BillLine::class],
    version = 1,
    exportSchema = true,
)
abstract class QuickBillDatabase : RoomDatabase() {
    abstract fun dao(): QuickBillDao

    companion object {
        fun create(context: Context): QuickBillDatabase = Room.databaseBuilder(
            context.applicationContext,
            QuickBillDatabase::class.java,
            "quick-bill.db",
        ).build()
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

    @Query("SELECT * FROM bills WHERE billNumber = :billNumber")
    suspend fun getBill(billNumber: Long): Bill?

    @Query("SELECT * FROM bill_lines WHERE billNumber = :billNumber ORDER BY position ASC")
    suspend fun getBillLines(billNumber: Long): List<BillLine>

    @Insert
    suspend fun insertBill(bill: Bill): Long

    @Insert
    suspend fun insertBillLines(lines: List<BillLine>)
}
