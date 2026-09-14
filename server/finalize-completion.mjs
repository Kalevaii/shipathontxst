/** SERVER ONLY. Inject a trusted Firebase Admin Firestore instance.
 * The caller must provide the existing Stage 4 finalRating and qualityBasedPoints
 * functions. This module exposes no HTTP/callable endpoint and must not be bundled
 * into the Android client.
 */

export const MIN_REVIEWS_FOR_FINALIZATION = 1;

const completionPattern = /^[A-Za-z0-9_-]{1,128}$/;
const householdPattern = /^[A-Za-z0-9]{20}$/;

function validateScope(householdId, completionId) {
  if (typeof householdId !== 'string' || !householdPattern.test(householdId) ||
      typeof completionId !== 'string' || !completionPattern.test(completionId)) {
    throw new Error('Invalid completion scope');
  }
}

function requireInteger(value, label) {
  if (!Number.isInteger(value)) throw new Error(`Invalid ${label}`);
  return value;
}

function requireFinalResult(result) {
  if (!result || typeof result !== 'object' ||
      typeof result.finalRating !== 'number' || !Number.isFinite(result.finalRating) ||
      result.finalRating < 1 || result.finalRating > 10 ||
      !Number.isInteger(result.pointsAwarded) || result.pointsAwarded < 0) {
    throw new Error('Invalid stored verification result');
  }
  return { finalRating: result.finalRating, pointsAwarded: result.pointsAwarded };
}

function reviewRating(doc, householdId, completionId, completedBy) {
  const data = doc.data();
  if (data.householdId !== householdId || data.completionId !== completionId ||
      data.reviewerId !== doc.id || data.reviewerId === completedBy ||
      !Number.isInteger(data.rating) || data.rating < 1 || data.rating > 10) {
    throw new Error('Invalid private review record');
  }
  return data.rating;
}

/**
 * Atomically finalizes one AWAITING_VERIFICATION completion and awards points.
 * Retries and concurrent calls are idempotent because the result is the commit
 * marker and the member increment happens in the same transaction.
 */
export async function finalizeCompletion(
  adminFirestore,
  householdId,
  completionId,
  { calculateFinalRating, calculatePoints, minimumReviews = MIN_REVIEWS_FOR_FINALIZATION } = {},
) {
  validateScope(householdId, completionId);
  if (typeof calculateFinalRating !== 'function' || typeof calculatePoints !== 'function') {
    throw new Error('Trusted Stage 4 calculators are required');
  }
  if (!Number.isInteger(minimumReviews) || minimumReviews < 1) {
    throw new Error('Invalid minimum review count');
  }

  const completionRef = adminFirestore.doc(`households/${householdId}/completions/${completionId}`);
  const assignmentRef = adminFirestore.doc(`households/${householdId}/assignments/${completionId}`);

  return adminFirestore.runTransaction(async transaction => {
    const completionSnapshot = await transaction.get(completionRef);
    if (!completionSnapshot.exists) throw new Error('Completion does not exist');
    const completion = completionSnapshot.data();
    if (completion.householdId !== householdId || completion.assignmentId !== completionId) {
      throw new Error('Completion scope mismatch');
    }
    if (completion.result != null) return requireFinalResult(completion.result);
    if (typeof completion.completedBy !== 'string' || !Array.isArray(completion.proof) ||
        completion.proof.length !== 1) {
      throw new Error('Completion proof is invalid');
    }

    const assignmentSnapshot = await transaction.get(assignmentRef);
    if (!assignmentSnapshot.exists) throw new Error('Assignment does not exist');
    const assignment = assignmentSnapshot.data();
    if (assignment.householdId !== householdId || assignment.status !== 'AWAITING_VERIFICATION' ||
        assignment.assignedUserId !== completion.completedBy || completion.completedBy !== assignment.assignedUserId) {
      throw new Error('Completion is not ready for finalization');
    }
    const workerRef = adminFirestore.doc(`households/${householdId}/members/${assignment.assignedUserId}`);
    const workerSnapshot = await transaction.get(workerRef);
    if (!workerSnapshot.exists || workerSnapshot.data().userId !== assignment.assignedUserId) {
      throw new Error('Worker membership does not exist');
    }
    const reviewsSnapshot = await transaction.get(completionRef.collection('reviews'));
    const ratings = reviewsSnapshot.docs.map(doc => reviewRating(doc, householdId, completionId, completion.completedBy));
    if (ratings.length < minimumReviews) throw new Error('Insufficient reviews');

    const finalRating = calculateFinalRating(ratings);
    if (typeof finalRating !== 'number' || !Number.isFinite(finalRating) || finalRating < 1 || finalRating > 10) {
      throw new Error('Rating calculator returned no valid rating');
    }
    const maxPoints = requireInteger(assignment.maxPoints, 'maxPoints');
    if (maxPoints < 0) throw new Error('Invalid maxPoints');
    const pointsAwarded = calculatePoints(finalRating, maxPoints);
    requireInteger(pointsAwarded, 'pointsAwarded');
    if (pointsAwarded < 0) throw new Error('Invalid pointsAwarded');
    const contributionPoints = requireInteger(workerSnapshot.data().contributionPoints, 'contributionPoints');
    if (contributionPoints < 0 || contributionPoints + pointsAwarded > Number.MAX_SAFE_INTEGER) {
      throw new Error('Invalid contributionPoints');
    }

    const result = { finalRating, pointsAwarded };
    transaction.update(completionRef, { result });
    transaction.update(assignmentRef, { status: 'VERIFIED' });
    transaction.update(workerRef, { contributionPoints: contributionPoints + pointsAwarded });
    return result;
  });
}