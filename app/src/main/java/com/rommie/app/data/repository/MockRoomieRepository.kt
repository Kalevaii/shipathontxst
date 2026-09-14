package com.rommie.app.data.repository

import com.rommie.app.data.mock.RoomieSampleData

class MockRoomieRepository(
    private val snapshot: HouseholdSnapshot = RoomieSampleData.household,
) : RoomieRepository {
    override suspend fun loadHousehold(householdId: String): HouseholdSnapshot? =
        snapshot.takeIf { it.household.id == householdId }
}
