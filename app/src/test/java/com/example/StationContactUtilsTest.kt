package com.example

import com.example.data.AppUser
import com.example.util.StationContactUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationContactUtilsTest {

    @Test
    fun phoneValidationRequiresAtLeastNineDigits() {
        assertFalse(StationContactUtils.isValidPhoneNumber(""))
        assertFalse(StationContactUtils.isValidPhoneNumber("07712345"))
        assertFalse(StationContactUtils.isValidPhoneNumber("abc-123-45"))
        assertTrue(StationContactUtils.isValidPhoneNumber("0771234567"))
        assertTrue(StationContactUtils.isValidPhoneNumber("077-123 4567"))
        assertTrue(StationContactUtils.isValidPhoneNumber("+94771234567"))
    }

    @Test
    fun stationContactsStrictlyScopedToSameApprovedStationAndSortedByRole() {
        val oic = AppUser(
            uid = "u_oic",
            displayName = "Kamal Perera",
            role = "OIC",
            status = "APPROVED",
            oicStationCode = "OIC-KND-1001",
            badgeNumber = "1020",
            rank = "OIC (CI)",
            phoneNumber = "0771111111"
        )
        val admin = AppUser(
            uid = "u_admin",
            displayName = "Nimal Silva",
            role = "ADMIN_BRANCH",
            status = "APPROVED",
            oicStationCode = "oic-knd-1001",
            badgeNumber = "2050",
            rank = "Admin Branch",
            phoneNumber = "0772222222"
        )
        val officerA = AppUser(
            uid = "u_off_a",
            displayName = "Ruwan Bandara",
            role = "OFFICER",
            status = "APPROVED",
            oicStationCode = "OIC-KND-1001",
            badgeNumber = "84725",
            rank = "Police Constable (PC)",
            phoneNumber = "0773333333"
        )
        val pendingOfficer = AppUser(
            uid = "u_pending",
            displayName = "Pending User",
            role = "OFFICER",
            status = "PENDING",
            oicStationCode = "OIC-KND-1001",
            phoneNumber = "0774444444"
        )
        val otherStationOfficer = AppUser(
            uid = "u_other_station",
            displayName = "Other Station Officer",
            role = "OFFICER",
            status = "APPROVED",
            oicStationCode = "OIC-COL-9999",
            phoneNumber = "0775555555"
        )

        val resultForOfficer = StationContactUtils.filterAndSortStationContacts(
            currentUser = officerA,
            stationUsers = listOf(officerA, otherStationOfficer, pendingOfficer, admin, oic)
        )

        assertEquals(listOf("u_oic", "u_admin", "u_off_a"), resultForOfficer.map { it.uid })

        val resultForPendingViewer = StationContactUtils.filterAndSortStationContacts(
            currentUser = pendingOfficer,
            stationUsers = listOf(oic, admin, officerA)
        )
        assertTrue(resultForPendingViewer.isEmpty())
    }
}
