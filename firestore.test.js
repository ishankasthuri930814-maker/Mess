const { initializeTestEnvironment, assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("fs");

const PROJECT_ID = "gen-lang-client-0104297205";
const DATABASE_ID = "ai-studio-android-policedu-d11e543c-dd7c-4ec6-9686-cb3346787965";

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host: "127.0.0.1",
      port: 8085,
      rules: fs.readFileSync("firestore.rules", "utf8"),
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

test("Officer can create their own profile", async () => {
  const alice = testEnv.authenticatedContext("alice", { email: "alice@police.lk" });
  await assertSucceeds(
    alice.firestore().collection("users").doc("alice").set({
      uid: "alice",
      displayName: "Alice",
      email: "alice@police.lk",
      role: "OFFICER",
      status: "PENDING",
      oicEmail: "oic@police.lk"
    })
  );
});

test("Officer cannot create profile for someone else", async () => {
  const alice = testEnv.authenticatedContext("alice", { email: "alice@police.lk" });
  await assertFails(
    alice.firestore().collection("users").doc("bob").set({
      uid: "bob",
      displayName: "Bob",
      email: "bob@police.lk",
      role: "OFFICER",
      status: "PENDING"
    })
  );
});

test("OIC can approve subordinate", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc("oic_user").set({
      uid: "oic_user",
      displayName: "OIC",
      email: "oic@police.lk",
      role: "OIC",
      status: "APPROVED"
    });
    await db.collection("users").doc("alice").set({
      uid: "alice",
      displayName: "Alice",
      email: "alice@police.lk",
      role: "OFFICER",
      status: "PENDING",
      oicEmail: "oic@police.lk"
    });
  });

  const oic = testEnv.authenticatedContext("oic_user", { email: "oic@police.lk" });
  await assertSucceeds(
    oic.firestore().collection("users").doc("alice").update({
      status: "APPROVED"
    })
  );
});

test("OIC cannot change other fields of subordinate", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc("oic_user").set({
      uid: "oic_user",
      displayName: "OIC",
      email: "oic@police.lk",
      role: "OIC",
      status: "APPROVED"
    });
    await db.collection("users").doc("alice").set({
      uid: "alice",
      displayName: "Alice",
      email: "alice@police.lk",
      role: "OFFICER",
      status: "PENDING",
      oicEmail: "oic@police.lk"
    });
  });

  const oic = testEnv.authenticatedContext("oic_user", { email: "oic@police.lk" });
  await assertFails(
    oic.firestore().collection("users").doc("alice").update({
      displayName: "Hacker Alice"
    })
  );
});

test("User can update their own fcmToken", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc("alice").set({
      uid: "alice",
      displayName: "Alice",
      email: "alice@police.lk",
      role: "OFFICER",
      status: "APPROVED",
      oicStationCode: "OIC-COL-1001"
    });
  });

  const alice = testEnv.authenticatedContext("alice", { email: "alice@police.lk" });
  await assertSucceeds(
    alice.firestore().collection("users").doc("alice").update({
      fcmToken: "sample_fcm_token_xyz",
      updatedAt: new Date(Date.now() - 1000)
    })
  );
});

test("Unauthenticated user cannot read or create notifications", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("notifications").where("targetType", "==", "ALL").get());
  await assertFails(
    unauthDb.collection("notifications").doc("n1").set({
      title: "Test",
      message: "Hello",
      senderUid: "alice",
      senderName: "Alice",
      senderRole: "OIC",
      targetType: "ALL",
      priority: "NORMAL",
      createdAtMs: Date.now() - 1000,
      createdAt: new Date(Date.now() - 1000)
    })
  );
});

