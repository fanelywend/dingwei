package com.dingwei.gpsmock.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dingwei.gpsmock.data.PlaceResult

/**
 * 城市搜索卡片——本项目的主入口（「输入城市名即可模拟定位」）。
 */
@Composable
fun CitySearchCard(
    query: String,
    results: List<PlaceResult>,
    searching: Boolean,
    resultsAreOnline: Boolean,
    cityCount: Int,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSelect: (PlaceResult) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("输入城市名称") },
                placeholder = { Text("如：北京 / shanghai / 深圳 / 杭州") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (query.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Filled.Clear, contentDescription = "清空")
                        }
                    }
                }
            )

            if (cityCount > 0) {
                Text(
                    text = "内置城市库 $cityCount 条，支持中文 / 拼音 / 首字母（如 bj、sh）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (resultsAreOnline) {
                Text(
                    text = "以下结果来自在线搜索（OpenStreetMap）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (results.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(results, key = { "${it.name}|${it.lat}|${it.lon}|${it.source}" }) { place ->
                        PlaceRow(place = place, onClick = { onSelect(place) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(place: PlaceResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = buildString {
                if (place.detail.isNotBlank()) append(place.detail)
                if (isNotEmpty()) append("  ·  ")
                append("%.5f, %.5f".format(place.lat, place.lon))
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
