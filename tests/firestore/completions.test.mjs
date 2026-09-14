import { readFile } from 'node:fs/promises';
import { before, after, test } from 'node:test';
import assert from 'node:assert/strict';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, collection, setDoc, updateDoc, deleteDoc, writeBatch, getDocFromServer, getDocsFromServer, serverTimestamp, Timestamp } from 'firebase/firestore';
let env, worker, peer, outsider;
const hid = 'c'.repeat(20), base = `households/${hid}`;
const payload = (id, changes = {}) => ({ householdId: hid, assignmentId: id, completedBy: 'worker',
  proof: [{ id: 'proof1', mediaUrl: `${base}/completions/${id}/proof/worker/proof1`, type: 'PHOTO' }],
  submittedAt: serverTimestamp(), result: null, ...changes });
async function seed(id, changes = {}) {
  await env.withSecurityRulesDisabled(async ctx => {
    await setDoc(doc(ctx.firestore(), `${base}/assignments/${id}`), { householdId: hid, choreId: 'chore',
      assignedUserId: 'worker', dueAt: 2000000000000, workloadValue: 3, maxPoints: 20, status: 'ASSIGNED',
      createdBy: 'peer', createdAt: Timestamp.now(), ...changes });
  });
}
function submit(db, id, changes = {}, taskChanges = {}) {
  const batch = writeBatch(db);
  batch.set(doc(db, `${base}/completions/${id}`), payload(id, changes));
  batch.update(doc(db, `${base}/assignments/${id}`), { status: 'AWAITING_VERIFICATION', ...taskChanges });
  return batch.commit();
}
before(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-roomie-rules', firestore: { host: '127.0.0.1', port: 8085,
    rules: await readFile(new URL('../../firestore.rules', import.meta.url), 'utf8') } });
  await env.clearFirestore();
  worker = env.authenticatedContext('worker').firestore(); peer = env.authenticatedContext('peer').firestore();
  outsider = env.authenticatedContext('outsider').firestore();
  await env.withSecurityRulesDisabled(async ctx => {
    for (const uid of ['worker', 'peer']) await setDoc(doc(ctx.firestore(), `${base}/members/${uid}`), { userId: uid, contributionPoints: 0 });
  });
});
after(async () => env?.cleanup());
test('worker submits photo atomically and household members read completion/list', async () => {
  await seed('photo'); await assertSucceeds(submit(worker, 'photo'));
  for (const db of [worker, peer]) {
    assert.equal((await getDocFromServer(doc(db, `${base}/completions/photo`))).data().completedBy, 'worker');
    assert.equal((await getDocFromServer(doc(db, `${base}/assignments/photo`))).data().status, 'AWAITING_VERIFICATION');
    assert.equal((await getDocsFromServer(collection(db, `${base}/completions`))).size, 1);
  }
});
test('video metadata supported with the same private path contract', async () => {
  await seed('video'); const data = payload('video'); data.proof[0].type = 'VIDEO';
  await assertSucceeds(submit(worker, 'video', data));
});
test('completion and status must be written together; rejection leaves state unchanged', async () => {
  await seed('atomic');
  await assertFails(setDoc(doc(worker, `${base}/completions/atomic`), payload('atomic')));
  await assertFails(updateDoc(doc(worker, `${base}/assignments/atomic`), { status: 'AWAITING_VERIFICATION' }));
  assert.equal((await getDocFromServer(doc(worker, `${base}/assignments/atomic`))).data().status, 'ASSIGNED');
  assert.equal((await getDocFromServer(doc(worker, `${base}/completions/atomic`))).exists(), false);
});
test('peer cannot complete worker task or spoof completedBy', async () => {
  await seed('spoof');
  await assertFails(submit(peer, 'spoof'));
  await assertFails(submit(worker, 'spoof', { completedBy: 'peer' }));
});
test('outsider and signed-out users cannot submit, read or list completions', async () => {
  await seed('private');
  for (const db of [outsider, env.unauthenticatedContext().firestore()]) {
    await assertFails(submit(db, 'private'));
    await assertFails(getDocFromServer(doc(db, `${base}/completions/photo`)));
    await assertFails(getDocsFromServer(collection(db, `${base}/completions`)));
  }
});
test('cross-household, foreign-worker, public URL, malformed and empty proof are rejected', async () => {
  await seed('badproof');
  const good = payload('badproof').proof[0];
  for (const proof of [[], [good, good], [{ ...good, id: '../escape' }], [{ ...good, type: 'AUDIO' }],
    [{ ...good, mediaUrl: 'https://example.com/proof.jpg' }], [{ ...good, mediaUrl: good.mediaUrl.replace(hid, 'd'.repeat(20)) }],
    [{ ...good, mediaUrl: good.mediaUrl.replace('/worker/', '/peer/') }], [{ ...good, extra: true }]]) {
    await assertFails(submit(worker, 'badproof', { proof }));
  }
});
test('forged aggregates, time, household and identity fields rejected', async () => {
  await seed('fields');
  for (const changes of [{ result: { finalRating: 10, pointsAwarded: 20 } }, { submittedAt: Timestamp.fromMillis(0) },
    { householdId: 'd'.repeat(20) }, { assignmentId: 'photo' }, { pointsAwarded: 100 }, { proof: null }]) {
    await assertFails(submit(worker, 'fields', changes));
  }
  for (const key of Object.keys(payload('fields'))) {
    const data = payload('fields'); delete data[key];
    const batch = writeBatch(worker); batch.set(doc(worker, `${base}/completions/fields`), data);
    batch.update(doc(worker, `${base}/assignments/fields`), { status: 'AWAITING_VERIFICATION' });
    await assertFails(batch.commit());
  }
});
test('submission cannot smuggle assignment snapshot changes', async () => {
  await seed('snapshot');
  for (const changes of [{ maxPoints: 100 }, { workloadValue: 1 }, { assignedUserId: 'peer' }, { dueAt: 1 }, { choreId: 'different' }])
    await assertFails(submit(worker, 'snapshot', {}, changes));
});
test('existing completion cannot be replaced, finalized or deleted by clients', async () => {
  await assertFails(submit(worker, 'photo'));
  await assertFails(updateDoc(doc(worker, `${base}/completions/photo`), { result: { finalRating: 10, pointsAwarded: 20 } }));
  await assertFails(deleteDoc(doc(worker, `${base}/completions/photo`)));
  await assertFails(updateDoc(doc(worker, `${base}/assignments/photo`), { status: 'VERIFIED' }));
});
test('missing, cancelled and verified assignments cannot be completed', async () => {
  await assertFails(submit(worker, 'missing'));
  for (const status of ['CANCELLED', 'VERIFIED', 'AWAITING_VERIFICATION']) {
    await seed(status, { status }); await assertFails(submit(worker, status));
  }
});