test("OIC can broadcast ALL and STATION notifications; officer queries succeed and isolation holds", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc("oic_col").set({
      uid: "oic_col",
      displayName: "OIC Colombo",
      email: "oic@police.lk",
      role: "OIC",
      status: "APPROVED",
      oicStationCode: "OIC-COL-1001"
    });
    await db.collection("users").doc("alice").set({
      uid: "alice",
      displayName: "Alice",
      email: "alice@police.lk",
      role: "OFFICER",
      status: "APPROVED",
      oicStationCode: "OIC-COL-1001"
    });
    await db.collection("users").doc("bob").set({
      uid: "bob",
      displayName: "Bob",
      email: "bob@police.lk",
      role: "OFFICER",
      status: "APPROVED",
      oicStationCode: "OIC-KND-9999"
    });
  });

  const oicDb = testEnv.authenticatedContext("oic_col").firestore();
  const aliceDb = testEnv.authenticatedContext("alice").firestore();
  const bobDb = testEnv.authenticatedContext("bob").firestore();
  const validTime = new Date(Date.now() - 1000);

  // OIC creates ALL notification
  await assertSucceeds(
    oicDb.collection("notifications").doc("n_all").set({
      title: "HQ Notice",
      message: "General briefing at 08:00",
      senderUid: "oic_col",
      senderName: "OIC Colombo",
      senderRole: "OIC",
      targetType: "ALL",
      targetStationCode: "",
      targetUid: "",
      priority: "URGENT",
      createdAtMs: Date.now() - 1000,
      createdAt: validTime
    })
  );

  // OIC creates STATION notification
  await assertSucceeds(
    oicDb.collection("notifications").doc("n_station").set({
      title: "Station Parade",
      message: "Colombo unit parade",
      senderUid: "oic_col",
      senderName: "OIC Colombo",
      senderRole: "OIC",
      targetType: "STATION",
      targetStationCode: "OIC-COL-1001",
      targetUid: "",
      priority: "NORMAL",
      createdAtMs: Date.now() - 1000,
      createdAt: validTime
    })
  );

  // OIC creates direct USER notification to Alice
  await assertSucceeds(
    oicDb.collection("notifications").doc("n_alice").set({
      title: "Special Duty",
      message: "Report to traffic desk",
      senderUid: "oic_col",
      senderName: "OIC Colombo",
      senderRole: "OIC",
      targetType: "USER",
      targetStationCode: "OIC-COL-1001",
      targetUid: "alice",
      priority: "URGENT",
      createdAtMs: Date.now() - 1000,
      createdAt: validTime
    })
  );

  // Officer cannot broadcast ALL
  await assertFails(
    aliceDb.collection("notifications").doc("n_bad").set({
      title: "Spam",
      message: "Unauthorized broadcast",
      senderUid: "alice",
      senderName: "Alice",
      senderRole: "OFFICER",
      targetType: "ALL",
      priority: "NORMAL",
      createdAtMs: Date.now() - 1000,
      createdAt: validTime
    })
  );

  // Future timestamp rejected
  await assertFails(
    oicDb.collection("notifications").doc("n_future").set({
      title: "Future",
      message: "Tampered timestamp",
      senderUid: "oic_col",
      senderName: "OIC Colombo",
      senderRole: "OIC",
      targetType: "ALL",
      priority: "NORMAL",
      createdAtMs: Date.now() + 60000,
      createdAt: new Date(Date.now() + 60000)
    })
  );

  // Alice can query ALL, STATION (OIC-COL-1001), and USER (alice)
  await assertSucceeds(aliceDb.collection("notifications").where("targetType", "==", "ALL").get());
  await assertSucceeds(
    aliceDb.collection("notifications")
      .where("targetType", "==", "STATION")
      .where("targetStationCode", "==", "OIC-COL-1001")
      .get()
  );
  await assertSucceeds(aliceDb.collection("notifications").where("targetUid", "==", "alice").get());

  // Bob (in OIC-KND-9999) cannot read Colombo station notification or Alice's direct notification
  await assertFails(bobDb.collection("notifications").doc("n_station").get());
  await assertFails(bobDb.collection("notifications").doc("n_alice").get());
  await assertFails(
    bobDb.collection("notifications")
      .where("targetType", "==", "STATION")
      .where("targetStationCode", "==", "OIC-COL-1001")
      .get()
  );
});

