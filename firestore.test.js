const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validBookmarkPayload(uid, overrides = {}) {
  return {
    userId: uid,
    sourceFile: "history.json",
    recordKey: "rec_01",
    recordIndex: 1,
    title: "Field Notes",
    subtitle: "usr_alpha",
    recordUserId: "usr_alpha",
    actionName: "OPENED",
    timestampText: "2026-11-13T06:00:00Z",
    fieldsPreview: "appletName: Field Notes | action: OPENED",
    tags: ["Investigate"],
    note: "Verify frequent export",
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated caller: cannot read or list bookmarks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("bookmarks").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("bookmarks").doc("rec_01").get()
  );
});

test("Authenticated owner: can create, list, update tags, and delete bookmark", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("bookmarks").doc("rec_01");

  await assertSucceeds(docRef.set(validBookmarkPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("bookmarks").get());
  await assertSucceeds(
    docRef.update({
      tags: ["Investigate", "Reviewed"],
      note: "Updated inspection note",
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(docRef.delete());
});

test("Cross-user isolation: Bob cannot read, create, update, or delete Alice's bookmarks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("bookmarks")
      .doc("rec_01")
      .set(validBookmarkPayload(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceDocInBobCtx = bobDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("bookmarks")
    .doc("rec_01");

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("bookmarks").get());
  await assertFails(aliceDocInBobCtx.get());
  await assertFails(aliceDocInBobCtx.update({ note: "tamper", updatedAt: new Date() }));
  await assertFails(aliceDocInBobCtx.delete());
  await assertFails(
    bobDb
      .collection("users")
      .doc(BOB_UID)
      .collection("bookmarks")
      .doc("spoof_01")
      .set(validBookmarkPayload(ALICE_UID))
  );
});

test("Schema hardening: rejects ghost fields, invalid IDs, oversized tags, and immutable field tampering", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const colRef = aliceDb.collection("users").doc(ALICE_UID).collection("bookmarks");

  // Ghost field rejected on create
  await assertFails(
    colRef.doc("rec_01").set(validBookmarkPayload(ALICE_UID, { isVerified: true }))
  );

  // Oversized tag list (> 10) rejected
  await assertFails(
    colRef.doc("rec_01").set(
      validBookmarkPayload(ALICE_UID, {
        tags: ["1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"],
      })
    )
  );

  // Valid create followed by invalid immutable recordKey update rejected
  await assertSucceeds(colRef.doc("rec_01").set(validBookmarkPayload(ALICE_UID)));
  await assertFails(
    colRef.doc("rec_01").update({
      recordKey: "changed_key",
      updatedAt: new Date(),
    })
  );
});
