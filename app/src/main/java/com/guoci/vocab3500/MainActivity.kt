package com.guoci.vocab3500

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guoci.vocab3500.data.AppState
import com.guoci.vocab3500.data.Phase
import com.guoci.vocab3500.platform.ShareFiles
import com.guoci.vocab3500.platform.Speaker
import com.guoci.vocab3500.ui.DoneScreen
import com.guoci.vocab3500.ui.GuociTheme
import com.guoci.vocab3500.ui.HomeScreen
import com.guoci.vocab3500.ui.Muted
import com.guoci.vocab3500.ui.QuizScreen
import com.guoci.vocab3500.ui.SettingsScreen
import com.guoci.vocab3500.ui.StudyScreen
import com.guoci.vocab3500.ui.WordsScreen
import java.time.LocalDate

/**
 * 唯一的 Activity。
 *
 * 为什么不做多 Activity / 不做 Navigation 库：主流程是一条直线（首页 → 学 → 测 →
 * 完成），屏幕之间必须共享同一个"今天的队列"。用一个 sealed 状态在 Compose 内部
 * 切换，比引入 nav 依赖再跨屏传队列更少出错，也少一个依赖。
 *
 * 跨 0 点的处理：AppState 里的 today 在创建时固定。如果 App 在后台过了一夜，
 * onResume 时发现日期变了就重建状态，避免把昨天的队列当成今天的继续用。
 */
class MainActivity : ComponentActivity() {

    private lateinit var stateHolder: MutableState<AppState>
    private lateinit var speaker: Speaker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        stateHolder = mutableStateOf(AppState(this).also { it.load() })
        speaker = Speaker(this)

        setContent {
            GuociTheme {
                val ctx = LocalContext.current
                val state = stateHolder.value
                val toast: (String) -> Unit = { msg ->
                    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                }

                DisposableEffect(Unit) {
                    speaker.start()
                    onDispose { speaker.shutdown() }
                }

                Surface(
                    Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .safeDrawingPadding(),
                    ) {
                        val error = state.loadError
                        if (error != null) {
                            ErrorScreen(error)
                        } else {
                            when (state.phase) {
                                Phase.HOME -> HomeScreen(
                                    state = state,
                                    onStart = { state.startToday() },
                                    onOpenWords = { state.open(Phase.WORDS) },
                                    onOpenSettings = { state.open(Phase.SETTINGS) },
                                )

                                Phase.LEARN -> StudyScreen(
                                    state = state,
                                    onExit = { state.backHome() },
                                    onSpeak = { speaker.speak(it) },
                                    canSpeak = speaker.available && state.progress.settings.ttsEnabled,
                                )

                                Phase.QUIZ -> QuizScreen(
                                    state = state,
                                    onExit = { state.backHome() },
                                )

                                Phase.DONE -> DoneScreen(
                                    state = state,
                                    onHome = { state.backHome() },
                                )

                                Phase.WORDS -> WordsScreen(
                                    state = state,
                                    onBack = { state.backHome() },
                                    onShareFile = { file, mime, subject ->
                                        ShareFiles.share(ctx, file, mime, subject)
                                    },
                                    onToast = toast,
                                )

                                Phase.SETTINGS -> SettingsScreen(
                                    state = state,
                                    onBack = { state.backHome() },
                                    onShareFile = { file, mime, subject ->
                                        ShareFiles.share(ctx, file, mime, subject)
                                    },
                                    onToast = toast,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 后台过夜：日期变了就重建（新的一天要重新算队列）。
        if (::stateHolder.isInitialized &&
            LocalDate.now().toEpochDay() != stateHolder.value.today
        ) {
            stateHolder.value = AppState(this).also { it.load() }
        }
    }

    override fun onDestroy() {
        if (::speaker.isInitialized) speaker.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun ErrorScreen(message: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(120.dp))
        Text("词库没能读出来", fontSize = 20.sp)
        Spacer(Modifier.height(10.dp))
        Text(message, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "重装一次 App 通常能修好；如果还不行，说明打包时词库文件没进去。",
            color = Muted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}
