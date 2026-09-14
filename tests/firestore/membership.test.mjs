import { readFile } from 'node:fs/promises';
import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, doc, getDocFromServer, getDocsFromServer, setDoc, updateDoc, writeBatch, serverTimestamp } from 'firebase/firestore';

let env, creator, joiner, outsider;
const hid = 'RoomieRulesTest00001';
const code = 'TEST24';
const household = `households/${hid}`;
const member = uid => `${household}/members/${uid}`;
const profile = uid => ({ name: uid, email: `${uid}@example.com` });
const fields = uid => ({ userId: uid, displayName: uid, joinedAt: serverTimestamp(), contributionPoints: 0 });

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-roomie-rules',
    firestore: { host: '127.0.0.1', port: 8085, rules: await readFile(new URL('../../firestore.rules', import.meta.url), 'utf8') }
  });
  await env.clearFirestore();
  creator = env.authenticatedContext('creator', { email: 'creator@example.com' }).firestore();
  joiner = env.authenticatedContext('joiner', { email: 'joiner@example.com' }).firestore();
  outsider = env.authenticatedContext('outsider', { email: 'outsider@example.com' }).firestore();
  for (const [uid, db] of [['creator', creator], ['joiner', joiner], ['outsider', outsider]]) {
    await setDoc(doc(db, `users/${uid}`), profile(uid));
  }
  const batch = writeBatch(creator);
  batch.set(doc(creator, household), { name: 'Rules test household', inviteCode: code, createdBy: 'creator', createdAt: serverTimestamp() });
  batch.set(doc(creator, `householdInvites/${code}`), { householdId: hid });
  batch.set(doc(creator, member('creator')), fields('creator'));
  batch.set(doc(creator, 'userHouseholds/creator'), { householdId: hid, inviteCode: code });
  await assertSucceeds(batch.commit());
});
after(async () => { await env?.cleanup(); });

test('creator reads household and initial roster', async () => {
  assert.equal((await getDocFromServer(doc(creator, household))).exists(), true);
  assert.equal((await getDocsFromServer(collection(creator, `${household}/members`))).size, 1);
});
test('second user is denied before joining, then reads household and roster after atomic join', async () => {
  await assertFails(getDocFromServer(doc(joiner, household)));
  await assertFails(getDocsFromServer(collection(joiner, `${household}/members`)));
  assert.equal((await getDocFromServer(doc(joiner, `householdInvites/${code}`))).data().householdId, hid);
  const batch = writeBatch(joiner);
  batch.set(doc(joiner, member('joiner')), fields('joiner'));
  batch.set(doc(joiner, 'userHouseholds/joiner'), { householdId: hid, inviteCode: code });
  await assertSucceeds(batch.commit());
  assert.equal((await getDocFromServer(doc(joiner, member('joiner')))).exists(), true);
  assert.equal((await getDocFromServer(doc(joiner, household))).exists(), true);
  assert.deepEqual((await getDocsFromServer(collection(joiner, `${household}/members`))).docs.map(d => d.id).sort(), ['creator', 'joiner']);
});
test('nonmember cannot read household, individual membership, or roster', async () => {
  await assertFails(getDocFromServer(doc(outsider, household)));
  await assertFails(getDocFromServer(doc(outsider, member('creator'))));
  await assertFails(getDocsFromServer(collection(outsider, `${household}/members`)));
});
test('private profiles and lookups remain owner-only', async () => {
  await assertSucceeds(getDocFromServer(doc(joiner, 'users/joiner')));
  await assertFails(getDocFromServer(doc(joiner, 'users/creator')));
  await assertFails(getDocFromServer(doc(joiner, 'userHouseholds/creator')));
  await assertFails(getDocsFromServer(collection(joiner, 'users')));
});
test('member cannot edit protected fields or add another UID', async () => {
  for (const change of [{ contributionPoints: 999 }, { workloadScore: 999 }, { userId: 'creator' }, { displayName: 'Changed' }]) {
    await assertFails(updateDoc(doc(joiner, member('joiner')), change));
  }
  await assertFails(setDoc(doc(joiner, member('forged')), fields('forged')));
  await assertFails(updateDoc(doc(joiner, household), { createdBy: 'joiner' }));
  assert.equal((await getDocFromServer(doc(joiner, member('joiner')))).data().contributionPoints, 0);
});
test('invite enumeration and incorrect-code join remain denied', async () => {
  await assertFails(getDocsFromServer(collection(outsider, 'householdInvites')));
  const batch = writeBatch(outsider);
  batch.set(doc(outsider, member('outsider')), fields('outsider'));
  batch.set(doc(outsider, 'userHouseholds/outsider'), { householdId: hid, inviteCode: 'BAD000' });
  await assertFails(batch.commit());
  assert.equal((await getDocFromServer(doc(outsider, 'userHouseholds/outsider'))).exists(), false);
});
test('unauthenticated reads remain denied', async () => {
  const db = env.unauthenticatedContext().firestore();
  await assertFails(getDocFromServer(doc(db, household)));
  await assertFails(getDocsFromServer(collection(db, `${household}/members`)));
  await assertFails(getDocFromServer(doc(db, 'users/creator')));
});
test('a private household pointer alone does not grant membership', async () => {
  await env.withSecurityRulesDisabled(async context => {
    await setDoc(doc(context.firestore(), 'userHouseholds/outsider'), { householdId: hid, inviteCode: code });
  });
  await assertFails(getDocFromServer(doc(outsider, household)));
  await assertFails(getDocsFromServer(collection(outsider, `${household}/members`)));
});
test('a malformed membership with a different userId does not grant access', async () => {
  // Only the emulator's privileged fixture can create this otherwise forbidden document.
  await env.withSecurityRulesDisabled(async context => {
    await setDoc(doc(context.firestore(), member('outsider')), fields('someone-else'));
  });
  await assertFails(getDocFromServer(doc(outsider, household)));
  await assertFails(getDocsFromServer(collection(outsider, `${household}/members`)));
});
