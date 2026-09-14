package com.rommie.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.rommie.app.Greeting
import com.rommie.app.ui.navigation.RoomieDestination

// Feature owners supply screen lambdas with their own state/ViewModels; Activity stays stable.
@Composable
fun RoomieApp(
    home: @Composable () -> Unit = { Greeting("Android") },
    chores: @Composable () -> Unit = { Text("Chores — coming soon") },
    verification: @Composable () -> Unit = { Text("Review — coming soon") },
    leaderboard: @Composable () -> Unit = { Text("Points — coming soon") },
) {
    var destinationName by rememberSaveable { mutableStateOf(RoomieDestination.HOME.name) }
    val destination = RoomieDestination.valueOf(destinationName)
    BackHandler(enabled = destination != RoomieDestination.HOME) {
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
                RoomieDestination.HOME -> home()
                RoomieDestination.CHORES -> chores()
                RoomieDestination.VERIFICATION -> verification()
                RoomieDestination.LEADERBOARD -> leaderboard()
            }
        }
    }
}
