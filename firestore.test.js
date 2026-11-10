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
