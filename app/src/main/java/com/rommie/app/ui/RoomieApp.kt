package com.rommie.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.rommie.app.data.mock.RoomieSampleData
import com.rommie.app.ui.navigation.RoomieDestination

@Composable
fun RoomieApp() {
    val demo = remember { DemoStore(RoomieSampleData.household) }
    var destinationName by rememberSaveable { mutableStateOf(RoomieDestination.HOME.name) }
    var addChoreOpen by remember { mutableStateOf(false) }
    var activeTaskId by remember { mutableStateOf<String?>(null) }
    var reviewTaskId by remember { mutableStateOf<String?>(null) }
    val destination = RoomieDestination.valueOf(destinationName)
    BackHandler(enabled = addChoreOpen || activeTaskId != null || reviewTaskId != null) {
        addChoreOpen = false
        activeTaskId = null
        reviewTaskId = null
    }
    BackHandler(enabled = !addChoreOpen && activeTaskId == null && reviewTaskId == null && destination != RoomieDestination.HOME) {
        destinationName = RoomieDestination.HOME.name
    }
    Scaffold(modifier = Modifier.fillMaxSize(), bottomBar = {
        NavigationBar {
            RoomieDestination.entries.forEach { item ->
                NavigationBarItem(selected = destination == item,
                    onClick = { destinationName = item.name },
                    icon = { Text(item.label.take(1)) }, label = { Text(item.label) })
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (destination) {
                RoomieDestination.HOME -> HomeScreen(demo, { destinationName = RoomieDestination.CHORES.name }, { activeTaskId = it }, { reviewTaskId = it })
                RoomieDestination.CHORES -> TasksScreen(demo, { addChoreOpen = true }, { activeTaskId = it }, { reviewTaskId = it })
                RoomieDestination.LEADERBOARD -> LeaderboardScreen(demo)
            }
        }
    }
    if (addChoreOpen) AddChoreDialog(demo) { addChoreOpen = false }
    activeTaskId?.let { ProofDialog(demo, it) { activeTaskId = null } }
    reviewTaskId?.let { ReviewDialog(demo, it) { reviewTaskId = null } }
}
