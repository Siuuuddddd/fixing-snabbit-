package com.snabbit.prototype.mobile.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Bathroom
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.domain.address.SavedAddress
import com.snabbit.prototype.mobile.domain.home.FavoriteExpertsPreview
import com.snabbit.prototype.mobile.domain.home.HabitPackPromo
import com.snabbit.prototype.mobile.domain.home.HomeServiceCategory
import com.snabbit.prototype.mobile.domain.home.HomeServiceTile
import com.snabbit.prototype.mobile.ui.common.DataErrorPanel
import com.snabbit.prototype.mobile.ui.common.EmptyStatePanel
import com.snabbit.prototype.mobile.ui.common.formatInrMinor
import com.snabbit.prototype.mobile.ui.theme.HomeFavoriteEnd
import com.snabbit.prototype.mobile.ui.theme.HomeFavoriteStart
import com.snabbit.prototype.mobile.ui.theme.HomePromoEnd
import com.snabbit.prototype.mobile.ui.theme.HomePromoStart
import com.snabbit.prototype.mobile.ui.theme.Peach
import com.snabbit.prototype.mobile.ui.theme.Saffron
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing
import com.snabbit.prototype.mobile.ui.theme.SnabbitTheme

@Composable
fun HomeScreen(
    state: HomeUiState,
    onEvent: (HomeUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { onEvent(HomeUiEvent.Refresh) },
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.error != null && state.services.isEmpty() -> DataErrorPanel(
                error = state.error,
                onRetry = { onEvent(HomeUiEvent.Retry) },
                modifier = Modifier.align(Alignment.Center),
            )
            else -> HomeContent(state = state, onEvent = onEvent)
        }
    }
}

@Composable
private fun HomeContent(state: HomeUiState, onEvent: (HomeUiEvent) -> Unit) {
    val largeText = LocalDensity.current.fontScale >= 1.5f
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (largeText) 2 else 3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SnabbitSpacing.standard,
            top = SnabbitSpacing.standard,
            end = SnabbitSpacing.standard,
            bottom = SnabbitSpacing.large,
        ),
        horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.standard),
    ) {
        fullWidthItem { HomeHeader(state.selectedAddress, onEvent) }
        fullWidthItem { BookingModeRow(state.preferredBookingMode, largeText, onEvent) }
        state.favorites?.let { favorites -> fullWidthItem { FavoritesBanner(favorites, onEvent) } }
        state.error?.let { error ->
            fullWidthItem { DataErrorPanel(error, onRetry = { onEvent(HomeUiEvent.Retry) }) }
        }
        fullWidthItem {
            Text(
                text = stringResource(R.string.home_services_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = SnabbitSpacing.small),
            )
        }
        if (state.services.isEmpty()) {
            fullWidthItem {
                EmptyStatePanel(
                    title = stringResource(R.string.home_empty_title),
                    description = stringResource(R.string.home_empty_description),
                )
            }
        } else {
            items(state.services, key = { it.category }) { service -> ServiceTile(service, onEvent) }
        }
        state.promo?.let { promo -> fullWidthItem { HabitPackBanner(promo, onEvent) } }
    }
}

private fun LazyGridScope.fullWidthItem(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun HomeHeader(address: SavedAddress?, onEvent: (HomeUiEvent) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onEvent(HomeUiEvent.AddressSelected) }
                .padding(vertical = SnabbitSpacing.extraSmall),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
                Icon(Icons.Outlined.ExpandMore, contentDescription = null)
            }
            Text(
                text = address?.let { stringResource(R.string.home_address_value, it.addressLine) }
                    ?: stringResource(R.string.home_location_prompt),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
        }
        HeaderAction(Icons.Outlined.AccountBalanceWallet, R.string.home_wallet_description) {
            onEvent(HomeUiEvent.WalletSelected)
        }
        Spacer(Modifier.width(SnabbitSpacing.small))
        HeaderAction(Icons.Outlined.CardGiftcard, R.string.home_rewards_description) {
            onEvent(HomeUiEvent.RewardsSelected)
        }
        Spacer(Modifier.width(SnabbitSpacing.small))
        HeaderAction(Icons.Outlined.Person, R.string.home_profile_description) {
            onEvent(HomeUiEvent.ProfileSelected)
        }
    }
}

