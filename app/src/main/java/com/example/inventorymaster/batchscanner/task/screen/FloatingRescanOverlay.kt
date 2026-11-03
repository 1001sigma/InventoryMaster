package com.example.inventorymaster.batchscanner.task.screen

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import com.example.inventorymaster.batchscanner.task.tracking.CodeTracker
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Surface
import androidx.compose.ui.text.font.FontWeight
import com.example.inventorymaster.utils.BarcodeColorHasher
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inventorymaster.ui.analyzer.SimpleFloatingScanner
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

/** 长按锁定连续扫描的时长（毫秒）：按住进度环走满即开启，松手不停止、再单击才结束 */
private const val LONG_PRESS_LOCK_MS = 1500L

/**
 * 镜头显示区高度——弹窗高度的唯一调节旋钮（自己微调改这里）：
 * 弹窗总高 ≈ CAMERA_HEIGHT + 92dp（上下内边距 20 + 底部结果条 52 + 间距 10，状态栏另计）。
 * 镜头为全宽时短边即此高度，扫码框边长 = 短边 × SCAN_FRAME_RATIO：
 * 调高 → 框更大、"完整入框"更容易，但遮挡下方单据更多；建议范围 180~260dp。
 */
private val CAMERA_HEIGHT = 275.dp

/** 扫码框边长占比（相对镜头视图短边），只识别完整落在框内的码：
 *  0.7 允许拿得更近（码在画面里更大、模块更粗，小 DataMatrix 更容易读出），
 *  也减少"读出但一角出框被丢弃"的浪费 */
private const val SCAN_FRAME_RATIO = 0.7f

/** 内容行的最大显示字符数：超过则中段省略（UDI 的 DI 在头部、批号/序列号在尾部，两端最有用） */
private const val CONTENT_MAX_CHARS = 48

/** 中段省略：保留头尾，中间以 … 代替，总长不超过 [maxChars] */
private fun middleEllipsis(value: String, maxChars: Int = CONTENT_MAX_CHARS): String {
    if (value.length <= maxChars) return value
    val head = maxChars / 2
    val tail = maxChars - head - 1
    return value.take(head) + "…" + value.takeLast(tail)
}

/** 按原始长度分档选字号：长码值自动缩小，保证单行尽量完整显示 */
private fun fontSizeForLength(length: Int): TextUnit = when {
    length <= 26 -> 17.sp
    length <= 42 -> 14.5.sp
    else -> 13.sp
}

/**
 * 任务详情页的补扫弹窗（补扫按钮触发）
 *
 * - 居顶模态大弹窗：顶部两行扫码内容（当前 + 上一次半透明，点击行复制），中部扫码镜头
 *   （白色四角扫码框，只识别完整入框的码），镜头下方直接收底以少遮挡单据
 * - 圆形扫码按钮为悬浮可拖动（初始位于镜头下缘中部）：
 *   单击 = 单发识别一码后自动暂停（连续扫描中单击 = 结束连续扫描）；
 *   长按 1.5s（按钮外圈进度环走满 + 震动）= 锁定连续扫描，松手不停止；
 *   400ms~1.5s 的犹豫按压不生效（防误停连扫）；按住拖动 = 挪位置
 * - 弹窗外区域放行滚动（可滑下方单据），仅拦截点击防止误触
 * - 同码在冷却窗内只计一次，冷却后再次识别会再次计入（一个码=一件的快速连扫场景）
 * - 点击"已扫 N 件"胶囊切换内容去重（绿=去重开启：已计过件的同码值不再计入；
 *   红=去重关闭：维持"重复也计入"语义）。去重记忆提升在任务页持有，跨弹窗开关保留，
 *   点击切换时清空重置；换任务重建屏幕自动归零
 * - 右上角 × 或系统返回键关闭，关闭时 Toast 本次补扫件数
 */
