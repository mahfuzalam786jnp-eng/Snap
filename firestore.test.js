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
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("reels").where("moderationStatus", "==", "approved").get());
  await assertFails(unauthDb.collection("chats").where("memberUids", "array-contains", ALICE_UID).get());
  await assertFails(
    unauthDb.collection("reels").doc("reel_1").set({
      creatorId: ALICE_UID,
      creatorUsername: "alice",
      videoUrl: "https://example.com/v.mp4",
      caption: "hi",
      hashtags: [],
      musicTitle: "Pulse",
      musicArtist: "SNAP Audio",
      musicLicenseId: "CC0-01",
      durationSeconds: 15,
      visibility: "public",
      moderationStatus: "approved",
      copyrightVerified: true,
      likesCount: 0,
      commentsCount: 0,
      sharesCount: 0,
      savesCount: 0,
      viewsCount: 0,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("User profiles & private PII isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice_snap",
      displayName: "Alice",
      bio: "Creator",
      privateAccount: false,
      discoverable: true,
      followersCount: 0,
      followingCount: 0,
      reelsCount: 0,
      blockedUserIds: [],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Private PII doc
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("info").set({
      userId: ALICE_UID,
      email: "alice@example.com",
      readReceipts: true,
      lastSeenVisible: true,
      autoPlayNextReel: true,
      videoQuality: "auto",
      dataSaver: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob cannot read Alice's private PII or modify Alice's bio
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("private").doc("info").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      bio: "Hacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Bob can read Alice's discoverable public profile & query discoverable users
  await assertSucceeds(bobDb.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(bobDb.collection("users").where("discoverable", "==", true).get());
  // Unfiltered users list fails
  await assertFails(bobDb.collection("users").get());
});

test("SNAP Chats & Messages enforce memberUids scoping", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_ab").set({
      title: "Alice & Bob",
      groupChat: false,
      memberUids: [ALICE_UID, BOB_UID],
      lastMessageText: "Hey!",
      lastMessageType: "text",
      lastMessageSenderId: ALICE_UID,
      unreadCount: 1,
      createdBy: ALICE_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    bobDb.collection("chats").doc("chat_ab").collection("messages").doc("msg_1").set({
      chatId: "chat_ab",
      senderId: BOB_UID,
      senderName: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      text: "Check out this reel!",
      messageType: "reel_share",
      sharedReelId: "reel_1",
      sharedReelCaption: "Sunset",
      sharedReelCreator: "alice_snap",
      readBy: [BOB_UID],
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Valid member query succeeds
  await assertSucceeds(
    bobDb.collection("chats").where("memberUids", "array-contains", BOB_UID).get()
  );
  await assertSucceeds(
    aliceDb
      .collection("chats")
      .doc("chat_ab")
      .collection("messages")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );

  // Charlie (non-member) cannot read or send messages
  await assertFails(charlieDb.collection("chats").doc("chat_ab").get());
  await assertFails(
    charlieDb.collection("chats").doc("chat_ab").collection("messages").doc("msg_2").set({
      chatId: "chat_ab",
      senderId: CHARLIE_UID,
      senderName: "Charlie",
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      text: "Intruder",
      messageType: "text",
      createdAt: new Date(Date.now() - 500),
    })
  );
});

test("SNAP Reels creation, likes, comments, saves, follows, reports, and deletion", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  // Create Reel (valid Kotlin payload without @DocumentId 'id')
  await assertSucceeds(
    aliceDb.collection("reels").doc("reel_1").set({
      creatorId: ALICE_UID,
      creatorUsername: "alice_snap",
      videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
      caption: "Morning coffee #snap #reels",
      hashtags: ["snap", "reels"],
      musicTitle: "Neon Horizon",
      musicArtist: "SNAP Sound",
      musicLicenseId: "CC-BY-4.0-SNAP-01",
      durationSeconds: 15,
      visibility: "public",
      moderationStatus: "approved",
      copyrightVerified: true,
      likesCount: 0,
      commentsCount: 0,
      sharesCount: 0,
      savesCount: 0,
      viewsCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Shadow update / ghost field fails
  await assertFails(
    aliceDb.collection("reels").doc("reel_ghost").set({
      id: "reel_ghost",
      creatorId: ALICE_UID,
      creatorUsername: "alice_snap",
      videoUrl: "https://example.com/v.mp4",
      caption: "Ghost",
      hashtags: [],
      musicTitle: "Track",
      musicArtist: "Artist",
      musicLicenseId: "LIC-1",
      durationSeconds: 15,
      visibility: "public",
      moderationStatus: "approved",
      copyrightVerified: true,
      likesCount: 0,
      commentsCount: 0,
      sharesCount: 0,
      savesCount: 0,
      viewsCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Filtered Reels query succeeds; unfiltered fails
  await assertSucceeds(
    bobDb
      .collection("reels")
      .where("moderationStatus", "==", "approved")
      .where("visibility", "==", "public")
      .get()
  );
  await assertFails(bobDb.collection("reels").get());

  // Bob likes, saves, comments, follows Alice, and reports
  await assertSucceeds(
    bobDb.collection("reel_likes").doc("reel_1_bob_456").set({
      reelId: "reel_1",
      userId: BOB_UID,
      active: true,
      createdAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    bobDb.collection("reels").doc("reel_1").update({
      likesCount: 1,
      updatedAt: new Date(Date.now() - 900),
    })
  );
  await assertSucceeds(
    bobDb.collection("reels").doc("reel_1").collection("comments").doc("c_1").set({
      reelId: "reel_1",
      authorId: BOB_UID,
      authorUsername: "bob_456",
      text: "Awesome video!",
      parentCommentId: "",
      likesCount: 0,
      createdAt: new Date(Date.now() - 800),
    })
  );
  await assertSucceeds(
    bobDb.collection("follows").doc("bob_456_alice_123").set({
      followerId: BOB_UID,
      creatorId: ALICE_UID,
      active: true,
      createdAt: new Date(Date.now() - 700),
    })
  );
  await assertSucceeds(
    bobDb.collection("reel_reports").doc("rep_1").set({
      reelId: "reel_1",
      reporterId: BOB_UID,
      reason: "Spam or misleading",
      details: "Test report",
      status: "submitted",
      createdAt: new Date(Date.now() - 600),
    })
  );

  // Bob cannot delete Alice's reel or edit caption
  await assertFails(bobDb.collection("reels").doc("reel_1").delete());
  await assertFails(
    bobDb.collection("reels").doc("reel_1").update({
      caption: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb.collection("reels").doc("reel_1").update({
      caption: "Future",
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Alice can delete her own reel
  await assertSucceeds(aliceDb.collection("reels").doc("reel_1").delete());
});

test("Status updates & call logs enforce ownership and participant scoping", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("updates").doc("upd_1").set({
      userId: ALICE_UID,
      username: "alice_snap",
      caption: "Recording a new SNAP Reel!",
      backgroundHex: "#00E5A3",
      visibility: "public",
      viewerIds: [],
      createdAt: now,
    })
  );
  await assertSucceeds(
    bobDb.collection("updates").where("visibility", "==", "public").get()
  );

  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").set({
      callerId: ALICE_UID,
      callerName: "Alice",
      calleeId: BOB_UID,
      calleeName: "Bob",
      participantUids: [ALICE_UID, BOB_UID],
      callType: "video",
      status: "ringing",
      durationSeconds: 0,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    bobDb.collection("calls").where("participantUids", "array-contains", BOB_UID).get()
  );
  await assertFails(charlieDb.collection("calls").doc("call_1").get());
});
