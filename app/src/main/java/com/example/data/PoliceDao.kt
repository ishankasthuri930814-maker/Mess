package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PoliceDao {

    // --- Officer Profile ---
    @Query("SELECT * FROM officer_profile WHERE id = 1 LIMIT 1")
    fun getProfile(): Flow<OfficerProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: OfficerProfile)

    // --- Duty Records ---
    @Query("SELECT * FROM duty_records ORDER BY timestamp DESC")
    fun getAllDutyRecords(): Flow<List<DutyRecord>>

    @Query("SELECT * FROM duty_records ORDER BY timestamp DESC LIMIT 1")
    fun getLatestDutyRecord(): Flow<DutyRecord?>

    @Query("SELECT * FROM duty_records WHERE ibPage LIKE '%' || :query || '%' OR ibParagraph LIKE '%' || :query || '%' OR dutyNature LIKE '%' || :query || '%' OR remarks LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchDutyRecords(query: String): Flow<List<DutyRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDutyRecord(record: DutyRecord): Long

    @Query("DELETE FROM duty_records WHERE id = :id")
    suspend fun deleteDutyRecord(id: Long)

    // --- Mess Records ---
    @Query("SELECT * FROM mess_records WHERE date = :date")
    fun getMessRecordsForDate(date: String): Flow<List<MessRecord>>

    @Query("SELECT * FROM mess_records WHERE date LIKE :monthPrefix || '%' ORDER BY date DESC, timestamp DESC")
    fun getMessRecordsForMonth(monthPrefix: String): Flow<List<MessRecord>>

    @Query("SELECT * FROM mess_records ORDER BY date DESC, timestamp DESC")
    fun getAllMessRecords(): Flow<List<MessRecord>>

    @Query("SELECT * FROM mess_records WHERE date = :date AND mealType = :mealType LIMIT 1")
    suspend fun findMessRecord(date: String, mealType: String): MessRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessRecord(record: MessRecord): Long

    @Update
    suspend fun updateMessRecord(record: MessRecord)

    @Query("DELETE FROM mess_records WHERE id = :id")
    suspend fun deleteMessRecord(id: Long)

    // --- Running Chart Records ---
    @Query("SELECT * FROM running_chart_records ORDER BY date DESC, timestamp DESC")
    fun getAllRunningChartRecords(): Flow<List<RunningChartRecord>>

    @Query("SELECT * FROM running_chart_records WHERE vehicleNumber = :vehicleNumber ORDER BY date DESC, timestamp DESC")
    fun getRunningChartRecordsByVehicle(vehicleNumber: String): Flow<List<RunningChartRecord>>

    @Query("SELECT * FROM running_chart_records WHERE date LIKE :monthPrefix || '%' ORDER BY date DESC, timestamp DESC")
    fun getRunningChartRecordsForMonth(monthPrefix: String): Flow<List<RunningChartRecord>>

    @Query("SELECT DISTINCT vehicleNumber FROM running_chart_records WHERE vehicleNumber != '' ORDER BY vehicleNumber ASC")
    fun getDistinctVehicles(): Flow<List<String>>

    @Query("SELECT * FROM running_chart_records WHERE vehicleNumber = :vehicleNumber ORDER BY date DESC, timestamp DESC LIMIT 1")
    suspend fun getLatestRecordForVehicle(vehicleNumber: String): RunningChartRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRunningChartRecord(record: RunningChartRecord): Long

    @Update
    suspend fun updateRunningChartRecord(record: RunningChartRecord)

    @Query("DELETE FROM running_chart_records WHERE id = :id")
    suspend fun deleteRunningChartRecord(id: Long)
}
