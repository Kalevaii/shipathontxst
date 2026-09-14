import { readFile } from 'node:fs/promises';
import { before, after, test } from 'node:test';
import assert from 'node:assert/strict';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, collection, setDoc, getDocFromServer, getDocsFromServer, updateDoc, deleteDoc, serverTimestamp, Timestamp } from 'firebase/firestore';
import { readPrivateReviews } from '../../server/reviews/read-private-reviews.mjs';
let env, worker, alice, carol, outsider;
const hid='r'.repeat(20), base=`households/${hid}/completions/task`;
const payload=(uid, rating=1, changes={})=>({householdId:hid,completionId:'task',reviewerId:uid,rating,submittedAt:serverTimestamp(),...changes});
const path=(uid,cid='task')=>`households/${hid}/completions/${cid}/reviews/${uid}`;
before(async()=>{
 env=await initializeTestEnvironment({projectId:'demo-roomie-rules',firestore:{host:'127.0.0.1',port:8085,rules:await readFile(new URL('../../firestore.rules',import.meta.url),'utf8')}});
 await env.clearFirestore();
 [worker,alice,carol,outsider]=['worker','alice','carol','outsider'].map(uid=>env.authenticatedContext(uid).firestore());
 await env.withSecurityRulesDisabled(async ctx=>{
  const db=ctx.firestore();
  for(const uid of ['worker','alice','carol']) await setDoc(doc(db,`households/${hid}/members/${uid}`),{userId:uid});
  for(const [id,status] of [['task','AWAITING_VERIFICATION'],['early','ASSIGNED'],['verified','VERIFIED'],['cancelled','CANCELLED'],['foreign','AWAITING_VERIFICATION'],['result','AWAITING_VERIFICATION']]){
   await setDoc(doc(db,`households/${hid}/assignments/${id}`),{householdId:hid,assignedUserId:'worker',status});
   await setDoc(doc(db,`households/${hid}/completions/${id}`),{householdId:id==='foreign'?'foreign':hid,assignmentId:id,completedBy:'worker',result:id==='result'?{finalRating:10,pointsAwarded:20}:null});
  }
 });
});
after(async()=>env?.cleanup());
test('reviewer can check own absence then submit rating 1',async()=>{
 assert.equal((await assertSucceeds(getDocFromServer(doc(alice,path('alice'))))).exists(),false);
 await assertSucceeds(setDoc(doc(alice,path('alice')),payload('alice')));
 assert.equal((await getDocFromServer(doc(alice,path('alice')))).data().rating,1);
});
test('another roommate independently submits rating 10',async()=>{
 await assertFails(getDocFromServer(doc(carol,path('alice'))));
 await assertSucceeds(setDoc(doc(carol,path('carol')),payload('carol',10)));
 await assertFails(getDocFromServer(doc(carol,path('alice'))));
});
test('duplicate submission including identical overwrite is denied',async()=>{
 await assertFails(setDoc(doc(alice,path('alice')),payload('alice')));
 assert.equal((await getDocFromServer(doc(alice,path('alice')))).data().rating,1);
});
test('self-review and own missing-review read denied for worker',async()=>{
 await assertFails(setDoc(doc(worker,path('worker')),payload('worker')));
 await assertFails(getDocFromServer(doc(worker,path('worker'))));
});
test('worker cannot read raw review or enumerate reviews',async()=>{
 await assertFails(getDocFromServer(doc(worker,path('alice'))));
 await assertFails(getDocsFromServer(collection(worker,`${base}/reviews`)));
});
test('members cannot list raw reviews even after submitting',async()=>{
 for(const db of [alice,carol]) await assertFails(getDocsFromServer(collection(db,`${base}/reviews`)));
});
test('nonmember and unauthenticated read/create denied',async()=>{
 for(const db of [outsider,env.unauthenticatedContext().firestore()]){
  await assertFails(setDoc(doc(db,path('outsider')),payload('outsider')));
  await assertFails(getDocFromServer(doc(db,path('alice'))));
  await assertFails(getDocsFromServer(collection(db,`${base}/reviews`)));
 }
});
// Use fresh eligible member documents to distinguish validation failures from duplicate rejection.
async function fresh(){
 const uid=`new${counter++}`;
 await env.withSecurityRulesDisabled(ctx=>setDoc(doc(ctx.firestore(),`households/${hid}/members/${uid}`),{userId:uid}));
 return [uid,env.authenticatedContext(uid).firestore()];
}
let counter=0;
test('ratings 0, 11, fractional and string are rejected on fresh creates',async()=>{
 for(const rating of [0,11,1.5,'5']){const [uid,db]=await fresh(); await assertFails(setDoc(doc(db,path(uid)),payload(uid,rating)));}
});
test('spoofed reviewer field and document ID rejected',async()=>{
 const [uid,db]=await fresh();
 await assertFails(setDoc(doc(db,path(uid)),payload('worker')));
 await assertFails(setDoc(doc(db,path('forged')),payload(uid)));
});
test('spoofed household/completion, extra fields and client time rejected',async()=>{
 for(const change of [{householdId:'foreign'},{completionId:'other'},{submittedAt:Timestamp.fromMillis(0)},{name:'personal data'}]){
  const [uid,db]=await fresh(); await assertFails(setDoc(doc(db,path(uid)),payload(uid,5,change)));
 }
});
test('missing required fields rejected',async()=>{
 for(const key of Object.keys(payload('x'))){const [uid,db]=await fresh();const data=payload(uid);delete data[key];await assertFails(setDoc(doc(db,path(uid)),data));}
});
test('missing completion and wrong household completion rejected',async()=>{
 for(const cid of ['missing','foreign']){const [uid,db]=await fresh();await assertFails(setDoc(doc(db,path(uid,cid)),payload(uid,5,{completionId:cid})));}
});
test('not awaiting verification or already has final result rejected',async()=>{
 for(const cid of ['early','verified','cancelled','result']){const [uid,db]=await fresh();await assertFails(setDoc(doc(db,path(uid,cid)),payload(uid,5,{completionId:cid})));}
});
test('updates and deletion denied',async()=>{
 for(const change of [{rating:10},{reviewerId:'carol'},{householdId:'foreign'},{submittedAt:serverTimestamp()}])await assertFails(updateDoc(doc(alice,path('alice')),change));
 await assertFails(deleteDoc(doc(alice,path('alice'))));
});
test('server-only trusted reader retrieves private records without exposing client list access',async()=>{
 await env.withSecurityRulesDisabled(async ctx=>{
  // Adapter matches Admin Firestore read methods; disabled-rules context is test-only.
  const db=ctx.firestore();
  const adapter={doc:p=>({get:async()=>{const s=await getDocFromServer(doc(db,p));return {exists:s.exists(),data:()=>s.data()};},collection:n=>({get:()=>getDocsFromServer(collection(db,`${p}/${n}`))})})};
  const rows=await readPrivateReviews(adapter,hid,'task');
  assert.deepEqual(rows.map(x=>[x.reviewerId,x.rating]),[['alice',1],['carol',10]]);
  await assert.rejects(readPrivateReviews(adapter,hid,'missing'));
  await assert.rejects(readPrivateReviews(adapter,'../escape','task'));
 });
 await assertFails(getDocsFromServer(collection(alice,`${base}/reviews`)));
});
