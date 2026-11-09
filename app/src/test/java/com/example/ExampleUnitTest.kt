package com.example

import com.example.data.DutyRecord
import com.example.data.MessRecord
import com.example.data.OfficerProfile
import com.example.data.PoliceContact
import com.example.data.RunningChartRecord
import com.example.util.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun verifyOfficerProfileCreation() {
    val profile = OfficerProfile(
      name = "Ishan Kasthuri",
      rank = "Police Constable (PC)",
      badgeNumber = "84725",
      station = "Colombo Central"
    )
    assertEquals("Ishan Kasthuri", profile.name)
    assertEquals("84725", profile.badgeNumber)
    assertEquals("Colombo Central", profile.station)
  }

  @Test
  fun verifyDutyRecordCreation() {
    val duty = DutyRecord(
      type = "ON_DUTY",
      ibPage = "142",
      ibParagraph = "05",
      dutyNature = "Station Reserve",
      compressedSizeKb = 120
    )
    assertEquals("ON_DUTY", duty.type)
    assertEquals("142", duty.ibPage)
    assertEquals("05", duty.ibParagraph)
    assertEquals(120, duty.compressedSizeKb)
  }

  @Test
  fun verifyMessRecordCreation() {
    val mess = MessRecord(
      date = "2026-11-09",
      mealType = "LUNCH",
      isTaken = true,
      mealNotes = "Rice & Fish"
    )
    assertEquals("2026-11-09", mess.date)
    assertEquals("LUNCH", mess.mealType)
    assertEquals(true, mess.isTaken)
  }

  @Test
  fun verifyRunningChartCalculations() {
    val startMeter = 123456.0
    val endMeter = 123526.0
    val distance = endMeter - startMeter
    val kmPerLiter = 7.0
    val startFuel = 45.0
    val addedFuel = 14.0
    val consumed = distance / kmPerLiter
    val remaining = startFuel + addedFuel - consumed

    val record = RunningChartRecord(
      date = "2026-10-28",
      vehicleNumber = "KX 1493",
      startMeter = startMeter,
      endMeter = endMeter,
      distanceKm = distance,
      kmPerLiter = kmPerLiter,
      startFuel = startFuel,
      addedFuel = addedFuel,
      consumedFuel = consumed,
      remainingFuel = remaining
    )

    assertEquals(70.0, record.distanceKm, 0.01)
    assertEquals(10.0, record.consumedFuel, 0.01)
    assertEquals(49.0, record.remainingFuel, 0.01)
  }

  @Test
  fun verifyPoliceContactModel() {
    val contact = PoliceContact(
      designation = "Payagala Police Station",
      rank = "OIC",
      officeNo = "034-2222751",
      mobileNo = "071-8591694",
      oicTraffic = "071-8594371"
    )
    assertEquals("Payagala Police Station", contact.displayName)
    assertEquals("034-2222751", contact.primaryPhone)
    assertEquals("071-8591694", contact.secondaryPhone)
  }

  @Test
  fun verifyDateTimeUtilsFormatting() {
    val durationText = DateTimeUtils.formatDuration(3600000L * 2 + 60000L * 15)
    assertEquals("2h 15m", durationText)
  }
}
