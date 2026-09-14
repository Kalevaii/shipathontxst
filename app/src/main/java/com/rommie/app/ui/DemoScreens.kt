package com.rommie.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rommie.app.model.Difficulty
import com.rommie.app.model.ProofType
import com.rommie.app.model.TaskAssignment
import com.rommie.app.model.TaskStatus
import com.rommie.app.model.Recurrence
import com.rommie.app.ui.theme.Coral
import com.rommie.app.ui.theme.InkMuted
import com.rommie.app.ui.theme.Lime
import com.rommie.app.ui.theme.Mint
import com.rommie.app.ui.theme.Paper

@Composable
fun HomeScreen(demo: DemoStore, onTasks: () -> Unit, onComplete: (String) -> Unit, onReview: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { BrandHeader(demo) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Good morning, Aayush", style = MaterialTheme.typography.headlineLarge)
                Text("Here is what is moving in ${demo.household.name}.", color = InkMuted)
            }
        }
        item { HouseholdBanner(demo) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Your tasks", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onTasks) { Text("See all") }
            }
        }
        items(demo.assignments.filter { it.assignedUserId == demo.currentUserId }.take(3), key = { it.id }) { assignment ->
            TaskCard(demo, assignment, onComplete, onReview)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Lime), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Small wins add up.", style = MaterialTheme.typography.titleLarge)
                    Text("Complete a chore, share proof, and let the household reward quality work.")
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
fun TasksScreen(demo: DemoStore, onAddChore: () -> Unit, onComplete: (String) -> Unit, onReview: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BrandHeader(demo) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Tasks", style = MaterialTheme.typography.headlineLarge)
                    Text("The shared list for ${demo.household.name}.", color = InkMuted)
                }
                Button(onClick = onAddChore) { Text("+ Add chore") }
            }
        }
        item { DemoNotice("Demo mode", "Changes are local to this preview. Firebase repositories remain the source of truth for production wiring.") }
        if (demo.assignments.isEmpty()) item { EmptyState("No chores yet", "Add the first chore for your household.") }
        else items(demo.assignments, key = { it.id }) { assignment -> TaskCard(demo, assignment, onComplete, onReview) }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
