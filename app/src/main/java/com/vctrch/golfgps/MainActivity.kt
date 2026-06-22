package com.vctrch.golfgps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vctrch.golfgps.ui.round.ActiveRoundScreen
import com.vctrch.golfgps.ui.search.CourseSearchScreen
import com.vctrch.golfgps.ui.theme.GolfGpsTheme
import com.vctrch.golfgps.viewmodel.RoundViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GolfGpsTheme {
                val viewModel: RoundViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val mapDisplayStyle by viewModel.mapDisplayStyle.collectAsStateWithLifecycle()

                if (state.isLoadingCourse) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (state.isRoundReady) {
                    ActiveRoundScreen(
                        state = state,
                        mapDisplayStyle = mapDisplayStyle,
                        onMapDisplayStyleChange = viewModel::setMapDisplayStyle,
                        onEndRound = viewModel::endRound,
                        onSelectHole = viewModel::selectHole,
                        onPreviousHole = viewModel::previousHole,
                        onNextHole = viewModel::nextHole,
                    )
                } else {
                    CourseSearchScreen(
                        state = state,
                        onSearchQueryChange = viewModel::onSearchQueryChange,
                        onClearSearch = viewModel::clearSearch,
                        onCourseSelected = viewModel::selectCourse,
                    )
                }
            }
        }
    }
}
