package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.DutyRecord
import com.example.data.MessRecord
import com.example.data.PoliceDao
import com.example.data.RunningChartRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OfficerIsolationDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PoliceDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.policeDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun dutyRecords_areStrictlyScopedToOfficerId() = runBlocking {
        val officerA = "uid_officer_a"
        val officerB = "uid_officer_b"

        dao.insertDutyRecord(
            DutyRecord(
                officerId = officerA,
                oicStationCode = "OIC-CMB-1234",
                type = "ON_DUTY",
                timestamp = 1700000001000L,
                ibPage = "101",
                ibParagraph = "04",
                dutyNature = "Patrol Duty",
                remarks = "Station patrol"
            )
        )

        val aDuties = dao.getAllDutyRecords(officerA).first()
        val bDuties = dao.getAllDutyRecords(officerB).first()
        val bLatest = dao.getLatestDutyRecord(officerB).first()
        val bSearchEmpty = dao.searchDutyRecords(officerB, "").first()
        val bSearchKeyword = dao.searchDutyRecords(officerB, "Patrol").first()

        assertEquals(1, aDuties.size)
        assertEquals(0, bDuties.size)
        assertNull(bLatest)
        assertEquals(0, bSearchEmpty.size)
        assertEquals(0, bSearchKeyword.size)
        assertEquals(0, dao.getDutyCountForOfficer(officerB))
    }

    @Test
    fun messAndRunningCharts_areStrictlyScopedToOfficerId() = runBlocking {
        val officerA = "uid_officer_a"
        val officerB = "uid_officer_b"

        dao.insertMessRecord(
            MessRecord(
                officerId = officerA,
                oicStationCode = "OIC-CMB-1234",
                date = "2026-11-17",
                mealType = "LUNCH",
                isTaken = true,
                price = 220.0
            )
        )
        dao.insertRunningChartRecord(
            RunningChartRecord(
                officerId = officerA,
                oicStationCode = "OIC-CMB-1234",
                date = "2026-11-17",
                vehicleNumber = "WP-CAB-1234",
                startMeter = 1000.0,
                endMeter = 1050.0,
                distanceKm = 50.0,
                kmPerLiter = 10.0,
                startFuel = 20.0,
                addedFuel = 0.0,
                consumedFuel = 5.0,
                remainingFuel = 15.0
            )
        )

        assertEquals(1, dao.getAllMessRecords(officerA).first().size)
        assertEquals(0, dao.getAllMessRecords(officerB).first().size)
        assertEquals(0, dao.getMessRecordsForDate(officerB, "2026-11-17").first().size)
        assertEquals(0, dao.getMessRecordsForMonth(officerB, "2026-11").first().size)
        assertNull(dao.findMessRecord(officerB, "2026-11-17", "LUNCH"))

        assertEquals(1, dao.getAllRunningChartRecords(officerA).first().size)
        assertEquals(0, dao.getAllRunningChartRecords(officerB).first().size)
        assertEquals(0, dao.getDistinctVehicles(officerB).first().size)
        assertNull(dao.getLatestRecordForVehicle(officerB, "WP-CAB-1234"))
    }
}
