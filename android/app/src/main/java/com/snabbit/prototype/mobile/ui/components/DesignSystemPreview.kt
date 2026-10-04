package com.snabbit.prototype.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.ui.theme.ConfirmedGreen
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing
import com.snabbit.prototype.mobile.ui.theme.SnabbitTheme

@Preview(showBackground = true)
@Composable
private fun ComponentsPreview() {
    SnabbitTheme(darkTheme = false) {
        Column(verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium)) {
            BrandLogo()
            InfoChip(text = stringResource(R.string.sample_duration))
            StatusBadge(text = stringResource(R.string.sample_status), color = ConfirmedGreen)
            SnabbitCard { PlaceholderArtwork(contentDescription = stringResource(R.string.preview_artwork_description)) }
        }
    }
}
