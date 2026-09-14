package com.rommie.app.data.repository

import com.rommie.app.model.Chore
import com.rommie.app.model.Difficulty
import com.rommie.app.model.Recurrence

/** Editable input, not a second persisted Chore model. Null recurrence means one-time. */
data class ChoreDetails(
    val title: String,
    val estimatedMinutes: Int,
    val difficulty: Difficulty,
    val maxPoints: Int,
    val description: String = "",
    val recurrence: Recurrence? = null,
)

interface ChoreRepository {
    suspend fun createChore(householdId: String, details: ChoreDetails): Chore
    suspend fun getChore(householdId: String, choreId: String): Chore?
    suspend fun getHouseholdChores(householdId: String): List<Chore>
    /** Replaces all editable fields. Identity and creation metadata are preserved. */
    suspend fun updateChore(householdId: String, choreId: String, details: ChoreDetails): Chore
}

class ChoreNotFoundException : Exception("Chore not found")
