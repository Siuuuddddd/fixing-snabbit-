package com.snabbit.prototype.mobile.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.ui.components.SnabbitButton
import com.snabbit.prototype.mobile.ui.theme.Mint
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing
import kotlinx.coroutines.flow.distinctUntilChanged

private data class OnboardingPage(
    val title: Int,
    val description: Int,
    val artworkDescription: Int,
    val icon: ImageVector,
    val background: Color,
)

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onPageChanged: (Int) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = listOf(
        OnboardingPage(
            R.string.onboarding_book_title,
            R.string.onboarding_book_description,
            R.string.onboarding_book_artwork_description,
            Icons.Outlined.CleaningServices,
            Mint,
        ),
        OnboardingPage(
            R.string.onboarding_verified_title,
            R.string.onboarding_verified_description,
            R.string.onboarding_verified_artwork_description,
            Icons.Outlined.VerifiedUser,
            MaterialTheme.colorScheme.secondaryContainer,
        ),
        OnboardingPage(
            R.string.onboarding_payments_title,
            R.string.onboarding_payments_description,
            R.string.onboarding_payments_artwork_description,
            Icons.Outlined.Payments,
            MaterialTheme.colorScheme.primaryContainer,
        ),
    )
    val pagerState = rememberPagerState(initialPage = state.page, pageCount = { pages.size })
    LaunchedEffect(state.page) {
        if (pagerState.currentPage != state.page) pagerState.animateScrollToPage(state.page)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect(onPageChanged)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(SnabbitSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onSkip, enabled = !state.isCompleting) {
                Text(stringResource(R.string.action_skip))
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { index ->
            val page = pages[index]
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = SnabbitSpacing.small),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier.size(220.dp).background(page.background, MaterialTheme.shapes.large),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = page.icon,
                        contentDescription = stringResource(page.artworkDescription),
                        modifier = Modifier.size(104.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(SnabbitSpacing.extraLarge))
                Text(
                    text = stringResource(page.title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(SnabbitSpacing.medium))
                Text(
                    text = stringResource(page.description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Row(
            modifier = Modifier.padding(vertical = SnabbitSpacing.large),
            horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.small),
        ) {
            pages.indices.forEach { index ->
                Box(
                    Modifier
                        .size(if (index == state.page) 12.dp else 8.dp)
                        .background(
                            if (index == state.page) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape,
                        ),
                )
            }
        }
        SnabbitButton(
            text = stringResource(if (state.page == pages.lastIndex) R.string.action_get_started else R.string.action_continue),
            onClick = onContinue,
            loading = state.isCompleting,
        )
    }
}
