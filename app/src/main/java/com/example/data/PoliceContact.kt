package com.example.data

data class PoliceContact(
    val designation: String = "",
    val rank: String = "",
    val name: String = "",
    val officeNo: String = "",
    val officeNo2: String = "",
    val mobileNo: String = "",
    val officeNo3: String = "",
    val fax: String = "",
    val email: String = "",
    val oicTraffic: String = "",
    val oicCrime: String = "",
    val oicVice: String = "",
    val oicCommunity: String = "",
    val locationGeo: String = "",
    val locationAddress: String = ""
) {
    val displayName: String
        get() = designation.ifBlank { name.ifBlank { "Police Contact" } }

    val primaryPhone: String
        get() = officeNo.ifBlank { officeNo2.ifBlank { mobileNo } }

    val secondaryPhone: String
        get() = if (primaryPhone == mobileNo) officeNo2.ifBlank { officeNo } else mobileNo
}