@Composable
private fun HeaderAction(icon: ImageVector, description: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = stringResource(description), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun BookingModeRow(
    selected: HomeBookingMode?,
    largeText: Boolean,
    onEvent: (HomeUiEvent) -> Unit,
) {
    if (largeText) {
        Column(verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium)) {
            BookingModeCard(HomeBookingMode.SCHEDULED, selected, onEvent, Modifier.fillMaxWidth())
            BookingModeCard(HomeBookingMode.INSTANT, selected, onEvent, Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium)) {
            BookingModeCard(HomeBookingMode.SCHEDULED, selected, onEvent, Modifier.weight(1f))
            BookingModeCard(HomeBookingMode.INSTANT, selected, onEvent, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BookingModeCard(
    mode: HomeBookingMode,
    selected: HomeBookingMode?,
    onEvent: (HomeUiEvent) -> Unit,
    modifier: Modifier,
) {
    val instant = mode == HomeBookingMode.INSTANT
    Surface(
        onClick = { onEvent(if (instant) HomeUiEvent.InstantSelected else HomeUiEvent.ScheduleSelected) },
        modifier = modifier.height(148.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (selected == mode) 4.dp else 1.dp,
        border = BorderStroke(
            1.dp,
            if (selected == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Box(Modifier.fillMaxSize().padding(SnabbitSpacing.standard)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(
                    if (instant) Icons.Outlined.Bolt else Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(SnabbitSpacing.small).size(24.dp),
                )
            }
            if (instant) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = stringResource(R.string.home_worker_mascot_placeholder),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterEnd).size(58.dp),
                )
            }
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Text(
                    stringResource(if (instant) R.string.home_instant_title else R.string.home_schedule_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(if (instant) R.string.home_instant_subtitle else R.string.home_schedule_subtitle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                modifier = Modifier.align(Alignment.BottomEnd).size(20.dp),
            )
        }
    }
}

@Composable
private fun FavoritesBanner(favorites: FavoriteExpertsPreview, onEvent: (HomeUiEvent) -> Unit) {
    Surface(
        onClick = { onEvent(HomeUiEvent.RewardsSelected) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .background(Brush.horizontalGradient(listOf(HomeFavoriteStart, HomeFavoriteEnd)))
                .padding(SnabbitSpacing.standard),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.extraSmall)) {
                Text(
                    buildAnnotatedString {
                        append(stringResource(R.string.home_favorites_prefix))
                        withStyle(SpanStyle(color = Saffron, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold)) {
                            append(stringResource(R.string.home_favorites_emphasis))
                        }
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(R.string.home_favorites_remaining, favorites.remainingExpertsToUnlock),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            AvatarStack(favorites.visibleAvatarCount)
            Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AvatarStack(count: Int) {
    Box(modifier = Modifier.width(72.dp).height(48.dp), contentAlignment = Alignment.CenterStart) {
        repeat(count.coerceIn(0, 3)) { index ->
            Surface(
                modifier = Modifier.offset(x = (index * 18).dp).size(42.dp),
                shape = CircleShape,
                color = if (index % 2 == 0) MaterialTheme.colorScheme.primaryContainer else Peach,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
            ) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = stringResource(R.string.home_expert_avatar_placeholder, index + 1),
                    modifier = Modifier.padding(SnabbitSpacing.small),
                )
            }
        }
    }
}

@Composable
private fun ServiceTile(service: HomeServiceTile, onEvent: (HomeUiEvent) -> Unit) {
    val content = serviceContent(service.category)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (service.serviceId != null) Modifier.clickable {
                    onEvent(HomeUiEvent.ServiceSelected(service.serviceId))
                } else Modifier,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.small),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                content.icon,
                contentDescription = stringResource(R.string.home_service_artwork_placeholder, stringResource(content.label)),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(46.dp),
            )
        }
        Text(
            stringResource(content.label),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
        )
    }
}

private data class ServiceContent(val label: Int, val icon: ImageVector)

private fun serviceContent(category: HomeServiceCategory) = when (category) {
    HomeServiceCategory.HOUSE_CLEANING -> ServiceContent(R.string.home_service_house_cleaning, Icons.Outlined.Home)
    HomeServiceCategory.DUSTING_AND_WIPING -> ServiceContent(R.string.home_service_dusting, Icons.Outlined.CleaningServices)
    HomeServiceCategory.BATHROOM_CLEANING -> ServiceContent(R.string.home_service_bathroom, Icons.Outlined.Bathroom)
    HomeServiceCategory.WASH_FOLD_ARRANGE -> ServiceContent(R.string.home_service_laundry, Icons.Outlined.LocalLaundryService)
    HomeServiceCategory.CLEANING_DISHES -> ServiceContent(R.string.home_service_dishes, Icons.Outlined.Restaurant)
    HomeServiceCategory.MORE -> ServiceContent(R.string.home_service_more, Icons.Outlined.MoreHoriz)
}

@Composable
private fun HabitPackBanner(promo: HabitPackPromo, onEvent: (HomeUiEvent) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(HomePromoStart, HomePromoEnd)))
            .padding(SnabbitSpacing.standard),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium),
    ) {
        Icon(
            Icons.Outlined.ConfirmationNumber,
            contentDescription = stringResource(R.string.home_ticket_art_placeholder),
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.home_promo_visits, promo.visitCount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatInrMinor(promo.originalPriceMinor),
                    style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough),
                )
                Spacer(Modifier.width(SnabbitSpacing.small))
                Text(formatInrMinor(promo.offerPriceMinor), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        Button(
            onClick = { onEvent(HomeUiEvent.PromoSelected) },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface,
            ),
            contentPadding = PaddingValues(horizontal = SnabbitSpacing.standard, vertical = SnabbitSpacing.medium),
        ) {
            Text(stringResource(R.string.home_promo_claim))
        }
    }
}

private val previewState = HomeUiState(
    services = HomeServiceCategory.entries.mapIndexed { index, category -> HomeServiceTile(category, "service-$index") },
    selectedAddress = SavedAddress("1", "Home", "D711, New Usha Nagar", "Mumbai", "400001", 0.0, 0.0, true),
    favorites = FavoriteExpertsPreview(remainingExpertsToUnlock = 1, visibleAvatarCount = 3),
    promo = HabitPackPromo(visitCount = 3, originalPriceMinor = 89_700, offerPriceMinor = 27_900),
    isLoading = false,
)

@Preview(name = "Home light", widthDp = 411, heightDp = 914, showBackground = true)
@Composable
private fun HomeLightPreview() = SnabbitTheme { HomeScreen(previewState, {}) }

@Preview(name = "Home dark", widthDp = 411, heightDp = 914, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeDarkPreview() = SnabbitTheme { HomeScreen(previewState, {}) }

@Preview(name = "Home large text", widthDp = 411, heightDp = 914, fontScale = 2f, showBackground = true)
@Composable
private fun HomeLargeTextPreview() = SnabbitTheme { HomeScreen(previewState, {}) }
