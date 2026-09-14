import { readFile } from 'node:fs/promises';
import { before, after, test } from 'node:test';
import assert from 'node:assert/strict';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, doc, getDocFromServer, getDocsFromServer, setDoc, updateDoc, deleteDoc, writeBatch, serverTimestamp, Timestamp, query, where } from 'firebase/firestore';

let env, creator, joiner, outsider;
const hid = 'a'.repeat(20), other = 'b'.repeat(20), base = `households/${hid}`;
const payload = (changes = {}) => ({ householdId: hid, choreId: 'hard', assignedUserId: 'joiner', dueAt: 2000000000000,
  workloadValue: 3, maxPoints: 20, status: 'ASSIGNED', createdBy: 'creator', createdAt: serverTimestamp(), ...changes });
async function household(db, uid, id, code) {
  const batch = writeBatch(db);
  batch.set(doc(db, `households/${id}`), { name: 'Assignment test', inviteCode: code, createdBy: uid, createdAt: serverTimestamp() });
  batch.set(doc(db, `householdInvites/${code}`), { householdId: id });
  batch.set(doc(db, `households/${id}/members/${uid}`), { userId: uid, displayName: uid, contributionPoints: 0, joinedAt: serverTimestamp() });
  batch.set(doc(db, `userHouseholds/${uid}`), { householdId: id, inviteCode: code });
  await assertSucceeds(batch.commit());
}
before(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-roomie-rules', firestore: {
    host: '127.0.0.1', port: 8085, rules: await readFile(new URL('../../firestore.rules', import.meta.url), 'utf8') } });
  await env.clearFirestore();
  creator = env.authenticatedContext('creator', { email: 'creator@example.com' }).firestore();
  joiner = env.authenticatedContext('joiner', { email: 'joiner@example.com' }).firestore();
  outsider = env.authenticatedContext('outsider', { email: 'outsider@example.com' }).firestore();
  for (const [uid, db] of [['creator', creator], ['joiner', joiner], ['outsider', outsider]]) {
    await setDoc(doc(db, `users/${uid}`), { name: uid, email: `${uid}@example.com` });
  }
  await household(creator, 'creator', hid, 'ASSGN1');
  await household(outsider, 'outsider', other, 'ASSGN2');
  const join = writeBatch(joiner);
  join.set(doc(joiner, `${base}/members/joiner`), { userId: 'joiner', displayName: 'joiner', contributionPoints: 0, joinedAt: serverTimestamp() });
  join.set(doc(joiner, 'userHouseholds/joiner'), { householdId: hid, inviteCode: 'ASSGN1' });
  await join.commit();
  for (const [id, difficulty, points] of [['easy', 'EASY', 5], ['medium', 'MEDIUM', 10], ['hard', 'HARD', 20]]) {
    await setDoc(doc(creator, `${base}/chores/${id}`), { householdId: hid, title: id, description: '',
      estimatedMinutes: 30, difficulty, maxPoints: points, createdBy: 'creator', createdAt: serverTimestamp(), recurrence: null });
  }
  await setDoc(doc(outsider, `households/${other}/chores/foreign`), { householdId: other, title: 'Foreign', description: '',
    estimatedMinutes: 30, difficulty: 'HARD', maxPoints: 20, createdBy: 'outsider', createdAt: serverTimestamp(), recurrence: null });
  await assertSucceeds(setDoc(doc(creator, `${base}/assignments/base`), payload()));
});
after(async () => { await env?.cleanup(); });

test('members create snapshots matching every chore difficulty', async () => {
  for (const [id, workloadValue, maxPoints] of [['easy', 1, 5], ['medium', 2, 10], ['hard', 3, 20]]) {
    await assertSucceeds(setDoc(doc(joiner, `${base}/assignments/${id}`), payload({ choreId: id, workloadValue, maxPoints, createdBy: 'joiner' })));
  }
});
test('both members read individual, household, and user-filtered assignments', async () => {
  for (const db of [creator, joiner]) {
    assert.equal((await getDocFromServer(doc(db, `${base}/assignments/base`))).data().assignedUserId, 'joiner');
    assert.ok((await getDocsFromServer(collection(db, `${base}/assignments`))).size > 0);
    assert.ok((await getDocsFromServer(query(collection(db, `${base}/assignments`), where('assignedUserId', '==', 'joiner')))).size > 0);
  }
});
test('nonmember cannot read, list, or create assignments', async () => {
  await assertFails(getDocFromServer(doc(outsider, `${base}/assignments/base`)));
  await assertFails(getDocsFromServer(collection(outsider, `${base}/assignments`)));
  await assertFails(setDoc(doc(outsider, `${base}/assignments/forged`), payload({ createdBy: 'outsider' })));
});
test('nonmember and cross-household chore references are rejected', async () => {
  for (const change of [{ assignedUserId: 'outsider' }, { assignedUserId: 'missing' }, { choreId: 'foreign' },
    { choreId: 'missing' }, { choreId: `households/${other}/chores/foreign` }, { householdId: other }]) {
    await assertFails(setDoc(doc(creator, `${base}/assignments/bad`), payload(change)));
  }
});
test('forged snapshot, status, creator, timestamp, and malformed fields rejected', async () => {
  for (const change of [{ workloadValue: 1 }, { workloadValue: 3.5 }, { maxPoints: 999 }, { status: 'VERIFIED' },
    { createdBy: 'joiner' }, { createdAt: Timestamp.fromMillis(0) }, { dueAt: -1 }, { dueAt: 1.5 },
    { dueAt: 253402300800000 }, { id: 'spoof' }]) {
    await assertFails(setDoc(doc(creator, `${base}/assignments/bad`), payload(change)));
  }
  for (const key of Object.keys(payload())) {
    const data = payload(); delete data[key];
    await assertFails(setDoc(doc(creator, `${base}/assignments/bad`), data));
  }
});
test('all protected fields including ownership/status and deletes remain blocked', async () => {
  for (const change of [{ assignedUserId: 'creator' }, { choreId: 'easy' }, { dueAt: 1 }, { status: 'CANCELLED' },
    { status: 'AWAITING_VERIFICATION' }, { maxPoints: 99 }, { workloadValue: 1 }, { householdId: other },
    { createdAt: serverTimestamp() }, { createdBy: 'joiner' }]) {
    await assertFails(updateDoc(doc(joiner, `${base}/assignments/base`), change));
  }
  await assertFails(deleteDoc(doc(creator, `${base}/assignments/base`)));
});
test('editing a chore cannot rewrite previous assignment snapshots', async () => {
  await updateDoc(doc(joiner, `${base}/chores/hard`), { difficulty: 'EASY', maxPoints: 7 });
  const existing = (await getDocFromServer(doc(joiner, `${base}/assignments/base`))).data();
  assert.equal(existing.workloadValue, 3); assert.equal(existing.maxPoints, 20);
  await assertFails(setDoc(doc(creator, `${base}/assignments/stale`), payload()));
  await assertSucceeds(setDoc(doc(creator, `${base}/assignments/current`), payload({ workloadValue: 1, maxPoints: 7 })));
});
test('unauthenticated reads and writes are denied', async () => {
  const db = env.unauthenticatedContext().firestore();
  await assertFails(getDocFromServer(doc(db, `${base}/assignments/base`)));
  await assertFails(getDocsFromServer(collection(db, `${base}/assignments`)));
  await assertFails(setDoc(doc(db, `${base}/assignments/anon`), payload()));
});
