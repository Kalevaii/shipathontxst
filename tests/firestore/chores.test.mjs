import { readFile } from 'node:fs/promises';
import { before, after, test } from 'node:test';
import assert from 'node:assert/strict';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, doc, getDocFromServer, getDocsFromServer, setDoc, updateDoc, deleteDoc, writeBatch, serverTimestamp, Timestamp } from 'firebase/firestore';

let env, creator, joiner, outsider;
const hid = 'c'.repeat(20), other = 'd'.repeat(20);
const path = `households/${hid}/chores`;
const payload = (changes = {}) => ({ householdId: hid, title: 'Clean kitchen', description: '',
  estimatedMinutes: 30, difficulty: 'MEDIUM', maxPoints: 15, createdBy: 'creator',
  createdAt: serverTimestamp(), recurrence: null, ...changes });
async function makeHousehold(db, uid, id, code) {
  const batch = writeBatch(db);
  batch.set(doc(db, `households/${id}`), { name: 'Chore test household', inviteCode: code, createdBy: uid, createdAt: serverTimestamp() });
  batch.set(doc(db, `householdInvites/${code}`), { householdId: id });
  batch.set(doc(db, `households/${id}/members/${uid}`), { userId: uid, displayName: uid, joinedAt: serverTimestamp(), contributionPoints: 0 });
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
  await makeHousehold(creator, 'creator', hid, 'CHORE1');
  await makeHousehold(outsider, 'outsider', other, 'CHORE2');
  const join = writeBatch(joiner);
  join.set(doc(joiner, `households/${hid}/members/joiner`), { userId: 'joiner', displayName: 'joiner', joinedAt: serverTimestamp(), contributionPoints: 0 });
  join.set(doc(joiner, 'userHouseholds/joiner'), { householdId: hid, inviteCode: 'CHORE1' });
  await assertSucceeds(join.commit());
  await assertSucceeds(setDoc(doc(creator, `${path}/base`), payload()));
});
after(async () => { await env?.cleanup(); });

test('members create one-time and both recurring chore types', async () => {
  for (const [id, recurrence] of [['one', null], ['daily', { type: 'EVERY_DAYS', interval: 3 }],
    ['weekly', { type: 'WEEKLY', weekdays: ['MONDAY', 'FRIDAY'] }]]) {
    await assertSucceeds(setDoc(doc(joiner, `${path}/${id}`), payload({ createdBy: 'joiner', recurrence })));
    assert.deepEqual((await getDocFromServer(doc(joiner, `${path}/${id}`))).data().recurrence, recurrence);
  }
});
test('creator and joined member read and list scoped chores', async () => {
  for (const db of [creator, joiner]) {
    assert.equal((await getDocFromServer(doc(db, `${path}/base`))).data().title, 'Clean kitchen');
    assert.ok((await getDocsFromServer(collection(db, path))).docs.some(d => d.id === 'base'));
  }
});
test('joined member edits allowed fields while preserving creation metadata', async () => {
  const before = (await getDocFromServer(doc(joiner, `${path}/base`))).data();
  await assertSucceeds(updateDoc(doc(joiner, `${path}/base`), { title: 'Bathroom', description: 'Scrub sink',
    difficulty: 'HARD', estimatedMinutes: 45, maxPoints: 20, recurrence: { type: 'EVERY_DAYS', interval: 7 } }));
  const after = (await getDocFromServer(doc(creator, `${path}/base`))).data();
  assert.equal(after.title, 'Bathroom');
  assert.equal(after.createdBy, before.createdBy);
  assert.ok(after.createdAt.isEqual(before.createdAt));
  await assertSucceeds(updateDoc(doc(joiner, `${path}/base`), { recurrence: null }));
});
test('nonmember cannot read, list, create, or update another household chores', async () => {
  await assertFails(getDocFromServer(doc(outsider, `${path}/base`)));
  await assertFails(getDocsFromServer(collection(outsider, path)));
  await assertFails(setDoc(doc(outsider, `${path}/unauthorized`), payload({ createdBy: 'outsider' })));
  await assertFails(updateDoc(doc(outsider, `${path}/base`), { title: 'Forged' }));
});
test('creation cannot spoof creator, household path, timestamp, or extra protected fields', async () => {
  for (const change of [{ createdBy: 'joiner' }, { householdId: other }, { createdAt: Timestamp.fromMillis(0) },
    { id: 'spoofed' }, { workloadValue: 999 }, { contributionPoints: 999 }]) {
    await assertFails(setDoc(doc(creator, `${path}/spoof`), payload(change)));
  }
});
test('protected fields and deletion stay denied for members', async () => {
  for (const change of [{ createdBy: 'joiner' }, { householdId: other }, { createdAt: serverTimestamp() }, { id: 'new' }]) {
    await assertFails(updateDoc(doc(joiner, `${path}/base`), change));
  }
  await assertFails(deleteDoc(doc(creator, `${path}/base`)));
  await assertFails(deleteDoc(doc(joiner, `${path}/base`)));
});
test('invalid content, difficulty, points, and duration rejected on create and update', async () => {
  for (const change of [{ title: '' }, { title: '   ' }, { title: 'a\nb' }, { title: 'x'.repeat(101) },
    { description: 'x'.repeat(2001) }, { difficulty: 'IMPOSSIBLE' }, { maxPoints: -1 }, { maxPoints: 1001 },
    { maxPoints: 1.5 }, { estimatedMinutes: 0 }, { estimatedMinutes: 1441 }, { estimatedMinutes: 1.5 }]) {
    await assertFails(setDoc(doc(creator, `${path}/bad`), payload(change)));
    await assertFails(updateDoc(doc(joiner, `${path}/base`), change));
  }
});
test('malformed recurrence and missing fields rejected', async () => {
  for (const recurrence of [{ type: 'EVERY_DAYS', interval: 0 }, { type: 'EVERY_DAYS', interval: 366 },
    { type: 'EVERY_DAYS', interval: 1.5 }, { type: 'EVERY_DAYS', interval: 2, extra: true },
    { type: 'WEEKLY', weekdays: [] }, { type: 'WEEKLY', weekdays: ['MONDAY', 'MONDAY'] },
    { type: 'WEEKLY', weekdays: ['UNKNOWN'] }, { type: 'UNKNOWN' }, 'weekly']) {
    await assertFails(setDoc(doc(creator, `${path}/bad`), payload({ recurrence })));
    await assertFails(updateDoc(doc(joiner, `${path}/base`), { recurrence }));
  }
  for (const field of Object.keys(payload())) {
    const data = payload(); delete data[field];
    await assertFails(setDoc(doc(creator, `${path}/bad`), data));
  }
});
test('valid boundary values and rewards independent of difficulty are accepted', async () => {
  await assertSucceeds(setDoc(doc(creator, `${path}/bounds`), payload({ title: 'x'.repeat(100),
    description: 'x'.repeat(2000), estimatedMinutes: 1440, difficulty: 'EASY', maxPoints: 1000,
    recurrence: { type: 'EVERY_DAYS', interval: 365 } })));
  await assertSucceeds(updateDoc(doc(joiner, `${path}/bounds`), { estimatedMinutes: 1, maxPoints: 0 }));
});
test('unauthenticated chore reads and writes are denied', async () => {
  const db = env.unauthenticatedContext().firestore();
  await assertFails(getDocFromServer(doc(db, `${path}/base`)));
  await assertFails(getDocsFromServer(collection(db, path)));
  await assertFails(setDoc(doc(db, `${path}/anonymous`), payload()));
});
