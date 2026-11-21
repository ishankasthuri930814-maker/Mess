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

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PoliceRepository(
    private val dao: PoliceDao,
    private val db: FirebaseFirestore
) {

    // --- Officer Profile & Per-Officer Scoped Flows ---
    val officerProfile: Flow<OfficerProfile?> = dao.getProfile()

    suspend fun clearLocalProfile() {
        dao.clearProfile()
    }

    fun getAllDutyRecords(officerId: String): Flow<List<DutyRecord>> =
        if (officerId.isBlank()) flowOf(emptyList()) else dao.getAllDutyRecords(officerId)

    fun getLatestDutyRecord(officerId: String): Flow<DutyRecord?> =
        if (officerId.isBlank()) flowOf(null) else dao.getLatestDutyRecord(officerId)

    fun getAllMessRecords(officerId: String): Flow<List<MessRecord>> =
        if (officerId.isBlank()) flowOf(emptyList()) else dao.getAllMessRecords(officerId)

    private val auth = Firebase.auth
    private fun requireUserId(): String = auth.currentUser?.uid ?: ""

    fun observeUser(uid: String): Flow<AppUser?> = db.collection("users").document(uid)
        .snapshots()
        .map { snapshot ->
            snapshot.toObject(AppUser::class.java)?.let { user ->
                val explicitOnDuty = snapshot.getBoolean("isOnDuty") ?: user.isOnDuty
                val hiddenLists = snapshot.getBoolean("hiddenFromStationLists") ?: user.hiddenFromStationLists
                val hiddenMap = snapshot.getBoolean("hiddenOnMap") ?: user.hiddenOnMap
                val pauseLog = snapshot.getBoolean("pauseLocationLogging") ?: user.pauseLocationLogging
                val hideDuty = snapshot.getBoolean("hideDutyStatus") ?: user.hideDutyStatus
                user.copy(
                    uid = if (user.uid.isBlank()) snapshot.id else user.uid,
                    isOnDuty = explicitOnDuty,
                    hiddenFromStationLists = hiddenLists,
                    hiddenOnMap = hiddenMap,
                    pauseLocationLogging = pauseLog,
                    hideDutyStatus = hideDuty
                )
            }
        }

    suspend fun saveUser(user: AppUser) {
        val payload = mutableMapOf<String, Any>(
            "uid" to user.uid,
            "displayName" to user.displayName,
            "email" to user.email,
            "role" to user.role,
            "status" to user.status,
            "oicEmail" to user.oicEmail,
            "stationName" to user.stationName,
            "badgeNumber" to user.badgeNumber,
            "rank" to user.rank,
            "phoneNumber" to user.phoneNumber,
            "address" to user.address,
            "isOnDuty" to user.isOnDuty,
            "hiddenFromStationLists" to user.hiddenFromStationLists,
            "hiddenOnMap" to user.hiddenOnMap,
            "pauseLocationLogging" to user.pauseLocationLogging,
            "hideDutyStatus" to user.hideDutyStatus,
            "adminSecretSeed" to user.adminSecretSeed,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        user.oicStationCode?.let { payload["oicStationCode"] = it }
        user.lastLocation?.let { payload["lastLocation"] = it }
        db.collection("users").document(user.uid).set(payload).await()
    }

    suspend fun updateOfficerFullProfile(
        uid: String,
        displayName: String,
        rank: String,
        badgeNumber: String,
        stationName: String,
        phoneNumber: String,
        address: String
    ) {
        db.collection("users").document(uid).update(
            mapOf(
                "displayName" to displayName,
                "rank" to rank,
                "badgeNumber" to badgeNumber,
                "stationName" to stationName,
                "phoneNumber" to phoneNumber,
                "address" to address,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun updateOwnContactInfo(
        uid: String,
        phoneNumber: String,
        badgeNumber: String,
        rank: String
    ) {
        val updates = mutableMapOf<String, Any>(
            "phoneNumber" to phoneNumber,
            "badgeNumber" to badgeNumber,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (rank.isNotBlank()) {
            updates["rank"] = rank
        }
        db.collection("users").document(uid).update(updates).await()
    }

    fun observeSubordinates(oicStationCode: String): Flow<List<AppUser>> = db.collection("users")
        .whereEqualTo("oicStationCode", oicStationCode)
        .snapshots()
        .map { snapshot ->
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(AppUser::class.java)?.let { user ->
                    val explicitOnDuty = doc.getBoolean("isOnDuty") ?: user.isOnDuty
                    val hiddenLists = doc.getBoolean("hiddenFromStationLists") ?: user.hiddenFromStationLists
                    val hiddenMap = doc.getBoolean("hiddenOnMap") ?: user.hiddenOnMap
                    val pauseLog = doc.getBoolean("pauseLocationLogging") ?: user.pauseLocationLogging
                    val hideDuty = doc.getBoolean("hideDutyStatus") ?: user.hideDutyStatus
                    user.copy(
                        uid = if (user.uid.isBlank()) doc.id else user.uid,
                        isOnDuty = explicitOnDuty,
                        hiddenFromStationLists = hiddenLists,
                        hiddenOnMap = hiddenMap,
                        pauseLocationLogging = pauseLog,
                        hideDutyStatus = hideDuty
                    )
                }
            }.filter { it.role == "OFFICER" || it.role == "ADMIN_BRANCH" }
        }

    suspend fun updateOfficerStatus(officerUid: String, status: String) {
        db.collection("users").document(officerUid).update("status", status).await()
    }

    suspend fun removeMemberFromStation(officerUid: String) {
        db.collection("users").document(officerUid).update(
            mapOf(
                "oicStationCode" to "",
                "status" to "REMOVED",
                "isOnDuty" to false,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun updateOfficerStealthSettings(
        uid: String,
        hiddenFromStationLists: Boolean,
        hiddenOnMap: Boolean,
        pauseLocationLogging: Boolean,
        hideDutyStatus: Boolean
    ) {
        db.collection("users").document(uid).update(
            mapOf(
                "hiddenFromStationLists" to hiddenFromStationLists,
                "hiddenOnMap" to hiddenOnMap,
                "pauseLocationLogging" to pauseLocationLogging,
                "hideDutyStatus" to hideDutyStatus,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun transferOfficer(officerUid: String, newStationCode: String, newStatus: String = "PENDING") {
        db.collection("users").document(officerUid).update(
            "oicStationCode", newStationCode,
            "status", newStatus
        ).await()
    }

    suspend fun transferSubordinatesBatch(officerUids: List<String>, newStationCode: String, newStatus: String = "APPROVED") {
        val batch = db.batch()
        for (uid in officerUids) {
            val docRef = db.collection("users").document(uid)
            batch.update(docRef, mapOf(
                "oicStationCode" to newStationCode,
                "status" to newStatus
            ))
        }
        batch.commit().await()
    }

    suspend fun updateOicStation(oicUid: String, newStationName: String, newStationCode: String) {
        db.collection("users").document(oicUid).update(
            "stationName", newStationName,
            "oicStationCode", newStationCode
        ).await()
    }

    fun observeDutyRecordsForOfficer(officerId: String): Flow<List<DutyRecord>> = db.collection("duty_records")
        .whereEqualTo("officerId", officerId)
        .snapshots()
        .map { it.toObjects(DutyRecord::class.java) }

    fun observeStationDutyRecords(oicStationCode: String): Flow<List<DutyRecord>> {
        val cleanCode = oicStationCode.trim().uppercase()
        if (cleanCode.isBlank()) return flowOf(emptyList())
        return db.collection("duty_records")
            .whereEqualTo("oicStationCode", cleanCode)
            .snapshots()
            .map { it.toObjects(DutyRecord::class.java) }
            .catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w("PoliceRepository", "Error observing station duty records", e)
                emit(emptyList())
            }
    }

    fun observeStationUsers(oicStationCode: String): Flow<List<AppUser>> {
        val cleanCode = oicStationCode.trim().uppercase()
        if (cleanCode.isBlank()) return flowOf(emptyList())
        return db.collection("users")
            .whereEqualTo("oicStationCode", cleanCode)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(AppUser::class.java)?.let { user ->
                        val explicitOnDuty = doc.getBoolean("isOnDuty") ?: user.isOnDuty
                        user.copy(
                            uid = if (user.uid.isBlank()) doc.id else user.uid,
                            isOnDuty = explicitOnDuty
                        )
                    }
                }
            }
            .catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w("PoliceRepository", "Error observing station users", e)
                emit(emptyList())
            }
    }

    fun observeStationAdminSeed(oicStationCode: String): Flow<String> {
        val cleanCode = oicStationCode.trim().uppercase()
        if (cleanCode.isBlank()) return flowOf("")
        return db.collection("users")
            .whereEqualTo("oicStationCode", cleanCode)
            .whereEqualTo("role", "ADMIN_BRANCH")
            .snapshots()
            .map { snapshot ->
                val admins = snapshot.documents.mapNotNull { it.toObject(AppUser::class.java) }
                val chosen = admins.firstOrNull { it.status == "APPROVED" && it.adminSecretSeed.isNotBlank() }
                    ?: admins.firstOrNull { it.adminSecretSeed.isNotBlank() }
                chosen?.adminSecretSeed ?: ""
            }
            .catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w("PoliceRepository", "Error observing station admin seed", e)
                emit("")
            }
    }

    suspend fun fetchStationDutyAndUsersState(oicStationCode: String): Pair<List<AppUser>, List<DutyRecord>> {
        val cleanCode = oicStationCode.trim().uppercase()
        if (cleanCode.isBlank()) return Pair(emptyList(), emptyList())
        return try {
            val usersSnap = db.collection("users")
                .whereEqualTo("oicStationCode", cleanCode)
                .get()
                .await()
            val users = usersSnap.documents.mapNotNull { doc ->
                doc.toObject(AppUser::class.java)?.let { user ->
                    val explicitOnDuty = doc.getBoolean("isOnDuty") ?: user.isOnDuty
                    user.copy(
                        uid = if (user.uid.isBlank()) doc.id else user.uid,
                        isOnDuty = explicitOnDuty
                    )
                }
            }
            val dutySnap = db.collection("duty_records")
                .whereEqualTo("oicStationCode", cleanCode)
                .get()
                .await()
            val records = dutySnap.toObjects(DutyRecord::class.java)
            Pair(users, records)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PoliceRepository", "Failed to fetch live station duty & user state", e)
            Pair(emptyList(), emptyList())
        }
    }

    fun observeMessRecordsForOfficer(officerId: String): Flow<List<MessRecord>> = db.collection("mess_records")
        .whereEqualTo("officerId", officerId)
        .snapshots()
        .map { snapshot ->
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(MessRecord::class.java)?.let { m ->
                    val explicitTaken = doc.getBoolean("isTaken") ?: m.isTaken
                    m.copy(isTaken = explicitTaken)
                }
            }
        }

    fun observeRunningChartRecordsForOfficer(officerId: String): Flow<List<RunningChartRecord>> = db.collection("running_chart_records")
        .whereEqualTo("officerId", officerId)
        .snapshots()
        .map { it.toObjects(RunningChartRecord::class.java) }

    fun observeStationDutyAssignments(oicStationCode: String, dateKey: String? = null): Flow<List<DutyAssignment>> {
        val cleanCode = oicStationCode.trim().uppercase()
        if (cleanCode.isBlank()) return flowOf(emptyList())
        var query = db.collection("duty_assignments").whereEqualTo("oicStationCode", cleanCode)
        if (!dateKey.isNullOrBlank()) {
            query = query.whereEqualTo("dateKey", dateKey.trim())
        }
        return query.snapshots().map { snapshot ->
            snapshot.toObjects(DutyAssignment::class.java).sortedBy { it.officerName }
        }.catch { e ->
            if (e is CancellationException && e.cause == null) throw e
            Log.w("PoliceRepository", "Error observing station duty assignments", e)
            emit(emptyList())
        }
    }

    fun observeOfficerDutyAssignments(officerId: String, fromDateKey: String? = null): Flow<List<DutyAssignment>> {
        if (officerId.isBlank()) return flowOf(emptyList())
        var query = db.collection("duty_assignments").whereEqualTo("officerId", officerId)
        if (!fromDateKey.isNullOrBlank()) {
            query = query.whereGreaterThanOrEqualTo("dateKey", fromDateKey.trim())
        }
        return query.snapshots().map { snapshot ->
            snapshot.toObjects(DutyAssignment::class.java).sortedBy { it.dateKey }
        }.catch { e ->
            if (e is CancellationException && e.cause == null) throw e
            Log.w("PoliceRepository", "Error observing officer duty assignments", e)
            emit(emptyList())
        }
    }

    suspend fun saveDutyAssignment(assignment: DutyAssignment): Result<Unit> {
        return try {
            val docId = if (assignment.id.isNotBlank()) assignment.id else db.collection("duty_assignments").document().id
            val finalAssignment = assignment.copy(id = docId)
            val payload = mapOf(
                "id" to finalAssignment.id,
                "officerId" to finalAssignment.officerId,
                "officerName" to finalAssignment.officerName,
                "officerBadge" to finalAssignment.officerBadge,
                "officerRank" to finalAssignment.officerRank,
                "officerPhone" to finalAssignment.officerPhone,
                "oicStationCode" to finalAssignment.oicStationCode,
                "dateKey" to finalAssignment.dateKey,
                "dutyNature" to finalAssignment.dutyNature,
                "shift" to finalAssignment.shift,
                "remarks" to finalAssignment.remarks,
                "assignedByUid" to finalAssignment.assignedByUid,
                "assignedByName" to finalAssignment.assignedByName,
                "status" to finalAssignment.status,
                "createdAtMs" to finalAssignment.createdAtMs,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("duty_assignments").document(docId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PoliceRepository", "Failed to save duty assignment", e)
            Result.failure(e)
        }
    }

    suspend fun deleteDutyAssignment(assignmentId: String): Result<Unit> {
        if (assignmentId.isBlank()) return Result.failure(IllegalArgumentException("Invalid ID"))
        return try {
            db.collection("duty_assignments").document(assignmentId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PoliceRepository", "Failed to delete duty assignment", e)
            Result.failure(e)
        }
    }

    fun observeStationMessRecords(oicStationCode: String): Flow<List<MessRecord>> {
        val cleanCode = oicStationCode.trim().uppercase()
        if (cleanCode.isBlank()) return flowOf(emptyList())
        return db.collection("mess_records")
            .whereEqualTo("oicStationCode", cleanCode)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(MessRecord::class.java)?.let { m ->
                        val explicitTaken = doc.getBoolean("isTaken") ?: m.isTaken
                        m.copy(isTaken = explicitTaken)
                    }
                }
            }.catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w("PoliceRepository", "Error observing station mess records", e)
                emit(emptyList())
            }
    }

    suspend fun syncDutyRecordToFirestore(record: DutyRecord) {
        if (record.officerId.isBlank()) return
        try {
            val payload = mutableMapOf<String, Any>(
                "officerId" to record.officerId,
                "oicStationCode" to record.oicStationCode,
                "type" to record.type,
                "timestamp" to record.timestamp,
                "ibPage" to record.ibPage,
                "ibParagraph" to record.ibParagraph,
                "dutyNature" to record.dutyNature,
                "remarks" to record.remarks,
                "dutyDurationMinutes" to record.dutyDurationMinutes,
                "createdAt" to FieldValue.serverTimestamp()
            )
            record.photoPath?.let { payload["photoPath"] = it }
            record.scenePhotoPath?.let { payload["scenePhotoPath"] = it }
            db.collection("duty_records").document().set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PoliceRepository", "Failed to sync duty record to Firestore (saved locally)", e)
        }
    }

    suspend fun syncMessRecordToFirestore(record: MessRecord) {
        if (record.officerId.isBlank()) return
        try {
            val payload = mutableMapOf<String, Any>(
                "officerId" to record.officerId,
                "oicStationCode" to record.oicStationCode,
                "date" to record.date,
                "mealType" to record.mealType,
                "isTaken" to record.isTaken,
                "mealNotes" to record.mealNotes,
                "price" to record.price,
                "timestamp" to record.timestamp,
                "createdAt" to FieldValue.serverTimestamp()
            )
            record.photoPath?.let { payload["photoPath"] = it }
            val docId = "${record.officerId}_${record.date}_${record.mealType}"
            db.collection("mess_records").document(docId).set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PoliceRepository", "Failed to sync mess record to Firestore (saved locally)", e)
        }
    }

    suspend fun syncRunningChartRecordToFirestore(record: RunningChartRecord) {
        if (record.officerId.isBlank()) return
        try {
            val payload = mapOf(
                "officerId" to record.officerId,
                "oicStationCode" to record.oicStationCode,
                "date" to record.date,
                "vehicleNumber" to record.vehicleNumber,
                "startMeter" to record.startMeter,
                "endMeter" to record.endMeter,
                "distanceKm" to record.distanceKm,
                "kmPerLiter" to record.kmPerLiter,
                "startFuel" to record.startFuel,
                "addedFuel" to record.addedFuel,
                "consumedFuel" to record.consumedFuel,
                "remainingFuel" to record.remainingFuel,
                "notes" to record.notes,
                "timestamp" to record.timestamp,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("running_chart_records").document().set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PoliceRepository", "Failed to sync running chart record to Firestore", e)
        }
    }

    // --- Team Duty QR Sessions ---

    suspend fun createTeamDutySession(
        session: TeamDutySession,
        leaderMember: TeamDutyMember
    ): Result<Unit> {
        return try {
            val sessionRef = db.collection("team_duties").document(session.sessionId)
            val memberRef = sessionRef.collection("members").document(leaderMember.uid)
            val sessionPayload = mapOf(
                "sessionId" to session.sessionId,
                "joinCode" to session.joinCode,
                "leaderUid" to session.leaderUid,
                "leaderName" to session.leaderName,
                "leaderBadge" to session.leaderBadge,
                "leaderRank" to session.leaderRank,
                "oicStationCode" to session.oicStationCode,
                "dutyNature" to session.dutyNature,
                "ibPage" to session.ibPage,
                "ibParagraph" to session.ibParagraph,
                "remarks" to session.remarks,
                "status" to "ACTIVE",
                "startedAtMs" to session.startedAtMs,
                "endedAtMs" to 0L,
                "createdAt" to FieldValue.serverTimestamp()
            )
            val memberPayload = mapOf(
                "uid" to leaderMember.uid,
                "displayName" to leaderMember.displayName,
                "rank" to leaderMember.rank,
                "badgeNumber" to leaderMember.badgeNumber,
                "phoneNumber" to leaderMember.phoneNumber,
                "roleInTeam" to "LEADER",
                "status" to "ACTIVE",
                "joinedAtMs" to leaderMember.joinedAtMs
            )
            val batch = db.batch()
            batch.set(sessionRef, sessionPayload)
            batch.set(memberRef, memberPayload)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PoliceRepository", "Failed to create team duty session", e)
            Result.failure(e)
        }
    }

    suspend fun findAndJoinTeamDuty(
        rawQrOrCode: String,
        member: TeamDutyMember
    ): Result<TeamDutySession> {
        val cleaned = rawQrOrCode.trim()
        if (cleaned.isBlank()) {
            return Result.failure(IllegalArgumentException("QR කේතය හෝ කණ්ඩායම් කේතය හිස්ය."))
        }
        return try {
            var session: TeamDutySession? = null
            if (cleaned.startsWith("POLICE_TEAM_DUTY:")) {
                val parts = cleaned.split(":")
                val sid = parts.getOrNull(1)?.trim().orEmpty()
                if (sid.isNotBlank()) {
                    session = db.collection("team_duties").document(sid).get().await()
                        .toObject(TeamDutySession::class.java)
                }
            }
            if (session == null) {
                val directDoc = db.collection("team_duties").document(cleaned).get().await()
                if (directDoc.exists()) {
                    session = directDoc.toObject(TeamDutySession::class.java)
                }
            }
            if (session == null) {
                val codeQuery = db.collection("team_duties")
                    .whereEqualTo("joinCode", cleaned.uppercase())
                    .whereEqualTo("status", "ACTIVE")
                    .get()
                    .await()
                session = codeQuery.documents.firstOrNull()?.toObject(TeamDutySession::class.java)
            }
            val found = session
                ?: return Result.failure(IllegalStateException("මෙම QR/කේතයට අදාළ සක්‍රීය කණ්ඩායම් රාජකාරියක් හමු නොවීය."))
            if (found.status != "ACTIVE") {
                return Result.failure(IllegalStateException("මෙම කණ්ඩායම් රාජකාරිය දැනටමත් අවසන් කර ඇත."))
            }

            val memberPayload = mapOf(
                "uid" to member.uid,
                "displayName" to member.displayName,
                "rank" to member.rank,
                "badgeNumber" to member.badgeNumber,
                "phoneNumber" to member.phoneNumber,
                "roleInTeam" to if (member.uid == found.leaderUid) "LEADER" else "MEMBER",
                "status" to "ACTIVE",
                "joinedAtMs" to System.currentTimeMillis()
            )
            db.collection("team_duties")
                .document(found.sessionId)
                .collection("members")
                .document(member.uid)
                .set(memberPayload)
                .await()
            Result.success(found)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PoliceRepository", "Failed to join team duty", e)
            Result.failure(e)
        }
    }

    fun observeTeamDutySession(sessionId: String): Flow<TeamDutySession?> {
        if (sessionId.isBlank()) return flowOf(null)
        return db.collection("team_duties").document(sessionId)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(TeamDutySession::class.java)
            }
    }

    fun observeTeamDutyMembers(sessionId: String): Flow<List<TeamDutyMember>> {
        if (sessionId.isBlank()) return flowOf(emptyList())
        return db.collection("team_duties").document(sessionId)
            .collection("members")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(TeamDutyMember::class.java)
                    .sortedBy { it.joinedAtMs }
            }
    }

    fun observeMyTeamMembership(sessionId: String, uid: String): Flow<TeamDutyMember?> {
        if (sessionId.isBlank() || uid.isBlank()) return flowOf(null)
        return db.collection("team_duties").document(sessionId)
            .collection("members")
            .document(uid)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(TeamDutyMember::class.java)
            }
    }

    fun observeTeamTeammatesUsers(uids: List<String>): Flow<List<AppUser>> {
        val cleanUids = uids.filter { it.isNotBlank() }.distinct().take(10)
        if (cleanUids.isEmpty()) return flowOf(emptyList())
        return db.collection("users")
            .whereIn("uid", cleanUids)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(AppUser::class.java)?.let { user ->
                        val explicitOnDuty = doc.getBoolean("isOnDuty") ?: user.isOnDuty
                        user.copy(
                            uid = if (user.uid.isBlank()) doc.id else user.uid,
                            isOnDuty = explicitOnDuty
                        )
                    }
                }
            }
    }

    suspend fun endTeamDutyAsLeader(sessionId: String, leaderUid: String, endedAtMs: Long): Result<Unit> {
        return try {
            val sessionRef = db.collection("team_duties").document(sessionId)
            val memberRef = sessionRef.collection("members").document(leaderUid)
            val batch = db.batch()
            batch.update(
                sessionRef,
                mapOf(
                    "status" to "ENDED",
                    "endedAtMs" to endedAtMs
                )
            )
            batch.update(memberRef, mapOf("status" to "ENDED_BY_LEADER"))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PoliceRepository", "Failed to end team duty session", e)
            Result.failure(e)
        }
    }

    suspend fun updateMyTeamMembershipStatus(sessionId: String, uid: String, status: String) {
        if (sessionId.isBlank() || uid.isBlank()) return
        try {
            db.collection("team_duties")
                .document(sessionId)
                .collection("members")
                .document(uid)
                .update("status", status)
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PoliceRepository", "Failed to update team membership status", e)
        }
    }

    suspend fun updateDutyStatus(uid: String, isOnDuty: Boolean) {
        try {
            db.collection("users").document(uid).update(
                mapOf(
                    "isOnDuty" to isOnDuty,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PoliceRepository", "Failed to update duty status in Firestore", e)
        }
    }

    suspend fun updateLastLocation(
        uid: String,
        lat: Double,
        lng: Double,
        isOnDuty: Boolean,
        recordOffDutySpot: Boolean = false
    ) {
        try {
            val nowMs = System.currentTimeMillis()
            val updates = mutableMapOf<String, Any>(
                "isOnDuty" to isOnDuty,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (lat != 0.0 || lng != 0.0) {
                val locMap = mapOf(
                    "lat" to lat,
                    "lng" to lng,
                    "timestamp" to nowMs
                )
                updates["lastLocation"] = locMap
                if (recordOffDutySpot || !isOnDuty) {
                    updates["lastOffDutyLocation"] = locMap
                }
            }
            db.collection("users").document(uid).update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PoliceRepository", "Failed to update GPS location in Firestore", e)
        }
    }

    suspend fun addLocationBreadcrumb(point: LocationHistoryPoint) {
        if (point.officerId.isBlank() || (point.lat == 0.0 && point.lng == 0.0)) return
        try {
            val payload = mapOf(
                "officerId" to point.officerId,
                "officerName" to point.officerName,
                "oicStationCode" to point.oicStationCode,
                "dateKey" to point.dateKey,
                "dayStationKey" to "${point.oicStationCode}_${point.dateKey}",
                "officerDayKey" to "${point.officerId}_${point.dateKey}",
                "lat" to point.lat,
                "lng" to point.lng,
                "accuracy" to point.accuracy,
                "dutyType" to if (point.dutyType == "OFF_DUTY") "OFF_DUTY" else "ON_DUTY",
                "timestamp" to point.timestamp,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("location_history").document().set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PoliceRepository", "Failed to log GPS breadcrumb", e)
        }
    }

    fun observeStationLocationHistory(
        oicStationCode: String,
        dateKey: String
    ): Flow<List<LocationHistoryPoint>> {
        if (oicStationCode.isBlank() || dateKey.isBlank()) return flowOf(emptyList())
        return db.collection("location_history")
            .whereEqualTo("dayStationKey", "${oicStationCode.trim()}_${dateKey.trim()}")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(LocationHistoryPoint::class.java)
                    .sortedBy { it.timestamp }
            }
            .catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w("PoliceRepository", "Error observing station location history", e)
                emit(emptyList())
            }
    }

    fun observeOfficerLocationHistory(
        officerId: String,
        dateKey: String
    ): Flow<List<LocationHistoryPoint>> {
        if (officerId.isBlank() || dateKey.isBlank()) return flowOf(emptyList())
        return db.collection("location_history")
            .whereEqualTo("officerDayKey", "${officerId.trim()}_${dateKey.trim()}")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(LocationHistoryPoint::class.java)
                    .sortedBy { it.timestamp }
            }
            .catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w("PoliceRepository", "Error observing officer location history", e)
                emit(emptyList())
            }
    }

    fun observeStationLocationHistoryMultiDay(
        oicStationCode: String,
        sinceMs: Long
    ): Flow<List<LocationHistoryPoint>> {
        val cleanStation = oicStationCode.trim()
        if (cleanStation.isBlank()) return flowOf(emptyList())
        return db.collection("location_history")
            .whereEqualTo("oicStationCode", cleanStation)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(LocationHistoryPoint::class.java)
                    .filter { it.timestamp >= sinceMs }
                    .sortedBy { it.timestamp }
            }
            .catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w("PoliceRepository", "Error observing station multi-day location history", e)
                emit(emptyList())
            }
    }

    fun observeOfficerLocationHistoryMultiDay(
        officerId: String,
        sinceMs: Long
    ): Flow<List<LocationHistoryPoint>> {
        val cleanUid = officerId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("location_history")
            .whereEqualTo("officerId", cleanUid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(LocationHistoryPoint::class.java)
                    .filter { it.timestamp >= sinceMs }
                    .sortedBy { it.timestamp }
            }
            .catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w("PoliceRepository", "Error observing officer multi-day location history", e)
                emit(emptyList())
            }
    }

    // --- Real-Time Station Chat Room ---
    fun observeStationChatMessages(oicStationCode: String): Flow<List<StationChatMessage>> {
        val cleanStation = oicStationCode.trim().uppercase()
        if (cleanStation.isBlank()) return flowOf(emptyList())
        return db.collection("station_chats")
            .whereEqualTo("oicStationCode", cleanStation)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(StationChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt?.toDate()?.time ?: it.createdAtMs }
            }
            .catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w("PoliceRepository", "Error observing station chat messages", e)
                emit(emptyList())
            }
    }

    suspend fun sendStationChatMessage(chatMessage: StationChatMessage): Result<Unit> {
        val uid = requireUserId()
        if (uid.isBlank()) {
            return Result.failure(IllegalStateException("පණිවිඩ යැවීමට පුරනය වී සිටිය යුතුය."))
        }
        val cleanStation = chatMessage.oicStationCode.trim().uppercase()
        val cleanText = chatMessage.message.trim().take(1000)
        if (cleanStation.isBlank() || cleanText.isBlank()) {
            return Result.failure(IllegalArgumentException("කරුණාකර පණිවිඩයක් ඇතුළත් කරන්න."))
        }
        return try {
            val payload = mapOf(
                "oicStationCode" to cleanStation,
                "senderUid" to uid,
                "senderName" to chatMessage.senderName.trim().take(120).ifBlank { "නිලධාරී" },
                "senderRole" to if (chatMessage.senderRole in listOf("OIC", "ADMIN_BRANCH", "OFFICER")) chatMessage.senderRole else "OFFICER",
                "senderRank" to chatMessage.senderRank.trim().take(80),
                "senderBadge" to chatMessage.senderBadge.trim().take(40),
                "message" to cleanText,
                "createdAtMs" to System.currentTimeMillis(),
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("station_chats").document().set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PoliceRepository", "Failed to send station chat message", e)
            Result.failure(e)
        }
    }

    suspend fun sendEmergencyAlert(alert: EmergencyAlert): Result<String> {
        val uid = requireUserId()
        if (uid.isBlank()) {
            return Result.failure(IllegalStateException("හදිසි පණිවිඩ යැවීමට පුරනය වී සිටිය යුතුය."))
        }
        val cleanMsg = alert.message.trim().take(600)
        if (cleanMsg.isBlank()) {
            return Result.failure(IllegalArgumentException("කරුණාකර හදිසි පණිවිඩය ඇතුළත් කරන්න."))
        }
        return try {
            val docRef = db.collection("emergency_alerts").document()
            val payload = mapOf(
                "senderUid" to uid,
                "senderName" to alert.senderName.trim().take(120),
                "senderRank" to alert.senderRank.trim(),
                "senderBadge" to alert.senderBadge.trim(),
                "senderRole" to if (alert.senderRole in listOf("OIC", "OFFICER", "ADMIN_BRANCH")) alert.senderRole else "OFFICER",
                "senderPhone" to alert.senderPhone.trim(),
                "oicStationCode" to alert.oicStationCode.trim().uppercase(),
                "message" to cleanMsg,
                "lat" to alert.lat,
                "lng" to alert.lng,
                "radiusKm" to alert.radiusKm.coerceIn(1.0, 200.0),
                "status" to "ACTIVE",
                "createdAtMs" to alert.createdAtMs,
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PoliceRepository", "Failed to send emergency alert", e)
            Result.failure(e)
        }
    }

    suspend fun resolveEmergencyAlert(alertId: String): Result<Unit> {
        if (alertId.isBlank()) return Result.failure(IllegalArgumentException("Invalid alert ID"))
        return try {
            db.collection("emergency_alerts").document(alertId)
                .update("status", "RESOLVED")
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PoliceRepository", "Failed to resolve emergency alert", e)
            Result.failure(e)
        }
    }

    fun observeActiveEmergencyAlerts(): Flow<List<EmergencyAlert>> =
        db.collection("emergency_alerts")
            .whereEqualTo("status", "ACTIVE")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(EmergencyAlert::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: it.createdAtMs }
            }
            .catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w("PoliceRepository", "Error observing active emergency alerts", e)
                emit(emptyList())
            }

    suspend fun saveFcmToken(uid: String, token: String) {
        if (uid.isBlank() || token.isBlank()) return
        try {
            db.collection("users").document(uid).update(
                mapOf(
                    "fcmToken" to token,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PoliceRepository", "Failed to save FCM token to Firestore", e)
        }
    }

    suspend fun sendNotification(
        title: String,
        message: String,
        senderName: String,
        senderRole: String,
        targetType: String,
        targetStationCode: String = "",
        targetUid: String = "",
        priority: String = "NORMAL"
    ): Result<Unit> {
        val uid = requireUserId()
        if (uid.isBlank()) {
            return Result.failure(IllegalStateException("User must be signed in to send notifications."))
        }
        val cleanTitle = title.trim().take(120)
        val cleanMsg = message.trim().take(1000)
        if (cleanTitle.isBlank() || cleanMsg.isBlank()) {
            return Result.failure(IllegalArgumentException("Title and message must not be empty."))
        }
        return try {
            val nowMs = System.currentTimeMillis()
            val payload = mapOf(
                "title" to cleanTitle,
                "message" to cleanMsg,
                "senderUid" to uid,
                "senderName" to senderName.trim().take(100),
                "senderRole" to if (senderRole in listOf("OIC", "OFFICER", "ADMIN_BRANCH")) senderRole else "OFFICER",
                "targetType" to targetType,
                "targetStationCode" to targetStationCode.trim().uppercase(),
                "targetUid" to targetUid.trim(),
                "priority" to if (priority == "URGENT" || priority == "HIGH") "URGENT" else "NORMAL",
                "createdAtMs" to nowMs,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("notifications").document().set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PoliceRepository", "Failed to send notification", e)
            Result.failure(e)
        }
    }

    fun observeNotifications(uid: String, stationCode: String?): Flow<List<AppNotification>> {
        if (uid.isBlank()) return flowOf(emptyList())

        val allFlow = db.collection("notifications")
            .whereEqualTo("targetType", "ALL")
            .snapshots()
            .map { it.toObjects(AppNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w("PoliceRepository", "Error observing ALL notifications", e)
                emit(emptyList())
            }

        val directFlow = db.collection("notifications")
            .whereEqualTo("targetUid", uid)
            .snapshots()
            .map { it.toObjects(AppNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w("PoliceRepository", "Error observing direct notifications", e)
                emit(emptyList())
            }

        val cleanStation = stationCode?.trim()?.uppercase().orEmpty()
        val stationFlow = if (cleanStation.isNotBlank()) {
            db.collection("notifications")
                .whereEqualTo("targetType", "STATION")
                .whereEqualTo("targetStationCode", cleanStation)
                .snapshots()
                .map { it.toObjects(AppNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { e ->
                    if (e is CancellationException && e.cause == null) throw e
                    Log.w("PoliceRepository", "Error observing station notifications", e)
                    emit(emptyList())
                }
        } else {
            flowOf(emptyList())
        }

        return combine(allFlow, directFlow, stationFlow) { allList, directList, stationList ->
            (allList + directList + stationList)
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt?.toDate()?.time ?: it.createdAtMs }
        }
    }

    suspend fun deleteNotification(notificationId: String) {
        if (notificationId.isBlank()) return
        try {
            db.collection("notifications").document(notificationId).delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PoliceRepository", "Failed to delete notification", e)
        }
    }

    fun searchDutyRecords(officerId: String, query: String): Flow<List<DutyRecord>> {
        if (officerId.isBlank()) return flowOf(emptyList())
        return if (query.isBlank()) {
            dao.getAllDutyRecords(officerId)
        } else {
            dao.searchDutyRecords(officerId, query.trim())
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
        if (record.officerId.isNotBlank()) {
            try {
                val snap = db.collection("duty_records")
                    .whereEqualTo("officerId", record.officerId)
                    .whereEqualTo("timestamp", record.timestamp)
                    .get()
                    .await()
                for (doc in snap.documents) {
                    doc.reference.delete().await()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("PoliceRepository", "Failed to delete duty record from Firestore", e)
            }
        }
    }

    // --- Mess Records ---
    fun getMessRecordsForDate(officerId: String, date: String): Flow<List<MessRecord>> {
        if (officerId.isBlank()) return flowOf(emptyList())
        return dao.getMessRecordsForDate(officerId, date)
    }

    fun getMessRecordsForMonth(officerId: String, monthPrefix: String): Flow<List<MessRecord>> {
        if (officerId.isBlank()) return flowOf(emptyList())
        return dao.getMessRecordsForMonth(officerId, monthPrefix)
    }

    suspend fun toggleMealTaken(
        officerId: String,
        oicStationCode: String,
        date: String,
        mealType: String,
        isTaken: Boolean,
        defaultPrice: Double = 0.0
    ) {
        if (officerId.isBlank()) return
        val existing = dao.findMessRecord(officerId, date, mealType)
        if (existing != null) {
            dao.updateMessRecord(existing.copy(isTaken = isTaken))
        } else {
            dao.insertMessRecord(
                MessRecord(
                    officerId = officerId,
                    oicStationCode = oicStationCode,
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
        officerId: String,
        oicStationCode: String,
        date: String,
        mealType: String,
        isTaken: Boolean,
        notes: String,
        price: Double,
        photoPath: String?,
        compressedSizeKb: Int
    ) {
        if (officerId.isBlank()) return
        val existing = dao.findMessRecord(officerId, date, mealType)
        if (existing != null) {
            dao.updateMessRecord(
                existing.copy(
                    officerId = officerId,
                    oicStationCode = oicStationCode,
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
                    officerId = officerId,
                    oicStationCode = oicStationCode,
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
        if (record.officerId.isNotBlank()) {
            try {
                val snap = db.collection("mess_records")
                    .whereEqualTo("officerId", record.officerId)
                    .whereEqualTo("date", record.date)
                    .whereEqualTo("mealType", record.mealType)
                    .get()
                    .await()
                for (doc in snap.documents) {
                    doc.reference.delete().await()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("PoliceRepository", "Failed to delete mess record from Firestore", e)
            }
        }
    }

    // --- Running Chart Records ---
    fun getAllRunningChartRecords(officerId: String): Flow<List<RunningChartRecord>> =
        if (officerId.isBlank()) flowOf(emptyList()) else dao.getAllRunningChartRecords(officerId)

    fun getDistinctVehicles(officerId: String): Flow<List<String>> =
        if (officerId.isBlank()) flowOf(emptyList()) else dao.getDistinctVehicles(officerId)

    fun getRunningChartRecordsByVehicle(officerId: String, vehicle: String): Flow<List<RunningChartRecord>> {
        if (officerId.isBlank()) return flowOf(emptyList())
        return if (vehicle.isBlank() || vehicle == "ALL" || vehicle == "සියල්ල") {
            dao.getAllRunningChartRecords(officerId)
        } else {
            dao.getRunningChartRecordsByVehicle(officerId, vehicle)
        }
    }

    fun getRunningChartRecordsForMonth(officerId: String, monthPrefix: String): Flow<List<RunningChartRecord>> {
        if (officerId.isBlank()) return flowOf(emptyList())
        return dao.getRunningChartRecordsForMonth(officerId, monthPrefix)
    }

    suspend fun getLatestRecordForVehicle(officerId: String, vehicle: String): RunningChartRecord? {
        if (officerId.isBlank()) return null
        return dao.getLatestRecordForVehicle(officerId, vehicle)
    }

    suspend fun insertRunningChartRecord(record: RunningChartRecord): Long {
        return dao.insertRunningChartRecord(record)
    }

    suspend fun updateRunningChartRecord(record: RunningChartRecord) {
        dao.updateRunningChartRecord(record)
    }

    suspend fun deleteRunningChartRecord(record: RunningChartRecord) {
        dao.deleteRunningChartRecord(record.id)
        if (record.officerId.isNotBlank()) {
            try {
                val snap = db.collection("running_chart_records")
                    .whereEqualTo("officerId", record.officerId)
                    .whereEqualTo("timestamp", record.timestamp)
                    .get()
                    .await()
                for (doc in snap.documents) {
                    doc.reference.delete().await()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("PoliceRepository", "Failed to delete running chart record from Firestore", e)
            }
        }
    }

    /**
     * Imports ONLY this officer's own records from Firestore when Room is empty for this UID
     * (for example after reinstalling or signing into a new device). Never touches other users' records.
     */
    suspend fun hydrateOfficerLocalDataFromFirestoreIfEmpty(officerId: String) {
        if (officerId.isBlank()) return
        try {
            if (dao.getDutyCountForOfficer(officerId) == 0) {
                val dutySnap = db.collection("duty_records")
                    .whereEqualTo("officerId", officerId)
                    .get()
                    .await()
                val duties = dutySnap.toObjects(DutyRecord::class.java)
                    .filter { it.officerId == officerId }
                for (d in duties) {
                    dao.insertDutyRecord(
                        d.copy(
                            id = 0,
                            photoPath = d.photoPath?.takeIf { File(it).exists() },
                            scenePhotoPath = d.scenePhotoPath?.takeIf { File(it).exists() }
                        )
                    )
                }
            }

            if (dao.getMessCountForOfficer(officerId) == 0) {
                val messSnap = db.collection("mess_records")
                    .whereEqualTo("officerId", officerId)
                    .get()
                    .await()
                for (doc in messSnap.documents) {
                    val m = doc.toObject(MessRecord::class.java) ?: continue
                    if (m.officerId != officerId) continue
                    val explicitTaken = doc.getBoolean("isTaken") ?: m.isTaken
                    dao.insertMessRecord(
                        m.copy(
                            id = 0,
                            isTaken = explicitTaken,
                            photoPath = m.photoPath?.takeIf { File(it).exists() }
                        )
                    )
                }
            }

            if (dao.getRunningChartCountForOfficer(officerId) == 0) {
                val rcSnap = db.collection("running_chart_records")
                    .whereEqualTo("officerId", officerId)
                    .get()
                    .await()
                val records = rcSnap.toObjects(RunningChartRecord::class.java)
                    .filter { it.officerId == officerId }
                for (r in records) {
                    dao.insertRunningChartRecord(r.copy(id = 0))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PoliceRepository", "Failed to hydrate officer local data from Firestore", e)
        }
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
