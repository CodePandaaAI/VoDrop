package com.liftley.vodrop.ui.components.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AppDrawerContent(
    isLoggedIn: Boolean,
    isPro: Boolean,
    statusText: String,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onClose: () -> Unit
) {
    ModalDrawerSheet(Modifier.fillMaxWidth(0.75f)) {
        Column(Modifier.padding(24.dp)) {
            Text(
                "VoDrop",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(24.dp))

            if (isLoggedIn) {
                Surface(
                    color = if (isPro) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Person, null, Modifier.size(40.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                if (isPro) "Pro User" else "Free User",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                statusText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Button(
                    onClick = onSignIn,
                    Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Rounded.Person, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign In", fontWeight = FontWeight.Bold)
                }
            }
        }

        HorizontalDivider(Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(16.dp))

        NavigationDrawerItem(
            { Text("All Recordings") },
            true,
            onClose,
            icon = { Icon(Icons.Rounded.Mic, null) },
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        NavigationDrawerItem(
            { Text("Settings") },
            false,
            onClose,
            icon = { Icon(Icons.Rounded.Settings, null) },
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        if (isLoggedIn) {
            Spacer(Modifier.weight(1f))
            HorizontalDivider(Modifier.padding(horizontal = 24.dp))
            NavigationDrawerItem(
                { Text("Sign Out") },
                false,
                onSignOut,
                icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) },
                modifier = Modifier.padding(12.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedTextColor = MaterialTheme.colorScheme.error,
                    unselectedIconColor = MaterialTheme.colorScheme.error
                )
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}