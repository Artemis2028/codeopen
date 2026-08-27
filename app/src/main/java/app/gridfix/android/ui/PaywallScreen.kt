package app.gridfix.android.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gridfix.android.billing.BillingManager

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Subscription gate shown instead of the app when there is no active
 * GridFix Pro subscription. Prices and the free-trial length come live from
 * Play Console — nothing is hardcoded here. [onClose] is non-null only in
 * the debug-build preview, where the paywall is browsable but dismissible.
 */
@Composable
fun PaywallScreen(
    billing: BillingManager,
    onClose: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val plans by billing.plans.collectAsStateWithLifecycle()
    val notice by billing.notice.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf(BillingManager.MONTHLY) }
    val selected = plans.firstOrNull { it.productId == selectedId } ?: plans.firstOrNull()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                "MGRS GPS Pro",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "The complete MGRS land-nav kit",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FeatureLine("Offline maps — download areas, bring MBTiles")
                FeatureLine("MGRS-first: grid entry, crosshair readout, go-to-grid")
                FeatureLine("Military symbols and tactical graphics, ATAK packages")
                FeatureLine("Terrain tools: contours, line of sight, viewshed")
                FeatureLine("Routes, tracks, practice courses, PDF strip maps")
                FeatureLine("Works fully offline in the field — no account, no tracking")
            }
            Spacer(Modifier.height(20.dp))

            if (plans.isEmpty()) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text(
                    "Loading plans from Google Play…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { billing.restore() }) { Text("Retry") }
            } else {
                val monthly = plans.firstOrNull { it.productId == BillingManager.MONTHLY }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    plans.forEach { plan ->
                        val savings = if (plan.period == "year" && monthly != null && monthly.priceMicros > 0) {
                            val pct = 100L - (plan.priceMicros * 100L / (monthly.priceMicros * 12L))
                            if (pct in 1L..99L) "save $pct%" else null
                        } else null
                        PlanCard(
                            plan = plan,
                            savings = savings,
                            selected = plan.productId == selectedId,
                            onSelect = { selectedId = plan.productId },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                if (selected != null && selected.trialDays > 0) {
                    Text(
                        "Includes a ${selected.trialDays}-day free trial",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = {
                        val activity = context.findActivity()
                        if (activity != null && selected != null) {
                            billing.launchPurchase(activity, selected)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selected != null,
                ) {
                    Text(
                        if (selected != null && selected.trialDays > 0) "Start free trial"
                        else "Subscribe",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Auto-renews until canceled. Cancel anytime in Google Play — " +
                        "canceling during the trial costs nothing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            notice?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { billing.restore() }) { Text("Restore purchases") }
                TextButton(onClick = {
                    uriHandler.openUri("https://github.com/Artemis2028/gridfix-legal/blob/main/PRIVACY.md")
                }) { Text("Privacy policy") }
            }
            if (onClose != null) {
                TextButton(onClick = onClose) { Text("Close preview (debug build)") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FeatureLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "▸",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PlanCard(
    plan: BillingManager.Plan,
    savings: String?,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        plan.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (savings != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            savings,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (plan.trialDays > 0) {
                    Text(
                        "${plan.trialDays}-day free trial",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                plan.price + "/" + plan.period,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
