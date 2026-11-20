package com.example.util

import com.example.data.AppUser

object StationContactUtils {

    fun isValidPhoneNumber(phone: String): Boolean {
        return phone.filter { it.isDigit() }.length >= 9
    }

    fun filterAndSortStationContacts(
        currentUser: AppUser?,
        stationUsers: List<AppUser>
    ): List<AppUser> {
        val myCode = currentUser?.oicStationCode?.trim()?.uppercase().orEmpty()
        if (currentUser == null || currentUser.status != "APPROVED" || myCode.isBlank()) {
            return emptyList()
        }
        return stationUsers
            .filter { u ->
                u.status == "APPROVED" &&
                    u.oicStationCode?.trim()?.uppercase() == myCode
            }
            .distinctBy { it.uid }
            .sortedWith(
                compareBy<AppUser>(
                    {
                        when (it.role) {
                            "OIC" -> 0
                            "ADMIN_BRANCH" -> 1
                            else -> 2
                        }
                    },
                    { it.displayName.lowercase() }
                )
            )
    }
}