test("Officer can save phoneNumber/address, running chart, and team duty QR session with members", async () => {
  const aliceDb = testEnv.authenticatedContext("alice").firestore();
  const bobDb = testEnv.authenticatedContext("bob").firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc("alice").set({
      uid: "alice",
      displayName: "PC Alice",
      email: "alice@police.lk",
      role: "OFFICER",
      status: "APPROVED",
      oicStationCode: "OIC-COL-1001",
      badgeNumber: "81234",
      rank: "PC",
      phoneNumber: "0771234567",
      address: "No 12, Galle Road, Colombo",
      isOnDuty: true
    })
  );

  await assertSucceeds(
    aliceDb.collection("running_chart_records").doc("rc1").set({
      officerId: "alice",
      oicStationCode: "OIC-COL-1001",
      date: "2026-11-17",
      vehicleNumber: "KX-1493",
      startMeter: 1000,
      endMeter: 1050,
      distanceKm: 50,
      kmPerLiter: 12,
      startFuel: 20,
      addedFuel: 0,
      consumedFuel: 4.16,
      remainingFuel: 15.84,
      notes: "Night patrol",
      timestamp: Date.now()
    })
  );

  await assertSucceeds(
    aliceDb.collection("team_duties").doc("sess1").set({
      sessionId: "sess1",
      joinCode: "TEAM99",
      leaderUid: "alice",
      leaderName: "PC Alice",
      leaderBadge: "81234",
      leaderRank: "PC",
      oicStationCode: "OIC-COL-1001",
      dutyNature: "Mobile Patrol",
      ibPage: "45",
      ibParagraph: "2",
      remarks: "Special patrol",
      status: "ACTIVE",
      startedAtMs: Date.now(),
      endedAtMs: 0
    })
  );

  // Bob joins Alice's team duty as a member
  await assertSucceeds(
    bobDb.collection("team_duties").doc("sess1").collection("members").doc("bob").set({
      uid: "bob",
      displayName: "PC Bob",
      rank: "PC",
      badgeNumber: "81235",
      phoneNumber: "0719876543",
      roleInTeam: "MEMBER",
      status: "ACTIVE",
      joinedAtMs: Date.now()
    })
  );

  // Bob cannot end Alice's session
  await assertFails(
    bobDb.collection("team_duties").doc("sess1").update({ status: "ENDED" })
  );

  // Leader Alice can end session for everyone
  await assertSucceeds(
    aliceDb.collection("team_duties").doc("sess1").update({
      status: "ENDED",
      endedAtMs: Date.now()
    })
  );
});

test("Location history breadcrumbs, lastOffDutyLocation, and Emergency SOS alerts enforce ownership and OIC resolve", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc("oic_col").set({
      uid: "oic_col",
      displayName: "OIC Colombo",
      email: "oic@police.lk",
      role: "OIC",
      status: "APPROVED",
      oicStationCode: "OIC-COL-1001"
    });
    await db.collection("users").doc("alice").set({
      uid: "alice",
      displayName: "PC Alice",
      email: "alice@police.lk",
      role: "OFFICER",
      status: "APPROVED",
      oicStationCode: "OIC-COL-1001",
      isOnDuty: true
    });
    await db.collection("users").doc("bob").set({
      uid: "bob",
      displayName: "PC Bob",
      email: "bob@police.lk",
      role: "OFFICER",
      status: "APPROVED",
      oicStationCode: "OIC-COL-1001",
      isOnDuty: true
    });
  });

  const oicDb = testEnv.authenticatedContext("oic_col").firestore();
  const aliceDb = testEnv.authenticatedContext("alice").firestore();
  const bobDb = testEnv.authenticatedContext("bob").firestore();
  const validTime = new Date(Date.now() - 1000);

  // Alice updates lastOffDutyLocation when going off duty
  await assertSucceeds(
    aliceDb.collection("users").doc("alice").update({
      isOnDuty: false,
      lastOffDutyLocation: { lat: 6.9271, lng: 79.8612, timestamp: Date.now() }
    })
  );

  // Alice records GPS trail breadcrumb
  await assertSucceeds(
    aliceDb.collection("location_history").doc("loc1").set({
      officerId: "alice",
      officerName: "PC Alice",
      oicStationCode: "OIC-COL-1001",
      dateKey: "2026-11-17",
      dayStationKey: "OIC-COL-1001_2026-11-17",
      officerDayKey: "alice_2026-11-17",
      lat: 6.9271,
      lng: 79.8612,
      accuracy: 8.5,
      dutyType: "ON_DUTY",
      timestamp: Date.now(),
      createdAt: validTime
    })
  );

  // Bob cannot spoof Alice's location history
  await assertFails(
    bobDb.collection("location_history").doc("loc_spoof").set({
      officerId: "alice",
      officerName: "PC Alice",
      oicStationCode: "OIC-COL-1001",
      dateKey: "2026-11-17",
      dayStationKey: "OIC-COL-1001_2026-11-17",
      officerDayKey: "alice_2026-11-17",
      lat: 6.93,
      lng: 79.86,
      dutyType: "ON_DUTY",
      timestamp: Date.now(),
      createdAt: validTime
    })
  );

  // OIC queries station breadcrumbs
  await assertSucceeds(
    oicDb.collection("location_history").where("dayStationKey", "==", "OIC-COL-1001_2026-11-17").get()
  );

  // Alice sends Emergency SOS
  await assertSucceeds(
    aliceDb.collection("emergency_alerts").doc("sos1").set({
      senderUid: "alice",
      senderName: "PC Alice",
      senderRank: "PC",
      senderBadge: "81234",
      senderRole: "OFFICER",
      senderPhone: "0771234567",
      oicStationCode: "OIC-COL-1001",
      message: "Need immediate backup at Galle Face!",
      lat: 6.9271,
      lng: 79.8612,
      radiusKm: 10,
      status: "ACTIVE",
      createdAtMs: Date.now() - 1000,
      createdAt: validTime
    })
  );

  // Bob cannot resolve Alice's SOS (Bob is not OIC or sender)
  await assertFails(
    bobDb.collection("emergency_alerts").doc("sos1").update({ status: "RESOLVED" })
  );

  // OIC can resolve Alice's SOS
  await assertSucceeds(
    oicDb.collection("emergency_alerts").doc("sos1").update({ status: "RESOLVED" })
  );
});

