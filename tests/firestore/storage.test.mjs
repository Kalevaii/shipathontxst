import { readFile } from 'node:fs/promises';
import { before, after, test } from 'node:test';
import assert from 'node:assert/strict';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, setDoc, updateDoc } from 'firebase/firestore';
import { ref, uploadBytes, getBytes, updateMetadata, deleteObject, listAll } from 'firebase/storage';
let env, worker, peer, outsider;
const hid = 's'.repeat(20), base = `households/${hid}`;
const path = (id = 'photo', uid = 'worker', household = hid, assignment = 'task') => `households/${household}/completions/${assignment}/proof/${uid}/${id}`;
const photo = { contentType: 'image/png', customMetadata: { proofType: 'PHOTO' } };
const bytes = new Uint8Array([137,80,78,71,13,10,26,10]);
before(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-roomie-rules', firestore: { host: '127.0.0.1', port: 8085,
    rules: await readFile(new URL('../../firestore.rules', import.meta.url), 'utf8') }, storage: { host: '127.0.0.1', port: 9195,
    rules: await readFile(new URL('../../storage.rules', import.meta.url), 'utf8') } });
  await env.clearFirestore(); await env.clearStorage();
  worker = env.authenticatedContext('worker').storage(); peer = env.authenticatedContext('peer').storage();
  outsider = env.authenticatedContext('outsider').storage();
  await env.withSecurityRulesDisabled(async ctx => {
    for (const uid of ['worker','peer']) await setDoc(doc(ctx.firestore(), `${base}/members/${uid}`), { userId: uid });
    await setDoc(doc(ctx.firestore(), `${base}/assignments/task`), { assignedUserId: 'worker', status: 'ASSIGNED' });
  });
});
after(async () => env?.cleanup());
test('Storage assigned worker uploads photo and video; peer downloads privately', async () => {
  await assertSucceeds(uploadBytes(ref(worker, path()), bytes, photo));
  await assertSucceeds(uploadBytes(ref(worker, path('video')), new Uint8Array([0,0,0,24]), { contentType: 'video/mp4', customMetadata: { proofType: 'VIDEO' } }));
  assert.deepEqual(new Uint8Array(await assertSucceeds(getBytes(ref(peer, path())))), bytes);
});
test('Storage rejects foreign worker, household, missing assignment, outsider and anonymous upload', async () => {
  for (const [storage, location] of [[peer, path('bad')], [peer, path('bad','peer')], [worker,path('bad','worker','other')],
    [worker,path('bad','worker',hid,'missing')], [outsider,path('bad')], [env.unauthenticatedContext().storage(),path('bad')]])
    await assertFails(uploadBytes(ref(storage, location), bytes, photo));
});
test('Storage rejects public reads and nonmember downloads', async () => {
  for (const storage of [outsider, env.unauthenticatedContext().storage()]) await assertFails(getBytes(ref(storage, path())));
});
test('Storage rejects overwrites, metadata/token changes, deletion and listing', async () => {
  await assertFails(uploadBytes(ref(worker, path()), bytes, photo));
  await assertFails(updateMetadata(ref(worker, path()), { customMetadata: { firebaseStorageDownloadTokens: 'public-token' } }));
  await assertFails(deleteObject(ref(worker, path())));
  await assertFails(listAll(ref(worker, `${base}/completions/task/proof/worker`)));
});
test('Storage enforces type, nonempty size, size cap and allowed metadata', async () => {
  for (const [body, metadata] of [[new Uint8Array(), photo], [bytes, { ...photo, contentType: 'text/html' }],
    [bytes, { contentType: 'video/mp4', customMetadata: { proofType: 'PHOTO' } }],
    [bytes, { ...photo, customMetadata: { proofType: 'PHOTO', forgedOwner: 'peer' } }],
    [new Uint8Array(5 * 1024 * 1024 + 1), photo],
    [new Uint8Array(20 * 1024 * 1024 + 1), { contentType: 'video/mp4', customMetadata: { proofType: 'VIDEO' } }]])
    await assertFails(uploadBytes(ref(worker, path('invalid')), body, metadata));
});
test('Storage rejects uploads after submission while existing evidence stays readable', async () => {
  await env.withSecurityRulesDisabled(async ctx => updateDoc(doc(ctx.firestore(), `${base}/assignments/task`), { status: 'AWAITING_VERIFICATION' }));
  await assertFails(uploadBytes(ref(worker, path('late')), bytes, photo));
  await assertSucceeds(getBytes(ref(peer, path())));
});
