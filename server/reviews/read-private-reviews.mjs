/** SERVER ONLY. Inject a trusted Firebase Admin Firestore instance, never a user client.
 * No HTTP/callable endpoint is exposed. Do not return these records to an Android client.
 * Credentials, deployment, finalization and scoring are intentionally deferred.
 */
export async function readPrivateReviews(adminFirestore, householdId, completionId) {
  if (typeof householdId !== 'string' || !/^[A-Za-z0-9]{20}$/.test(householdId) ||
      typeof completionId !== 'string' || !/^[A-Za-z0-9_-]{1,128}$/.test(completionId)) throw new Error('Invalid review scope');
  const parent = adminFirestore.doc(`households/${householdId}/completions/${completionId}`);
  const completion = await parent.get();
  if (!completion.exists || completion.data().householdId !== householdId || completion.data().assignmentId !== completionId)
    throw new Error('Completion scope mismatch or missing completion');
  const snapshot = await parent.collection('reviews').get();
  return snapshot.docs.map(doc => {
    const data = doc.data();
    if (data.householdId !== householdId || data.completionId !== completionId || data.reviewerId !== doc.id ||
        data.reviewerId === completion.data().completedBy || !Number.isInteger(data.rating) || data.rating < 1 || data.rating > 10 ||
        typeof data.submittedAt?.toMillis !== 'function') throw new Error('Invalid private review record');
    // Wire projection corresponding to the shared Review fields; no new domain or score model.
    return { id: doc.id, completionId, reviewerId: doc.id, rating: data.rating, submittedAt: data.submittedAt.toMillis() };
  }).sort((a,b) => a.reviewerId < b.reviewerId ? -1 : a.reviewerId > b.reviewerId ? 1 : 0);
}
