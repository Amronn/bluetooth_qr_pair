package com.example.projekt

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.provider.Contacts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.util.Locale

class UIMIDI(val pairDevice: () -> Unit, val QRPair: () -> Unit): ViewModel(){
    var connected: Boolean = false
    var keyList = mutableListOf<Int>()

    sealed class SettingsItem {
        data class Option(val title: String, val onClick: () -> Unit) : SettingsItem()
        data class Divider(val color: Color = Color.Gray) : SettingsItem()
    }

    fun setList(keys: MutableList<Int>) {
        keyList = keys
    }

    private fun setLocale(context: Context, language: String) {
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = Configuration()
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    @Composable
    fun MainScreen() {
        val navController = rememberNavController()

        Scaffold(
            bottomBar = {
                BottomBar(navController = navController)
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
            ) {
                NavHost(navController, startDestination = "home") {
                    composable("home") { HomePage() }
                    composable("piano") { PianoKeyboard() }
                    composable("settings") { SettingsPage(navController, this@UIMIDI) }
                    composable("about") { AboutPage() }
                }
            }
        }
    }

    @Composable
    fun BottomBar(navController: NavController) {
        BottomAppBar(
            modifier = Modifier.height(56.dp),
            contentPadding = PaddingValues(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.navigate("home") }) {
                    Icon(Icons.Outlined.Home, contentDescription = "Home")
                }
                IconButton(onClick = { navController.navigate("piano") }) {
                    Icon(Icons.Outlined.Build, contentDescription = "View")
                }
                IconButton(onClick = { navController.navigate("settings") }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                }
            }
        }
    }

    @Composable
    fun HomePage() {
        var showDialog by remember {
            mutableStateOf(false)
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .size(width = 320.dp, height = 220.dp)
                    .padding(32.dp), // Adding some padding for better aesthetics
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Pair your acoustic piano converter",
                        textAlign = TextAlign.Center, // Center aligning the text
                        modifier = Modifier.padding(bottom = 16.dp) // Adding some space below the text
                    )
                    Button(
                        onClick = {
                            showDialog = true
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally) // Centering the button
                    ) {
                        Text("Pair device")
                    }
                }
            }

            Card(
                modifier = Modifier
                    .size(width = 320.dp, height = 220.dp)
                    .padding(32.dp), // Adding some padding for better aesthetics
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "MIDI piano connected",
                        textAlign = TextAlign.Center, // Center aligning the text
                        modifier = Modifier.padding(bottom = 16.dp) // Adding some space below the text
                    )
                }
            }
            PairDeviceDialog(
                showDialog = showDialog,
                onDismiss = { showDialog = false },
                onPair = {
                    pairDevice()
                },
                qrPair = {
                    QRPair()
                }
            )
        }
    }

    @Composable
    fun PairDeviceDialog(
        showDialog: Boolean,
        onDismiss: () -> Unit,
        onPair: () -> Unit,
        qrPair: () -> Unit
    ) {
        if (showDialog) {
            Dialog(
                onDismissRequest = onDismiss
            ) {
                Card(
                    modifier = Modifier
                        .width(360.dp)
                        .height(400.dp)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp) // Rounded corners
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxSize(), // Fill the available space in the card
                        verticalArrangement = Arrangement.SpaceBetween // Align items with space between them
                    ) {
                        Column {
                            Text(
                                text = "Pair Device",
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "Do you want to pair a new device?",
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp), // Add padding to the bottom
                            horizontalAlignment = Alignment.End // Align the buttons to the end of the row
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text("Cancel")
                                }
                                Button(
                                    onClick = {
                                        qrPair()
                                        onDismiss()
                                    }
                                ) {
                                    Text("QR Pair")
                                }
                                Button(
                                    onClick = {
                                        onPair()
                                        onDismiss()
                                    }
                                ) {
                                    Text("Pair")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun PianoKeyboard() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.padding(0.dp)
            ) {
                Row {
                    repeat(12) { index ->
                        val isWhiteKey = !listOf(1, 3, 6, 8, 10).contains(index)
                        if (isWhiteKey) {
                            WhiteKey(index in keyList)
                        }
                    }
                }
                Row(modifier = Modifier.padding(horizontal = 10.dp)) {
                    repeat(12) { index ->
                        val isWhiteKey = !listOf(1, 3, 6, 8, 10).contains(index)
                        if (isWhiteKey) {
                            Spacer(Modifier.size(10.dp))
                            if (index == 5)
                                Spacer(Modifier.size(20.dp))
                        } else {
                            BlackKey(index in keyList)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun WhiteKey(isPressed: Boolean) {
        Box(
            modifier = Modifier
                .size(30.dp, 200.dp)
                .background(
                    if (!isPressed) Color.White else Color.Blue,
                    shape = RoundedCornerShape(4.dp)
                )
        )
    }

    @Composable
    fun BlackKey(isPressed: Boolean) {
        Box(
            modifier = Modifier
                .size(20.dp, 120.dp)
                .background(
                    if (!isPressed) Color.Black else Color.Blue,
                    shape = RoundedCornerShape(4.dp)
                )
        )
    }

    @Composable
    fun SettingsPage(navController: NavController, ui: UIMIDI) {
        val context = LocalContext.current

        val settingsItems = listOf(
            SettingsItem.Option(stringResource(id = R.string.devices)) { /* Handle Devices click */ },
            SettingsItem.Option(stringResource(id = R.string.language)) { /* Handle Language click */ },
            SettingsItem.Option(stringResource(id = R.string.about)) { navController.navigate("about") },
            SettingsItem.Divider()
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 32.dp)
        ) {
            items(settingsItems) { item ->
                when (item) {
                    is SettingsItem.Option -> SettingsOptionItem(item)
                    is SettingsItem.Divider -> SettingsDividerItem(item)
                }
            }

            item {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(text = stringResource(id = R.string.language))

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(onClick = {
                        ui.setLocale(context, "en")
                    }) {
                        Text(text = "English")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(onClick = {
                        ui.setLocale(context, "pl")
                    }) {
                        Text(text = "Polski")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(onClick = {
                        ui.setLocale(context, "de")
                    }) {
                        Text(text = "Deutsch")
                    }
                }
            }
        }
    }

    @Composable
    fun AboutPage() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "About",
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "This is the about page. Here you can add your descriptive text."
            )
        }
    }

    @Composable
    fun SettingsOptionItem(item: SettingsItem.Option) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp), // Rounded corners
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = item.onClick)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    @Composable
    fun SettingsDividerItem(item: SettingsItem.Divider) {
        Divider(
            color = item.color,
            thickness = 1.dp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )
    }
}