fun LeaderboardScreen(demo: DemoStore) {
    val ranked = demo.rankedMembers
    val hasPoints = ranked.any { it.member.contributionPoints > 0 }
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BrandHeader(demo) }
        item { Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { Text("Leaderboard", style = MaterialTheme.typography.headlineLarge); Text("Quality work, shared fairly.", color = InkMuted) } }
        item { DemoNotice("Points are trusted", "Points appear here after the server finalizes reviewed work. No client can edit this total.") }
        if (!hasPoints) {
            item {
                EmptyState("No points yet", "Complete and verify chores to start the leaderboard.")
            }
        } else {
            items(ranked.size) { index ->
                val member = ranked[index].member
                Card(colors = CardDefaults.cardColors(containerColor = if (member.userId == demo.currentUserId) Lime else Paper)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", style = MaterialTheme.typography.titleLarge, color = Mint, modifier = Modifier.width(34.dp))
                        Column(Modifier.weight(1f)) { Text(ranked[index].displayName, fontWeight = FontWeight.SemiBold); if (member.userId == demo.currentUserId) Text("You", color = Mint, style = MaterialTheme.typography.labelSmall) }
                        Text("${member.contributionPoints} pts", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item { Text("Leaderboard will update after trusted finalization.", color = InkMuted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun BrandHeader(demo: DemoStore) {
    var switcherOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = Mint, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(40.dp)) { Text("R", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(start = 12.dp, top = 8.dp)) }
        Spacer(Modifier.width(10.dp))
        Text("ROOMIE", fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = Color(0xFF0F4E42))
        Spacer(Modifier.weight(1f))
        androidx.compose.foundation.layout.Box {
            AssistChip(onClick = { switcherOpen = true }, label = { Text("Demo user: ${demo.currentUserId.replaceFirstChar { it.uppercase() }}") })
            DropdownMenu(expanded = switcherOpen, onDismissRequest = { switcherOpen = false }) {
                demo.demoUsers.forEach { user ->
                    DropdownMenuItem(
                        text = { Text(user.name) },
                        onClick = { demo.switchUser(user.id); switcherOpen = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun HouseholdBanner(demo: DemoStore) {
    Card(colors = CardDefaults.cardColors(containerColor = Mint), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("YOUR HOUSEHOLD", color = Lime, style = MaterialTheme.typography.labelSmall)
            Text(demo.household.name, color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Invite code  ${demo.household.inviteCode}", color = Color.White, modifier = Modifier.weight(1f)); Text("${demo.members.size} roommates", color = Lime) }
        }
    }
}

@Composable
private fun TaskCard(demo: DemoStore, assignment: TaskAssignment, onComplete: (String) -> Unit, onReview: (String) -> Unit) {
    val chore = demo.choreFor(assignment) ?: return
    val isMine = assignment.assignedUserId == demo.currentUserId
    val completed = demo.completions.any { it.assignmentId == assignment.id }
    Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(chore.title, style = MaterialTheme.typography.titleLarge); Text(if (isMine) "Assigned to you" else "Assigned to ${assignment.assignedUserId.replaceFirstChar { it.uppercase() }}", color = InkMuted) }; StatusPill(assignment.status) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MetaPill(chore.difficulty.name.lowercase().replaceFirstChar { it.uppercase() }, difficultyColor(chore.difficulty)); MetaPill("${chore.estimatedMinutes} min · ${assignment.workloadValue} workload", Color(0xFFEEF2EA)); MetaPill("${chore.maxPoints} pts", Color(0xFFFFF0D0)) }
            Text(dueLabel(assignment.dueAt), color = dueColor(assignment), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            if (chore.description.isNotBlank()) Text(chore.description, color = InkMuted, style = MaterialTheme.typography.bodySmall)
            if (assignment.status == TaskStatus.ASSIGNED && isMine) Button(onClick = { onComplete(assignment.id) }, modifier = Modifier.fillMaxWidth()) { Text("Complete task") }
            else if (assignment.status == TaskStatus.AWAITING_VERIFICATION && !isMine && !demo.hasReviewed(assignment.id)) OutlinedButton(onClick = { onReview(assignment.id) }, modifier = Modifier.fillMaxWidth()) { Text("Review anonymously") }
            else if (demo.hasReviewed(assignment.id)) Text("Review submitted. Trusted finalization is pending.", color = Mint, style = MaterialTheme.typography.bodySmall)
            else if (completed) Text("Proof submitted. Waiting for household review.", color = Mint, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatusPill(status: TaskStatus) {
    val (label, color) = when (status) { TaskStatus.ASSIGNED -> "Assigned" to Color(0xFFFFF0D0); TaskStatus.AWAITING_VERIFICATION -> "In review" to Color(0xFFFFE1DA); TaskStatus.VERIFIED -> "Verified" to Lime; TaskStatus.CANCELLED -> "Cancelled" to Color(0xFFE8E8E8) }
    Surface(color = color, shape = RoundedCornerShape(50.dp)) { Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall) }
}

@Composable
private fun MetaPill(label: String, color: Color) { Surface(color = color, shape = RoundedCornerShape(50.dp)) { Text(label, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall) } }

@Composable
private fun DemoNotice(title: String, body: String) { Surface(color = Color(0xFFFFF8E9), shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(body, color = InkMuted, style = MaterialTheme.typography.bodySmall) } } }

@Composable
private fun EmptyState(title: String, body: String) { Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, style = MaterialTheme.typography.titleLarge); Text(body, color = InkMuted) } }

@Composable
fun AddChoreDialog(demo: DemoStore, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("20") }
    var points by remember { mutableStateOf("10") }
    var difficulty by remember { mutableStateOf(Difficulty.MEDIUM) }
    var frequency by remember { mutableStateOf("One time") }
    val parsedMinutes = minutes.toIntOrNull()
    val parsedPoints = points.toIntOrNull()
    val valid = title.isNotBlank() && parsedMinutes != null && parsedMinutes in 1..1440 && parsedPoints != null && parsedPoints in 0..1000
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a chore") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Chore title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("Description (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, label = { Text("Minutes") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(points, { points = it.filter(Char::isDigit) }, label = { Text("Max points") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Text("Difficulty", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Difficulty.entries.forEach { option -> FilterChip(selected = difficulty == option, onClick = { difficulty = option }, label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) }) } }
                HorizontalDivider()
                Text("Frequency", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("One time", "Every day").forEach { option -> FilterChip(selected = frequency == option, onClick = { frequency = option }, label = { Text(option) }) }
                }
                if (!valid) Text("Enter a title, 1–1,440 minutes, and 0–1,000 points.", color = Color(0xFFB3261E), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = {
                demo.addChore(title.trim(), description.trim(), difficulty, parsedMinutes!!, parsedPoints!!, if (frequency == "Every day") Recurrence.EveryDays(1) else null)
                onDismiss()
            }) { Text("Add and assign") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ProofDialog(demo: DemoStore, assignmentId: String, onDismiss: () -> Unit) {
    var selectedType by remember { mutableStateOf<ProofType?>(null) }
    var submitted by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (submitted) "Proof submitted" else "Submit proof") },
        text = {
            if (submitted) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Your task is now awaiting verification.", color = Mint, fontWeight = FontWeight.SemiBold)
                    StatusPill(TaskStatus.AWAITING_VERIFICATION)
                    Text("The selected ${selectedType?.name?.lowercase()} proof is represented by private metadata in this demo. No live Storage upload is claimed.", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Proof is required before your roommate can review the work.")
                    Text("Choose the proof type you would submit:", color = InkMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProofType.entries.forEach { type ->
                            FilterChip(selected = selectedType == type, onClick = { selectedType = type }, label = { Text(if (type == ProofType.PHOTO) "Photo proof" else "Video proof") })
                        }
                    }
                    if (selectedType != null) Surface(color = Color(0xFFEEF2EA), shape = RoundedCornerShape(14.dp)) { Text("Selected: ${selectedType!!.name.lowercase().replaceFirstChar { it.uppercase() }} metadata", modifier = Modifier.padding(14.dp), color = InkMuted) }
                    Text("Live Firebase Storage upload is environment-unverified. This demo preserves the real proof metadata contract without claiming a file upload.", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (submitted) Button(onClick = onDismiss) { Text("Done") }
            else Button(enabled = selectedType != null, onClick = { demo.submitProof(assignmentId); submitted = true }) { Text("Submit proof") }
        },
        dismissButton = { if (!submitted) TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ReviewDialog(demo: DemoStore, assignmentId: String, onDismiss: () -> Unit) {
    val assignment = demo.assignments.firstOrNull { it.id == assignmentId }
    val chore = assignment?.let(demo::choreFor)
    val completion = demo.completions.firstOrNull { it.assignmentId == assignmentId }
    var rating by remember { mutableFloatStateOf(8f) }
    var submitted by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (submitted) "Review submitted" else "Anonymous review") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (submitted) {
                    Text("Your private rating was recorded for this demo.", color = Mint, fontWeight = FontWeight.SemiBold)
                    Text("Verification is pending trusted finalization. Individual ratings and reviewer identities remain hidden.", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(chore?.title ?: "Chore review", style = MaterialTheme.typography.titleLarge)
                    Text(chore?.description?.takeIf { it.isNotBlank() } ?: "No description provided.", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                    ReviewDetailRow("Assigned roommate", assignment?.assignedUserId?.replaceFirstChar { it.uppercase() } ?: "Unknown")
                    ReviewDetailRow("Proof", completion?.proof?.singleOrNull()?.type?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Metadata submitted")
                    ReviewDetailRow("Maximum points", "${assignment?.maxPoints ?: chore?.maxPoints ?: 0} points")
                    HorizontalDivider()
                    Text("How well was this chore completed?")
                    Text("Your individual rating stays private.", color = InkMuted)
                    Text("${rating.toInt()} / 10", style = MaterialTheme.typography.headlineSmall, color = Coral)
                    Slider(value = rating, onValueChange = { rating = it }, valueRange = 1f..10f, steps = 8)
                    Text("The household sees a quality score, never your identity or raw rating.", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                    Text("Final points use the trusted rule: final rating / 10 × maximum points.", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (submitted) Button(onClick = onDismiss) { Text("Done") }
            else Button(onClick = { demo.submitReview(assignmentId); submitted = true }) { Text("Submit private rating") }
        },
        dismissButton = { if (!submitted) TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ReviewDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = InkMuted, style = MaterialTheme.typography.bodySmall)
        Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
    }
}

private fun difficultyColor(difficulty: Difficulty): Color = when (difficulty) { Difficulty.EASY -> Color(0xFFDDF3E5); Difficulty.MEDIUM -> Color(0xFFFFF0D0); Difficulty.HARD -> Color(0xFFFFE1DA) }

private fun dueLabel(dueAt: Long): String {
    val remaining = dueAt - System.currentTimeMillis()
    val hours = remaining / 3_600_000L
    return when {
        remaining < 0 -> "Past due"
        hours < 1 -> "Due in less than an hour"
        hours < 24 -> "Due in $hours hours"
        else -> "Due in ${hours / 24} days"
    }
}

private fun dueColor(assignment: TaskAssignment): Color = if (assignment.dueAt < System.currentTimeMillis()) Coral else InkMuted