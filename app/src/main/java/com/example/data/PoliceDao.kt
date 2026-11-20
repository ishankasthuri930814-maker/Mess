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

    @Query("DELETE FROM officer_profile")
    suspend fun clearProfile()

    // --- Duty Records (Strictly scoped to officerId) ---
    @Query("SELECT * FROM duty_records WHERE officerId = :officerId ORDER BY timestamp DESC")
    fun getAllDutyRecords(officerId: String): Flow<List<DutyRecord>>

    @Query("SELECT * FROM duty_records WHERE officerId = :officerId ORDER BY timestamp DESC LIMIT 1")
    fun getLatestDutyRecord(officerId: String): Flow<DutyRecord?>

    @Query("SELECT * FROM duty_records WHERE officerId = :officerId AND (ibPage LIKE '%' || :query || '%' OR ibParagraph LIKE '%' || :query || '%' OR dutyNature LIKE '%' || :query || '%' OR remarks LIKE '%' || :query || '%') ORDER BY timestamp DESC")
    fun searchDutyRecords(officerId: String, query: String): Flow<List<DutyRecord>>

    @Query("SELECT COUNT(*) FROM duty_records WHERE officerId = :officerId")
    suspend fun getDutyCountForOfficer(officerId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDutyRecord(record: DutyRecord): Long

    @Query("DELETE FROM duty_records WHERE id = :id")
    suspend fun deleteDutyRecord(id: Long)

    // --- Mess Records (Strictly scoped to officerId) ---
    @Query("SELECT * FROM mess_records WHERE officerId = :officerId AND date = :date")
    fun getMessRecordsForDate(officerId: String, date: String): Flow<List<MessRecord>>

    @Query("SELECT * FROM mess_records WHERE officerId = :officerId AND date LIKE :monthPrefix || '%' ORDER BY date DESC, timestamp DESC")
    fun getMessRecordsForMonth(officerId: String, monthPrefix: String): Flow<List<MessRecord>>

    @Query("SELECT * FROM mess_records WHERE officerId = :officerId ORDER BY date DESC, timestamp DESC")
    fun getAllMessRecords(officerId: String): Flow<List<MessRecord>>

    @Query("SELECT * FROM mess_records WHERE officerId = :officerId AND date = :date AND mealType = :mealType LIMIT 1")
    suspend fun findMessRecord(officerId: String, date: String, mealType: String): MessRecord?

    @Query("SELECT COUNT(*) FROM mess_records WHERE officerId = :officerId")
    suspend fun getMessCountForOfficer(officerId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessRecord(record: MessRecord): Long

    @Update
    suspend fun updateMessRecord(record: MessRecord)

    @Query("DELETE FROM mess_records WHERE id = :id")
    suspend fun deleteMessRecord(id: Long)

    // --- Running Chart Records (Strictly scoped to officerId) ---
    @Query("SELECT * FROM running_chart_records WHERE officerId = :officerId ORDER BY date DESC, timestamp DESC")
    fun getAllRunningChartRecords(officerId: String): Flow<List<RunningChartRecord>>

    @Query("SELECT * FROM running_chart_records WHERE officerId = :officerId AND vehicleNumber = :vehicleNumber ORDER BY date DESC, timestamp DESC")
    fun getRunningChartRecordsByVehicle(officerId: String, vehicleNumber: String): Flow<List<RunningChartRecord>>

    @Query("SELECT * FROM running_chart_records WHERE officerId = :officerId AND date LIKE :monthPrefix || '%' ORDER BY date DESC, timestamp DESC")
    fun getRunningChartRecordsForMonth(officerId: String, monthPrefix: String): Flow<List<RunningChartRecord>>

    @Query("SELECT DISTINCT vehicleNumber FROM running_chart_records WHERE officerId = :officerId AND vehicleNumber != '' ORDER BY vehicleNumber ASC")
    fun getDistinctVehicles(officerId: String): Flow<List<String>>

    @Query("SELECT * FROM running_chart_records WHERE officerId = :officerId AND vehicleNumber = :vehicleNumber ORDER BY date DESC, timestamp DESC LIMIT 1")
    suspend fun getLatestRecordForVehicle(officerId: String, vehicleNumber: String): RunningChartRecord?

    @Query("SELECT COUNT(*) FROM running_chart_records WHERE officerId = :officerId")
    suspend fun getRunningChartCountForOfficer(officerId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRunningChartRecord(record: RunningChartRecord): Long

    @Update
    suspend fun updateRunningChartRecord(record: RunningChartRecord)

    @Query("DELETE FROM running_chart_records WHERE id = :id")
    suspend fun deleteRunningChartRecord(id: Long)
}
