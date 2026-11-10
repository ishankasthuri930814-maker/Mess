package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class PoliceRepository(private val dao: PoliceDao) {

    // --- Officer Profile ---
    val officerProfile: Flow<OfficerProfile?> = dao.getProfile()
    val allDutyRecords: Flow<List<DutyRecord>> = dao.getAllDutyRecords()
    val latestDutyRecord: Flow<DutyRecord?> = dao.getLatestDutyRecord()
    val allMessRecords: Flow<List<MessRecord>> = dao.getAllMessRecords()

    fun searchDutyRecords(query: String): Flow<List<DutyRecord>> {
        return if (query.isBlank()) {
            dao.getAllDutyRecords()
        } else {
            dao.searchDutyRecords(query.trim())
        }
    }

    suspend fun saveOfficerProfile(profile: OfficerProfile) {
        dao.saveProfile(profile)
    }

    suspend fun insertDutyRecord(record: DutyRecord): Long {
        return dao.insertDutyRecord(record)
    }

    suspend fun updateDutyRecord(record: DutyRecord) {
        dao.insertDutyRecord(record)
    }

    suspend fun deleteDutyRecord(record: DutyRecord) {
        record.photoPath?.let { path ->
            try {
                val file = File(path)
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        record.scenePhotoPath?.let { path ->
            try {
                val file = File(path)
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        dao.deleteDutyRecord(record.id)
    }

    // --- Mess Records ---
    fun getMessRecordsForDate(date: String): Flow<List<MessRecord>> {
        return dao.getMessRecordsForDate(date)
    }

    fun getMessRecordsForMonth(monthPrefix: String): Flow<List<MessRecord>> {
        return dao.getMessRecordsForMonth(monthPrefix)
    }

    suspend fun toggleMealTaken(date: String, mealType: String, isTaken: Boolean, defaultPrice: Double = 0.0) {
        val existing = dao.findMessRecord(date, mealType)
        if (existing != null) {
            dao.updateMessRecord(existing.copy(isTaken = isTaken))
        } else {
            dao.insertMessRecord(
                MessRecord(
                    date = date,
                    mealType = mealType,
                    isTaken = isTaken,
                    price = defaultPrice,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun saveMealRecord(
        date: String,
        mealType: String,
        isTaken: Boolean,
        notes: String,
        price: Double,
        photoPath: String?,
        compressedSizeKb: Int
    ) {
        val existing = dao.findMessRecord(date, mealType)
        if (existing != null) {
            dao.updateMessRecord(
                existing.copy(
                    isTaken = isTaken,
                    mealNotes = notes,
                    price = price,
                    photoPath = photoPath ?: existing.photoPath,
                    compressedSizeKb = if (photoPath != null) compressedSizeKb else existing.compressedSizeKb
                )
            )
        } else {
            dao.insertMessRecord(
                MessRecord(
                    date = date,
                    mealType = mealType,
                    isTaken = isTaken,
                    mealNotes = notes,
                    price = price,
                    photoPath = photoPath,
                    compressedSizeKb = compressedSizeKb,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteMessRecord(record: MessRecord) {
        record.photoPath?.let { path ->
            try {
                val file = File(path)
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        dao.deleteMessRecord(record.id)
    }

    // --- Running Chart Records ---
    val allRunningChartRecords: Flow<List<RunningChartRecord>> = dao.getAllRunningChartRecords()
    val distinctVehicles: Flow<List<String>> = dao.getDistinctVehicles()

    fun getRunningChartRecordsByVehicle(vehicle: String): Flow<List<RunningChartRecord>> {
        return if (vehicle.isBlank() || vehicle == "ALL" || vehicle == "සියල්ල") {
            dao.getAllRunningChartRecords()
        } else {
            dao.getRunningChartRecordsByVehicle(vehicle)
        }
    }

    fun getRunningChartRecordsForMonth(monthPrefix: String): Flow<List<RunningChartRecord>> {
        return dao.getRunningChartRecordsForMonth(monthPrefix)
    }

    suspend fun getLatestRecordForVehicle(vehicle: String): RunningChartRecord? {
        return dao.getLatestRecordForVehicle(vehicle)
    }

    suspend fun insertRunningChartRecord(record: RunningChartRecord): Long {
        return dao.insertRunningChartRecord(record)
    }

    suspend fun updateRunningChartRecord(record: RunningChartRecord) {
        dao.updateRunningChartRecord(record)
    }

    suspend fun deleteRunningChartRecord(record: RunningChartRecord) {
        dao.deleteRunningChartRecord(record.id)
    }

    // --- Police Contacts & Directory ---
    suspend fun loadOfflineContacts(context: Context): List<PoliceContact> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.assets.open("police_contacts.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<PoliceContact>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    PoliceContact(
                        designation = obj.optString("designation", ""),
                        rank = obj.optString("rank", ""),
                        name = obj.optString("name", ""),
                        officeNo = obj.optString("officeNo", ""),
                        officeNo2 = obj.optString("officeNo2", ""),
                        mobileNo = obj.optString("mobileNo", ""),
                        officeNo3 = obj.optString("officeNo3", ""),
                        fax = obj.optString("fax", ""),
                        email = obj.optString("email", ""),
                        oicTraffic = obj.optString("oicTraffic", ""),
                        oicCrime = obj.optString("oicCrime", ""),
                        oicVice = obj.optString("oicVice", ""),
                        oicCommunity = obj.optString("oicCommunity", ""),
                        locationGeo = obj.optString("locationGeo", ""),
                        locationAddress = obj.optString("locationAddress", "")
                    )
                )
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun fetchLiveGoogleSheetContacts(): List<PoliceContact>? = withContext(Dispatchers.IO) {
        try {
            val urlString = "https://sheets.googleapis.com/v4/spreadsheets/1tMu-Wpwht7dH0NF4YSfiWjlttS_WOsgrbfJ3_zsJYd0/values/Sheet1!A1:Z1000?key=AIzaSyC6wDD_qFJmgK3tQICKrmgh6IgtWUbI0ps"
            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.requestMethod = "GET"

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(response)
                val values = root.optJSONArray("values") ?: return@withContext null
                val list = mutableListOf<PoliceContact>()
                for (i in 1 until values.length()) {
                    val row = values.getJSONArray(i)
                    fun col(idx: Int): String = if (idx < row.length()) row.optString(idx, "").trim() else ""
                    val contact = PoliceContact(
                        designation = col(0),
                        rank = col(1),
                        name = col(2),
                        officeNo = col(3),
                        officeNo2 = col(4),
                        mobileNo = col(5),
                        officeNo3 = col(6),
                        fax = col(7),
                        email = col(8),
                        oicTraffic = col(9),
                        oicCrime = col(10),
                        oicVice = col(11),
                        oicCommunity = col(12),
                        locationGeo = col(13),
                        locationAddress = col(14)
                    )
                    if (contact.displayName.isNotBlank() || contact.primaryPhone.isNotBlank()) {
                        list.add(contact)
                    }
                }
                list
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