@Composable
fun FloatingRescanOverlay(
    onCode: (String) -> Unit,
    onDismiss: () -> Unit,
    // 内容去重记忆：本次任务内已计过件的码值集合（跨弹窗开关保留，切换去重开关时清空）
    dedupHistory: MutableSet<String>
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // 扫描模式：0=暂停 1=单发武装 2=连续扫描（默认开启连续扫描，即开即扫，免除繁琐手势）
    var scanMode by remember { mutableIntStateOf(2) }
    var scanCount by remember { mutableIntStateOf(0) }
    // 去重开关：默认关闭内容去重（蓝色·空间追踪），由 CodeTracker 空间轨迹去重；点击可切换为绿色·内容去重
    var dedupEnabled by remember { mutableStateOf(false) }
    var currentCode by remember { mutableStateOf<String?>(null) }
    var previousCode by remember { mutableStateOf<String?>(null) }
    var lastFeedbackTime by remember { mutableLongStateOf(0L) }

    // 长按 1.5s 的进度环（0→1 走满即锁定连续扫描）；转为拖动或提前抬起立即归零。
    // AwaitPointerEventScope 是受限挂起作用域（不能 launch/调 Animatable），手势侧
    // 只写下面两个桥接状态，动画由随后的 LaunchedEffect 协程驱动
    val longPressProgress = remember { Animatable(0f) }
    var pressGeneration by remember { mutableIntStateOf(0) }  // 每次按下 +1，重启动画协程
    var pressActive by remember { mutableStateOf(false) }      // 本次按压仍在进行（未拖动/未抬起）
    var lockedThisPress by remember { mutableStateOf(false) }  // 本次按压进度环已走满（锁定连扫）

    LaunchedEffect(pressGeneration) {
        if (!pressActive) return@LaunchedEffect
        lockedThisPress = false
        try {
            longPressProgress.snapTo(0f)
            longPressProgress.animateTo(
                1f,
                tween(LONG_PRESS_LOCK_MS.toInt(), easing = LinearEasing)
            )
            // 动画自然走完 = 按住满 1.5s：锁定连续扫描（计时由动画帧回调驱动，
            // 手指静止没有指针事件也能照常触发）
            lockedThisPress = true
            scanMode = 2
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } catch (_: CancellationException) {
            // 被下一次按压顶替或按压结束被打断：进度环由重启/归零逻辑处理
        }
    }
    // 抬起/转拖动/取消：立即停掉进度环并归零（snapTo 会取消进行中的 animateTo）
    LaunchedEffect(pressActive, pressGeneration) {
        if (!pressActive) longPressProgress.snapTo(0f)
    }

    // 连续扫描锁定中的呼吸脉冲（按钮白描边 alpha 往返），提示"正在连扫、单击即停"
    val scanPulse by rememberInfiniteTransition(label = "scanPulse").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "scanPulseAlpha"
    )

    // 悬浮扫码按钮（可拖动）：初始位置取镜头下缘中点（容器内部坐标），
    // 首次拖动前每次布局自校正（跟随镜头位置），拖动开始后锁定
    val density = LocalDensity.current
    // 进度环外径比按钮大一圈，套在按钮外围绘制；悬浮偏移/越界钳制以环为基准
    val ringSize = 76.dp
    val ringSizePx = with(density) { ringSize.toPx() }.roundToInt()
    val buttonOffset = remember { mutableStateOf(IntOffset(0, 0)) }
    val overlayOrigin = remember { mutableStateOf(Offset.Zero) }
    var buttonPlaced by remember { mutableStateOf(false) }
    var buttonDragged by remember { mutableStateOf(false) }

    fun close() {
        if (scanCount > 0) {
            Toast.makeText(context, "本次补扫共 $scanCount 件", Toast.LENGTH_SHORT).show()
        }
        onDismiss()
    }

    fun copyCode(value: String) {
        clipboard.setText(AnnotatedString(value))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }

    fun handleCode(value: String) {
        if (scanMode == 0) return // 处于暂停状态时直接忽略入队的回调
        val now = System.currentTimeMillis()
        // 内容去重（开启时）：已计过件的同码值不再计入——内容行照常刷新（能看见"读到了"），
        // 但不计件、不震动、不入账；首次见到该码值先记入去重记忆
        if (dedupEnabled) {
            if (value in dedupHistory) {
                previousCode = currentCode
                currentCode = value
                return
            }
            dedupHistory.add(value)
        }
        // 空间追踪去重（关闭内容去重时）：由 CodeTracker 保证同一物理实物不重复计入，不同实物同内容独立计入
        scanCount++
        previousCode = currentCode
        currentCode = value
        // 单发模式扫到一码即暂停，等下一次单击/长按
        if (scanMode == 1) scanMode = 0
        // 触觉反馈按 200ms 节流
        if (now - lastFeedbackTime > 200) {
            lastFeedbackTime = now
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        onCode(value)
    }

    // 核心物理轨迹跟踪器：负责空间位置关联、实物去重与防抖
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val codeTracker = remember {
        CodeTracker(
            onCounted = { track ->
                mainHandler.post {
                    handleCode(track.value)
                }
            },
            minHits = 2,
            lostTimeoutNs = 700_000_000L,
            countedLostTimeoutNs = 200_000_000L,
            matchRadiusRatio = 1.2f
        )
    }

    // 拦截物理返回键/侧滑返回手势，走 close() 统一弹关闭小结
    BackHandler { close() }

    // 全屏容器：不挂任何手势拦截（detectTapGestures 会吞 down，导致背后表单的
    // 滚动/拖动检测失效）——点击拦截下沉到弹窗卡片本体，卡片以外完全交给任务页
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin.value = it.positionInRoot() },
        contentAlignment = Alignment.TopCenter
    ) {
        val maxW = constraints.maxWidth
        val maxH = constraints.maxHeight

        // 大弹窗卡片：宽度拉满、贴内容区顶边；无遮罩后用投影分层
        val cardShape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .shadow(elevation = 12.dp, shape = cardShape)
                .clip(cardShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, cardShape)
                .pointerInput(Unit) {
                    // 只拦截卡片覆盖范围内的点击（防穿透误触背后的单据行/按钮）；
                    // 卡片以外（下方表单）的滚动与点击完全不受影响
                    detectTapGestures { }
                }
                .padding(10.dp)
        ) {
            // ================= 主体：扫码镜头（占卡片顶部，× 悬浮右上角） =================
            // 宽度不变、高度 CAMERA_HEIGHT；scanFrameRatio>0 时显示白色四角扫码框，
            // 且只识别完整入框的码
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CAMERA_HEIGHT)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .onGloballyPositioned { coords ->
                        if (!buttonDragged) {
                            // 首次拖动前每次布局自校正：镜头位置（容器内部坐标）+ 居中偏移
                            // （偏移基准为外环，按钮本体在外环内居中）
                            buttonPlaced = true
                            val pos = coords.positionInRoot() - overlayOrigin.value
                            buttonOffset.value = IntOffset(
                                (pos.x + coords.size.width / 2f - ringSizePx / 2f).roundToInt(),
                                (pos.y + coords.size.height - ringSizePx * 0.6f).roundToInt()
                            )
                        }
                    }
            ) {
                SimpleFloatingScanner(
                    modifier = Modifier.fillMaxSize(),
                    onScanResult = ::handleCode,
                    isPaused = { scanMode == 0 },
                    stopAfterFirst = false,
                    scanFrameRatio = SCAN_FRAME_RATIO,
                    tracker = codeTracker
                )
                // 关闭 ×：悬浮镜头右上角（半透明黑圆底，亮画面下也清晰）
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(30.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { close() },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭补扫",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                // 扫描状态角标：绿=扫描中，灰=已暂停
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .size(10.dp)
                        .background(
                            if (scanMode != 0) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                            CircleShape
                        )
                )
                // 状态提示：扫到第一件后自动隐藏避免遮挡
                if (scanCount == 0) {
                    Text(
                        if (scanMode == 2) "连续扫描中 · 对准条码自动计件" else "已暂停 · 单击按钮恢复",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ================= 底部重构：控制行（已扫左移 + 切换平齐）+ 下方条码（独享全宽 + 1:1 色彩联动） =================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                // 1. 上层控制行：以扫件数（左移）与 模式切换（平齐同一水平行）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 已扫件数胶囊（左侧）
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "已扫 $scanCount 件",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }

                    // 模式切换胶囊（平齐同一水平行，右侧易触达）
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (dedupEnabled) Color(0xFF4CAF50).copy(alpha = 0.12f) else Color(0xFF1E88E5).copy(alpha = 0.12f),
                        border = BorderStroke(
                            1.5.dp,
                            if (dedupEnabled) Color(0xFF4CAF50) else Color(0xFF1E88E5)
                        ),
                        modifier = Modifier
                            .clickable {
                                dedupEnabled = !dedupEnabled
                                dedupHistory.clear()
                                codeTracker.reset()
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                tint = if (dedupEnabled) Color(0xFF2E7D32) else Color(0xFF1565C0),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (dedupEnabled) "内容去重" else "空间追踪",
                                color = if (dedupEnabled) Color(0xFF2E7D32) else Color(0xFF1565C0),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2. 下层条码显示行：置于下方，独享整行 100% 宽度，带色彩胶囊/圆点联动
                // 第一行：当前扫码内容（带高亮联动色彩胶囊）
                ScanContentLine(
                    value = currentCode,
                    dimmed = false,
                    placeholder = "将镜头对准条码…",
                    indicatorColor = currentCode?.let { BarcodeColorHasher.getColor(it) },
                    onClick = { it?.let(::copyCode) }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 第二行：上一次扫码内容（半透明，带上一次历史色彩小圆点）
                ScanContentLine(
                    value = previousCode,
                    dimmed = true,
                    placeholder = "—",
                    indicatorColor = previousCode?.let { BarcodeColorHasher.getColor(it) },
                    onClick = { it?.let(::copyCode) }
                )
            }
        }

        // ================= 悬浮扫码按钮（可拖动） =================
        // 单击=单发武装（连扫中单击=结束连扫）；长按1.5s=锁定连续扫描（松手不停止，
        // 再单击才结束）；400ms~1.5s 的犹豫按压不生效；按住拖动=挪位置
        if (buttonPlaced) {
            Box(
                modifier = Modifier
                    // 必须显式 TopStart：容器 contentAlignment=TopCenter 会先把按钮水平居中，
                    // offset 再叠加会导致"左拖卡在中线、右拖移出屏幕"的坐标基准错位
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            buttonOffset.value.x.coerceIn(0, (maxW - ringSizePx).coerceAtLeast(0)),
                            buttonOffset.value.y.coerceIn(0, (maxH - ringSizePx).coerceAtLeast(0))
                        )
                    }
                    .size(ringSize)
            ) {
                // 长按进度环：半透明轨道 + 已走过弧线，按下即走、1.5s 走满触发锁定、打断归零
                Canvas(modifier = Modifier.matchParentSize()) {
                    val strokeWidth = 3.5.dp.toPx()
                    val inset = strokeWidth / 2 + 1.dp.toPx()
                    val ringDiameter = size.minDimension - inset * 2
                    if (ringDiameter <= 0f) return@Canvas
                    drawCircle(
                        color = Color.White.copy(alpha = 0.30f),
                        radius = ringDiameter / 2,
                        center = center,
                        style = Stroke(width = strokeWidth)
                    )
                    val progress = longPressProgress.value
                    if (progress > 0f) {
                        drawArc(
                            color = Color.White,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(ringDiameter, ringDiameter),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }
                // 按钮本体
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            when (scanMode) {
                                2 -> Color(0xFF4CAF50)                 // 连续扫描中
                                1 -> MaterialTheme.colorScheme.primary // 单发武装
                                else -> Color(0xFF9E9E9E)              // 已暂停
                            }
                        )
                        // 连扫锁定时白描边呼吸脉冲，提示"正在连扫、单击即停"
                        .border(
                            3.dp,
                            Color.White.copy(alpha = if (scanMode == 2) scanPulse else 1f),
                            CircleShape
                        )
                        .pointerInput(maxW, maxH) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var moved = false
                                var cancelled = true
                                var upTime = 0L
                                var dragSum = Offset.Zero
                                pressGeneration++
                                pressActive = true

                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (change.changedToUp()) {
                                            cancelled = false
                                            upTime = change.uptimeMillis
                                            break
                                        }

                                        val delta = change.positionChange()
                                        if (!moved && delta != Offset.Zero) {
                                            dragSum += delta
                                            if (dragSum.getDistance() > viewConfiguration.touchSlop) {
                                                moved = true
                                                pressActive = false // 转为拖动：作废本次长按计时
                                            }
                                        }
                                        if (moved) {
                                            // 已锁定后拖动：连扫继续跑，仅停进度环
                                            change.consume()
                                            buttonDragged = true
                                            buttonOffset.value = IntOffset(
                                                (buttonOffset.value.x + delta.x).roundToInt()
                                                    .coerceIn(0, (maxW - ringSizePx).coerceAtLeast(0)),
                                                (buttonOffset.value.y + delta.y).roundToInt()
                                                    .coerceIn(0, (maxH - ringSizePx).coerceAtLeast(0))
                                            )
                                        }
                                    }
                                } finally {
                                    pressActive = false
                                }

                                when {
                                    lockedThisPress -> Unit // 长按锁定后松手：连续扫描继续，直到再次单击
                                    moved -> Unit          // 拖动结束，无模式变化
                                    else -> if (
                                        !cancelled &&
                                        upTime - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
                                    ) {
                                        // 仅快速单击生效：连扫中=暂停，暂停中=恢复连续扫描；
                                        // 400ms~1.5s 的犹豫按压忽略，防止误停连续扫描
                                        scanMode = if (scanMode == 2) 0 else 2
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

/** 弹窗底部的一行扫码内容：null 显示占位；可点击复制；支持左侧动态色彩胶囊/指示点 */
@Composable
private fun ScanContentLine(
    value: String?,
    dimmed: Boolean,
    placeholder: String,
    indicatorColor: Color? = null,
    onClick: (String?) -> Unit
) {
    val display = value?.let { middleEllipsis(it) } ?: placeholder
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.5f else 1f)
            .pointerInput(value) {
                detectTapGestures(
                    onTap = {
                        if (value != null) onClick(value)
                    }
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (value != null && indicatorColor != null) {
            if (!dimmed) {
                // 当前码：高度 16dp、宽度 4.5dp 的色彩小胶囊
                Box(
                    modifier = Modifier
                        .size(width = 4.5.dp, height = 16.dp)
                        .background(indicatorColor, RoundedCornerShape(2.dp))
                )
            } else {
                // 历史码：直径 6dp 的色彩小圆点
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(indicatorColor.copy(alpha = 0.7f), CircleShape)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = display,
            fontSize = value?.let { fontSizeForLength(it.length) } ?: 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}
