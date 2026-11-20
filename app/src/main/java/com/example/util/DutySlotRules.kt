package com.example.util

import com.example.data.AppUser
import com.example.data.DutyRecord

data class ReserveHolderInfo(
    val category: String,
    val officerUid: String,
    val officerName: String,
    val rank: String,
    val badgeNumber: String,
    val sinceTimestamp: Long
)

object DutySlotRules {

    const val SLOT_RESERVE = "STATION_RESERVE"
    const val SLOT_DUTY_OFFICER = "DUTY_OFFICER"
    const val SLOT_CHARGE_ROOM = "CHARGE_ROOM"

    fun isReserveDuty(dutyNature: String): Boolean {
        val lower = dutyNature.trim().lowercase()
        return lower.contains("reserve") ||
            lower.contains("උපසේවා") ||
            lower.contains("ස්ථාන රක්ෂිත")
    }

    fun getSingleSlotCategory(dutyNature: String): String? {
        val lower = dutyNature.trim().lowercase()
        return when {
            isReserveDuty(lower) -> SLOT_RESERVE
            lower.contains("duty officer") || lower.contains("රාජකාරි භාර") -> SLOT_DUTY_OFFICER
            lower.contains("charge room") || lower.contains("චෝදනාගාර") -> SLOT_CHARGE_ROOM
            else -> null
        }
    }

    fun getCategoryDisplayName(category: String): String = when (category) {
        SLOT_RESERVE -> "උපසේවා (Station Reserve)"
        SLOT_DUTY_OFFICER -> "රාජකාරි භාර නිලධාරී (Duty Officer)"
        SLOT_CHARGE_ROOM -> "චෝදනාගාර භාර නිලධාරී (Charge Room)"
        else -> "Single-Slot Duty"
    }

    /**
     * Resolves the currently active holder per single-slot duty category for a police station.
     * For Station Reserve, the latest ON_DUTY/EXTRA_DUTY Reserve officer remains the active holder
     * until either they go OFF_DUTY after that timestamp OR another officer reports ON_DUTY Reserve
     * with a newer timestamp (handover/relief).
     */
    fun resolveActiveSingleSlotHolders(
        stationUsers: List<AppUser>,
        stationRecords: List<DutyRecord>,
        nowMs: Long = System.currentTimeMillis()
    ): Map<String, ReserveHolderInfo> {
        val userMap = stationUsers.associateBy { it.uid }
        val cutoffMs = nowMs - (48L * 60L * 60L * 1000L) // Look at active shifts within last 48h

        val latestOffDutyByOfficer = mutableMapOf<String, Long>()
        for (rec in stationRecords) {
            if (rec.type == "OFF_DUTY") {
                val prev = latestOffDutyByOfficer[rec.officerId] ?: 0L
                if (rec.timestamp > prev) {
                    latestOffDutyByOfficer[rec.officerId] = rec.timestamp
                }
            }
        }

        val activeHolders = mutableMapOf<String, ReserveHolderInfo>()

        for (category in listOf(SLOT_RESERVE, SLOT_DUTY_OFFICER, SLOT_CHARGE_ROOM)) {
            // Take the latest ON_DUTY / EXTRA_DUTY record in this category
            val candidateRecords = stationRecords
                .filter {
                    (it.type == "ON_DUTY" || it.type == "EXTRA_DUTY") &&
                        it.timestamp >= cutoffMs &&
                        getSingleSlotCategory(it.dutyNature) == category
                }
                .sortedByDescending { it.timestamp }

            val latestClaim = candidateRecords.firstOrNull() ?: continue
            val offDutyAfterClaim = (latestOffDutyByOfficer[latestClaim.officerId] ?: 0L) > latestClaim.timestamp
            val user = userMap[latestClaim.officerId]

            val isUserStillActive = if (user != null) {
                user.status == "APPROVED" && user.isOnDuty && !offDutyAfterClaim
            } else {
                !offDutyAfterClaim
            }

            if (isUserStillActive) {
                activeHolders[category] = ReserveHolderInfo(
                    category = category,
                    officerUid = latestClaim.officerId,
                    officerName = user?.displayName?.ifBlank { user.email } ?: "Officer",
                    rank = user?.rank.orEmpty(),
                    badgeNumber = user?.badgeNumber.orEmpty(),
                    sinceTimestamp = latestClaim.timestamp
                )
            }
        }

        return activeHolders
    }

    /**
     * Rank-based expected duties guide for Administration Branch (පාලන අංශය).
     */
    data class RankDutyGuide(
        val rankGroup: String,
        val filterKey: String,
        val sinhalaTitle: String,
        val expectedDuties: List<String>
    )

    val rankDutyGuides = listOf(
        RankDutyGuide(
            rankGroup = "IP / CI",
            filterKey = "Inspector",
            sinhalaTitle = "පොලිස් පරීක්ෂක / ප්‍රධාන පොලිස් පරීක්ෂක (IP / CI)",
            expectedDuties = listOf(
                "ස්ථාන අධීක්ෂණය හා පාලනය",
                "අපරාධ හා විශේෂ විමර්ශන මෙහෙයවීම",
                "අධිකරණ කටයුතු හා නඩු පැවරීම",
                "Duty Officer (රාජකාරි භාර නිලධාරී)"
            )
        ),
        RankDutyGuide(
            rankGroup = "SI / WSI",
            filterKey = "Sub Inspector",
            sinhalaTitle = "උප පොලිස් පරීක්ෂක (SI / WSI)",
            expectedDuties = listOf(
                "Duty Officer / Charge Room භාර නිලධාරී",
                "මුරසංචාර හා වැටලීම් කණ්ඩායම් නායකත්වය",
                "පැමිණිලි විමර්ශනය සහ ස්ථාන පරීක්ෂණ",
                "මාර්ග බාධක හා රථවාහන මෙහෙයුම්"
            )
        ),
        RankDutyGuide(
            rankGroup = "PS / WPS",
            filterKey = "Sergeant",
            sinhalaTitle = "පොලිස් සැරයන් (PS / WPS)",
            expectedDuties = listOf(
                "Station Reserve (උපසේවා) / Charge Room භාර",
                "මුර රාජකාරි හා භට කණ්ඩායම් මෙහෙයවීම",
                "තොරතුරු පොත් (IB) සටහන් අධීක්ෂණය",
                "රථවාහන හා අධිකරණ රාජකාරි"
            )
        ),
        RankDutyGuide(
            rankGroup = "PC / WPC",
            filterKey = "Constable",
            sinhalaTitle = "පොලිස් කොස්තාපල් (PC / WPC)",
            expectedDuties = listOf(
                "Station Reserve (උපසේවා) / මුර රාජකාරි",
                "Mobile Patrol (මුරසංචාර) හා Check Point",
                "රථවාහන පාලනය, අධිකරණ හා VIP ආරක්ෂක",
                "පැමිණිලි ලියාපදිංචි කිරීම හා සහාය"
            )
        )
    )
}