test("OIC can remove station member, Admin Branch can send ALL notification, and station_chats enforces station isolation", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc("oic_col").set({
      uid: "oic_col",
      displayName: "OIC Colombo",
      email: "oic@police.lk",
      role: "OIC",
      status: "APPROVED",
      oicStationCode: "OIC-COL-1001"
    });
    await db.collection("users").doc("admin_col").set({
      uid: "admin_col",
      displayName: "Admin Colombo",
      email: "admin@police.lk",
      role: "ADMIN_BRANCH",
      status: "APPROVED",
      oicStationCode: "OIC-COL-1001"
    });
    await db.collection("users").doc("alice").set({
      uid: "alice",
      displayName: "PC Alice",
      email: "alice@police.lk",
      role: "OFFICER",
      status: "APPROVED",
      oicStationCode: "OIC-COL-1001",
      isOnDuty: true
    });
    await db.collection("users").doc("kandy_user").set({
      uid: "kandy_user",
      displayName: "PC Kandy",
      email: "kandy@police.lk",
      role: "OFFICER",
      status: "APPROVED",
      oicStationCode: "OIC-KND-9999"
    });
  });

  const oicDb = testEnv.authenticatedContext("oic_col").firestore();
  const adminDb = testEnv.authenticatedContext("admin_col").firestore();
  const aliceDb = testEnv.authenticatedContext("alice").firestore();
  const kandyDb = testEnv.authenticatedContext("kandy_user").firestore();
  const validTime = new Date(Date.now() - 1000);

  // Admin Branch can broadcast ALL notice
  await assertSucceeds(
    adminDb.collection("notifications").doc("n_admin_all").set({
      title: "Admin Duty Notice",
      message: "Roster updated for next week",
      senderUid: "admin_col",
      senderName: "Admin Colombo",
      senderRole: "ADMIN_BRANCH",
      targetType: "ALL",
      targetStationCode: "",
      targetUid: "",
      priority: "NORMAL",
      createdAtMs: Date.now() - 1000,
      createdAt: validTime
    })
  );

  // Officer can update own stealth flags
  await assertSucceeds(
    aliceDb.collection("users").doc("alice").update({
      hiddenFromStationLists: true,
      hiddenOnMap: true,
      pauseLocationLogging: true,
      hideDutyStatus: true
    })
  );

  // Station chat creation & isolation
  await assertSucceeds(
    aliceDb.collection("station_chats").doc("msg1").set({
      oicStationCode: "OIC-COL-1001",
      senderUid: "alice",
      senderName: "PC Alice",
      senderRole: "OFFICER",
      senderRank: "Police Constable (PC)",
      senderBadge: "81234",
      message: "Patrol unit reporting at Galle Road.",
      createdAtMs: Date.now() - 1000,
      createdAt: validTime
    })
  );

  await assertSucceeds(
    oicDb.collection("station_chats").where("oicStationCode", "==", "OIC-COL-1001").get()
  );

  // Kandy officer cannot read Colombo station chat
  await assertFails(
    kandyDb.collection("station_chats").doc("msg1").get()
  );
  await assertFails(
    kandyDb.collection("station_chats").where("oicStationCode", "==", "OIC-COL-1001").get()
  );

  // Alice can send a <10MB PDF attachment message + chunk, and same-station Admin/OIC can read it
  const expiresFuture = Date.now() + 2 * 24 * 60 * 60 * 1000;
  await assertSucceeds(
    aliceDb.collection("station_chats").doc("msg_pdf").collection("chunks").doc("0000").set({
      chunkIndex: 0,
      oicStationCode: "OIC-COL-1001",
      senderUid: "alice",
      expiresAtMs: expiresFuture,
      data: "JVBERi0xLjQK"
    })
  );
  await assertSucceeds(
    aliceDb.collection("station_chats").doc("msg_pdf").set({
      oicStationCode: "OIC-COL-1001",
      senderUid: "alice",
      senderName: "PC Alice",
      senderRole: "OFFICER",
      senderRank: "Police Constable (PC)",
      senderBadge: "81234",
      message: "",
      messageType: "PDF",
      attachmentName: "duty_order.pdf",
      attachmentMimeType: "application/pdf",
      attachmentSizeBytes: 204800,
      attachmentDurationMs: 0,
      attachmentChunkCount: 1,
      expiresAtMs: expiresFuture,
      createdAtMs: Date.now() - 1000,
      createdAt: validTime
    })
  );

  await assertSucceeds(
    adminDb.collection("station_chats").doc("msg_pdf").collection("chunks").doc("0000").get()
  );
  // Station-scoped chunks list query (matching Kotlin downloadChatAttachmentToCache) succeeds for sender & station members
  await assertSucceeds(
    aliceDb.collection("station_chats").doc("msg_pdf").collection("chunks")
      .where("oicStationCode", "==", "OIC-COL-1001")
      .get()
  );
  await assertSucceeds(
    adminDb.collection("station_chats").doc("msg_pdf").collection("chunks")
      .where("oicStationCode", "==", "OIC-COL-1001")
      .get()
  );
  // Unfiltered chunks list query is rejected by Firestore security rules
  await assertFails(
    adminDb.collection("station_chats").doc("msg_pdf").collection("chunks").get()
  );
  // Cross-station officer cannot read chunks by doc get or filtered list query
  await assertFails(
    kandyDb.collection("station_chats").doc("msg_pdf").collection("chunks").doc("0000").get()
  );
  await assertFails(
    kandyDb.collection("station_chats").doc("msg_pdf").collection("chunks")
      .where("oicStationCode", "==", "OIC-COL-1001")
      .get()
  );

  // Attachment > 10MB (10,485,760 bytes) is rejected
  await assertFails(
    aliceDb.collection("station_chats").doc("msg_too_big").set({
      oicStationCode: "OIC-COL-1001",
      senderUid: "alice",
      senderName: "PC Alice",
      senderRole: "OFFICER",
      message: "",
      messageType: "FILE",
      attachmentName: "huge.bin",
      attachmentMimeType: "application/octet-stream",
      attachmentSizeBytes: 10485761,
      attachmentChunkCount: 30,
      expiresAtMs: expiresFuture,
      createdAtMs: Date.now() - 1000,
      createdAt: validTime
    })
  );

  // Station member (Admin) can query & auto-delete expired (>48h old) message & its chunks, but NOT unexpired messages from Alice
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    const expiredAt = Date.now() - 60000;
    await db.collection("station_chats").doc("msg_expired").set({
      oicStationCode: "OIC-COL-1001",
      senderUid: "alice",
      senderName: "PC Alice",
      senderRole: "OFFICER",
      message: "Old chat",
      messageType: "TEXT",
      expiresAtMs: expiredAt,
      createdAtMs: expiredAt - 172800000
    });
    await db.collection("station_chats").doc("msg_expired").collection("chunks").doc("0000").set({
      chunkIndex: 0,
      oicStationCode: "OIC-COL-1001",
      senderUid: "alice",
      expiresAtMs: expiredAt,
      data: "AAAA"
    });
  });

  await assertFails(
    adminDb.collection("station_chats").doc("msg_pdf").delete()
  );
  await assertSucceeds(
    adminDb.collection("station_chats").doc("msg_expired").collection("chunks")
      .where("oicStationCode", "==", "OIC-COL-1001")
      .get()
  );
  await assertSucceeds(
    adminDb.collection("station_chats").doc("msg_expired").collection("chunks").doc("0000").delete()
  );
  await assertSucceeds(
    adminDb.collection("station_chats").doc("msg_expired").delete()
  );

  // OIC can remove Alice from station
  await assertSucceeds(
    oicDb.collection("users").doc("alice").update({
      oicStationCode: "",
      status: "REMOVED",
      isOnDuty: false,
      updatedAt: validTime
    })
  );
});

