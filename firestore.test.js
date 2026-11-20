const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "officer_alice_123";
const BOB_UID = "officer_bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

test("Unauthenticated context: cannot read or write any border collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/travelers`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/travelers`).doc("trv_1").set({
      fullName: "أحمد علي",
      idNumber: "09812345",
      travelAllowed: "نعم",
      status: "معتمد",
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/audit_logs`).get());
});

test("Cross-user isolation: officer Alice cannot read or modify officer Bob's records", async () => {
  const past = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection(`users/${BOB_UID}/travelers`).doc("trv_bob_1").set({
      fullName: "سالم محمد",
      idNumber: "A1234567",
      travelAllowed: "نعم",
      status: "معتمد",
      createdAt: past,
      updatedAt: past,
    });
    await db.collection(`users/${BOB_UID}/reports`).doc("rep_bob_1").set({
      title: "بلاغ اشتباه جواز",
      targetName: "مشتبه",
      status: "جديد",
      createdAt: past,
      updatedAt: past,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection(`users/${BOB_UID}/travelers`).get());
  await assertFails(aliceDb.collection(`users/${BOB_UID}/travelers`).doc("trv_bob_1").get());
  await assertFails(
    aliceDb.collection(`users/${BOB_UID}/travelers`).doc("trv_bob_1").update({
      travelAllowed: "لا",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(aliceDb.collection(`users/${BOB_UID}/reports`).get());
});

test("Owner CRUD: Kotlin Traveler create, partial update, and security bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Valid Kotlin Traveler map (NO @DocumentId id field)
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/travelers`).doc("trv_001").set({
      serialNumber: "1001",
      firstName: "محمد",
      lastName: "الحسني",
      fullName: "محمد علي صالح الحسني",
      passportName: "MOHAMMED ALI AL-HASANI",
      idNumber: "09451234",
      idType: "جواز سفر",
      passportStatus: "صالح",
      country: "اليمن",
      portName: "مطار صنعاء الدولي",
      travelAllowed: "نعم",
      vettingResult: "سليم",
      status: "معتمد",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid query matching Repository
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/travelers`).get());

  // Valid partial update preserving createdAt
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/travelers`).doc("trv_001").update({
      travelAllowed: "لا",
      returnReason: "منتهي الصلاحية",
      vettingResult: "🚨 تطابق قائمة الترقب",
      status: "مرتجع",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/travelers`).doc("trv_001").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field / @DocumentId "id" inside payload
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/travelers`).doc("trv_002").set({
      id: "trv_002",
      fullName: "اختبار حقل مرفوض",
      idNumber: "P999999",
      travelAllowed: "نعم",
      status: "معتمد",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject invalid travelAllowed enum
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/travelers`).doc("trv_003").set({
      fullName: "اختبار قيمة مرفوضة",
      idNumber: "P888888",
      travelAllowed: "MAYBE",
      status: "معتمد",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject future timestamp spoofing
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/travelers`).doc("trv_004").set({
      fullName: "اختبار توقيت مستقبلي",
      idNumber: "P777777",
      travelAllowed: "نعم",
      status: "معتمد",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: created,
    })
  );
});

test("Audit logs: append-only (create succeeds, update and delete always fail)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/audit_logs`).doc("log_001").set({
      action: "فحص جواز",
      entityType: "Traveler",
      entityId: "trv_001",
      details: "تم فحص الجواز ومطابقته أمنياً",
      officerName: "الرائد صادق",
      officerRole: "محقق أول",
      portName: "مطار صنعاء الدولي",
      timestampIso: "2026-11-19T14:00:00",
      createdAt: now,
    })
  );

  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/audit_logs`).doc("log_001").update({
      details: "تلاعب بالسجل",
    })
  );

  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/audit_logs`).doc("log_001").delete()
  );
});

test("Specialized sovereign collections: seizures, permits, mofa_visas, companies, settings", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 3000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/seizures`).doc("sz_1").set({
      recordNumber: "SZ-2026-01",
      travelerName: "علي صالح",
      passportNumber: "08112233",
      seizurePort: "منفذ الوديعة البري",
      reason: "تلف صفحة البيانات",
      status: "جديد",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/permits`).doc("prm_1").set({
      permitCode: "PRM-UN-901",
      delegationName: "وفد منظمة الصحة العالمية",
      authorizedRoute: "صنعاء - عدن",
      passengersCount: 6,
      approvalStatus: "معتمد رسمياً",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/mofa_visas`).doc("mofa_1").set({
      memoNumber: "MOFA-441",
      organization: "أطباء بلا حدود MSF",
      requestType: "مانيفست رحلة طيران",
      flightRoute: "AMM-ADE-SAH",
      beneficiariesCount: 14,
      approvalStatus: "معتمد رسمياً",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/companies`).doc("cmp_1").set({
      agencyName: "وكالة الأفق للسفريات",
      companyType: "وكالة سفر وسياحة",
      clearanceStatus: "معتمد وساري",
      linkedVisasCount: 12,
      issuedCount: 11,
      returnedCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/settings`).doc("profile").set({
      officerName: "العميد الركن خالد",
      officerRole: "مدير عام",
      assignedPort: "المركز الرئيسي - العاصمة صنعاء",
      biometricEnabled: true,
      highContrastMode: false,
      hqCenterName: "مصلحة الهجرة والجوازات - صنعاء",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Travel crossings (travels): owner CRUD valid, invalid status or cross-user rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/travels`).doc("tr_001").set({
      travelerId: "trv_001",
      travelerName: "أحمد علي المنصور",
      passportNumber: "08123456",
      portName: "منفذ الوديعة البري",
      direction: "مغادرة",
      destination: "جدة",
      travelAllowed: "نعم",
      clearanceStatus: "ALLOWED",
      vettingStatus: "سليم",
      officerName: "نقيب علي",
      crossedAtEpochMs: Date.now() - 2000,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/travels`).doc("tr_invalid").set({
      travelerId: "trv_001",
      travelerName: "أحمد علي المنصور",
      portName: "منفذ الوديعة البري",
      travelAllowed: "نعم",
      clearanceStatus: "UNKNOWN_STATUS",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(bobDb.collection(`users/${ALICE_UID}/travels`).get());
});

