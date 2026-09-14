import { test } from 'node:test';
import assert from 'node:assert/strict';
import { finalizeCompletion } from '../../server/finalize-completion.mjs';

class Snapshot {
  constructor(value) { this.value = value; }
  get exists() { return this.value !== undefined; }
  data() { return this.value; }
}

class Ref {
  constructor(store, path) { this.store = store; this.path = path; }
  collection(name) { return new Ref(this.store, `${this.path}/${name}`); }
}

class Transaction {
  constructor(store) { this.store = store; this.updates = []; }
  async get(ref) {
    if (ref.path.endsWith('/reviews')) {
      const prefix = `${ref.path}/`;
      return { docs: [...this.store.entries()]
        .filter(([path]) => path.startsWith(prefix) && !path.slice(prefix.length).includes('/'))
        .map(([path, value]) => ({ id: path.slice(prefix.length), data: () => value })) };
    }
    return new Snapshot(this.store.get(ref.path));
  }
  update(ref, fields) {
    this.updates.push([ref.path, fields]);
  }
}

class FakeAdminFirestore {
  constructor(entries) { this.store = new Map(entries); }
  doc(path) { return new Ref(this.store, path); }
  async runTransaction(callback) {
    const transaction = new Transaction(this.store);
    const result = await callback(transaction);
    for (const [path, fields] of transaction.updates) {
      this.store.set(path, { ...this.store.get(path), ...fields });
    }
    return result;
  }
}

const householdId = 'h'.repeat(20);
const completionId = 'task';
const completionPath = `households/${householdId}/completions/${completionId}`;
const assignmentPath = `households/${householdId}/assignments/${completionId}`;
const memberPath = `households/${householdId}/members/worker`;
const reviewPath = `${completionPath}/reviews/reviewer`;
const calculators = {
  calculateFinalRating: ratings => ratings.reduce((sum, rating) => sum + rating, 0) / ratings.length,
  calculatePoints: (rating, maxPoints) => Math.round(rating / 10 * maxPoints),
};

function database({ status = 'AWAITING_VERIFICATION', result = null, maxPoints = 20 } = {}) {
  return new FakeAdminFirestore([
    [completionPath, { householdId, assignmentId: completionId, completedBy: 'worker', proof: [{}], result }],
    [assignmentPath, { householdId, assignedUserId: 'worker', status, maxPoints }],
    [memberPath, { userId: 'worker', contributionPoints: 3 }],
    [reviewPath, { householdId, completionId, reviewerId: 'reviewer', rating: 7 }],
  ]);
}

test('finalization uses authoritative assignment data and awards partial points once', async () => {
  const db = database({ maxPoints: 20 });
  const result = await finalizeCompletion(db, householdId, completionId, calculators);
  assert.deepEqual(result, { finalRating: 7, pointsAwarded: 14 });
  assert.equal(db.store.get(memberPath).contributionPoints, 17);
  assert.equal(db.store.get(assignmentPath).status, 'VERIFIED');

  const retry = await finalizeCompletion(db, householdId, completionId, calculators);
  assert.deepEqual(retry, result);
  assert.equal(db.store.get(memberPath).contributionPoints, 17);
});

test('perfect rating and max points come from trusted records', async () => {
  const db = database({ maxPoints: 20 });
  db.store.set(reviewPath, { householdId, completionId, reviewerId: 'reviewer', rating: 10 });
  const result = await finalizeCompletion(db, householdId, completionId, {
    calculateFinalRating: ratings => { assert.deepEqual(ratings, [10]); return 10; },
    calculatePoints: (rating, maxPoints) => { assert.equal(rating, 10); assert.equal(maxPoints, 20); return 20; },
  });
  assert.deepEqual(result, { finalRating: 10, pointsAwarded: 20 });
});

test('missing, non-pending, or under-reviewed completions are rejected', async () => {
  await assert.rejects(finalizeCompletion(database(), householdId, 'missing', calculators), /does not exist/);
  await assert.rejects(finalizeCompletion(database({ status: 'ASSIGNED' }), householdId, completionId, calculators), /not ready/);
  const db = database();
  db.store.delete(reviewPath);
  await assert.rejects(finalizeCompletion(db, householdId, completionId, calculators), /Insufficient reviews/);
});