test("OIC and on-duty subordinate enforce strict privacy and permission-scoped rules on live_monitors", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc("oic_col").set({
      uid: "oic_col",
      displayName: "OIC Colombo",
      email: "oic@police.lk",
      role: "OIC",
      status: "APPROVED",
      oicStationCode: "OIC-COL-1001"
    });
    await db.collection("users").doc("alice").set({
      uid: "alice",
      displayName: "PC Alice",
      email: "alice@police.lk",
      role: "OFFICER",
      status: "APPROVED",
      oicStationCode: "OIC-COL-1001",
      isOnDuty: true,
      hasCameraPermission: true,
      hasMicPermission: true
    });
    await db.collection("users").doc("bob").set({
      uid: "bob",
      displayName: "PC Bob",
      email: "bob@police.lk",
      role: "OFFICER",
      status: "APPROVED",
      oicStationCode: "OIC-COL-1001",
      isOnDuty: true
    });
    await db.collection("users").doc("oic_knd").set({
      uid: "oic_knd",
      displayName: "OIC Kandy",
      email: "oicknd@police.lk",
      role: "OIC",
      status: "APPROVED",
      oicStationCode: "OIC-KND-9999"
    });
  });

  const oicDb = testEnv.authenticatedContext("oic_col").firestore();
  const aliceDb = testEnv.authenticatedContext("alice").firestore();
  const bobDb = testEnv.authenticatedContext("bob").firestore();
  const kandyOicDb = testEnv.authenticatedContext("oic_knd").firestore();
  const now = Date.now() - 1000;
  const validTime = new Date(now);

  // Alice can sync her runtime permission flags
  await assertSucceeds(
    aliceDb.collection("users").doc("alice").update({
      hasCameraPermission: true,
      hasMicPermission: false
    })
  );

  // Kandy OIC cannot create a live monitor session for Colombo officer Alice
  await assertFails(
    kandyOicDb.collection("live_monitors").doc("lm_cross").set({
      sessionId: "lm_cross",
      oicStationCode: "OIC-COL-1001",
      oicUid: "oic_knd",
      oicName: "OIC Kandy",
      officerUid: "alice",
      officerName: "PC Alice",
      status: "REQUESTED",
      requestedCamera: true,
      requestedAudio: true,
      cameraActive: false,
      micActive: false,
      lensFacing: "BACK",
      createdAtMs: now,
      updatedAtMs: now,
      expiresAtMs: now + 600000,
      createdAt: validTime
    })
  );

  // Non-OIC Bob cannot initiate a live monitor session
  await assertFails(
    bobDb.collection("live_monitors").doc("lm_bob").set({
      sessionId: "lm_bob",
      oicStationCode: "OIC-COL-1001",
      oicUid: "bob",
      oicName: "PC Bob",
      officerUid: "alice",
      officerName: "PC Alice",
      status: "REQUESTED",
      requestedCamera: true,
      requestedAudio: false,
      cameraActive: false,
      micActive: false,
      lensFacing: "BACK",
      createdAtMs: now,
      updatedAtMs: now,
      expiresAtMs: now + 600000,
      createdAt: validTime
    })
  );

  // Colombo OIC creates valid live monitor session for Alice
  await assertSucceeds(
    oicDb.collection("live_monitors").doc("lm_1").set({
      sessionId: "lm_1",
      oicStationCode: "OIC-COL-1001",
      oicUid: "oic_col",
      oicName: "OIC Colombo",
      officerUid: "alice",
      officerName: "PC Alice",
      status: "REQUESTED",
      requestedCamera: true,
      requestedAudio: true,
      cameraActive: false,
      micActive: false,
      lensFacing: "BACK",
      createdAtMs: now,
      updatedAtMs: now,
      expiresAtMs: now + 600000,
      createdAt: validTime
    })
  );

  // Alice accepts and marks stream ACTIVE, then publishes a video slot and audio chunk
  await assertSucceeds(
    aliceDb.collection("live_monitors").doc("lm_1").update({
      status: "ACTIVE",
      cameraActive: true,
      micActive: true,
      updatedAtMs: Date.now()
    })
  );
  await assertSucceeds(
    aliceDb.collection("live_monitors").doc("lm_1").collection("video_slots").doc("slot_0").set({
      slotIndex: 0,
      seq: 1,
      jpegBase64: "/9j/4AAQSkZJRg==",
      width: 480,
      height: 360,
      timestampMs: Date.now(),
      officerUid: "alice",
      oicUid: "oic_col",
      oicStationCode: "OIC-COL-1001"
    })
  );
  await assertSucceeds(
    aliceDb.collection("live_monitors").doc("lm_1").collection("audio_chunks").doc("slot_0").set({
      slotIndex: 0,
      seq: 1,
      adpcmBase64: "AAAA",
      sampleRate: 16000,
      sampleCount: 4000,
      timestampMs: Date.now(),
      officerUid: "alice",
      oicUid: "oic_col",
      oicStationCode: "OIC-COL-1001"
    })
  );

  // OIC can read video slot, audio chunk (both doc get & oicUid-filtered list queries), switch lensFacing, and end session
  await assertSucceeds(
    oicDb.collection("live_monitors").doc("lm_1").collection("video_slots").doc("slot_0").get()
  );
  await assertSucceeds(
    oicDb.collection("live_monitors").doc("lm_1").collection("video_slots")
      .where("oicUid", "==", "oic_col")
      .get()
  );
  await assertSucceeds(
    oicDb.collection("live_monitors").doc("lm_1").collection("audio_chunks").doc("slot_0").get()
  );
  await assertSucceeds(
    oicDb.collection("live_monitors").doc("lm_1").collection("audio_chunks")
      .where("oicUid", "==", "oic_col")
      .get()
  );
  // Unfiltered list queries on video_slots / audio_chunks must be rejected
  await assertFails(
    oicDb.collection("live_monitors").doc("lm_1").collection("video_slots").get()
  );
  await assertFails(
    oicDb.collection("live_monitors").doc("lm_1").collection("audio_chunks").get()
  );
  await assertSucceeds(
    oicDb.collection("live_monitors").doc("lm_1").update({
      lensFacing: "FRONT",
      updatedAtMs: Date.now()
    })
  );

  // Another officer (Bob) CANNOT read Alice's live session, video frame, or audio chunk
  await assertFails(bobDb.collection("live_monitors").doc("lm_1").get());
  await assertFails(bobDb.collection("live_monitors").doc("lm_1").collection("video_slots").doc("slot_0").get());
  await assertFails(
    bobDb.collection("live_monitors").doc("lm_1").collection("video_slots")
      .where("oicUid", "==", "oic_col")
      .get()
  );
  await assertFails(bobDb.collection("live_monitors").doc("lm_1").collection("audio_chunks").doc("slot_0").get());
  await assertFails(
    bobDb.collection("live_monitors").doc("lm_1").collection("audio_chunks")
      .where("oicUid", "==", "oic_col")
      .get()
  );

  // OIC ends the session cleanly
  await assertSucceeds(
    oicDb.collection("live_monitors").doc("lm_1").update({
      status: "ENDED",
      updatedAtMs: Date.now()
    })
  );
});





