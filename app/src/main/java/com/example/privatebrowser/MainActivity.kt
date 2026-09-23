package com.example.privatebrowser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = ProfileRepository(this)
        setContent {
            PrivateBrowserTheme {
                ProfileScreen(repository) { profile ->
                    repository.markUsed(profile)
                    startActivity(Intent(this, BrowserActivity::class.java).apply {
                        putExtra(BrowserActivity.EXTRA_PROFILE_ID, profile.id)
                        putExtra(BrowserActivity.EXTRA_PROFILE_NAME, profile.name)
                        putExtra(BrowserActivity.EXTRA_WINDOW_ID, "primary")
                    })
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ProfileScreen(repository: ProfileRepository, openProfile: (BrowserProfile) -> Unit) {
    var profiles by remember { mutableStateOf(repository.profiles()) }
    var name by remember { mutableStateOf("") }
    val lastUsedId = remember(profiles) { repository.lastUsedProfileId() }
    Scaffold(topBar = { TopAppBar(title = { Text("端末内プロフィール") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("使用する端末内プロフィールを選択してください。Webデータはプロフィールごとの領域に保存されます。")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("新しいプロフィール名") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        val created = repository.create(name)
                        profiles = repository.profiles()
                        name = ""
                        openProfile(created)
                    },
                    enabled = name.isNotBlank(),
                ) { Text("作成") }
            }
            if (profiles.isEmpty()) Text("プロフィールがありません。名前を入力して作成してください。")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                items(profiles, key = { it.id }) { profile ->
                    Card(onClick = { openProfile(profile) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Text(profile.name, style = MaterialTheme.typography.titleMedium)
                            Text(if (profile.id == lastUsedId) "前回使用 · タップして開く" else "タップして開く")
                        }
                    }
                }
            }
        }
    }
}
