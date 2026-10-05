package dk.decpt.focustaper

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.util.Date

/**
 * UI only. Every action loads the Engine from Store, mutates it, saves it and re-syncs the
 * alarm + notification, so the activity and TimerReceiver never hold diverging copies.
 */
class MainActivity : Activity() {
    private lateinit var pal: Palette
    private lateinit var header: TextView
    private lateinit var chip: TextView
    private lateinit var digits: TextView
    private lateinit var progress: ProgressBar
    private lateinit var meta: TextView
    private lateinit var toggleBtn: Button
    private lateinit var timeline: TimelineView
    private lateinit var customSub: TextView
    private lateinit var customMini: TimelineView
    private lateinit var customBox: LinearLayout
    private lateinit var useCustom: Button
    private lateinit var exactWarn: LinearLayout

    private class PlanCard(val root: LinearLayout, val sub: TextView, val mini: TimelineView, var selected: Boolean? = null)
    private val cards = linkedMapOf<String, PlanCard>()

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 250)
        }
    }

    private fun dp(v: Number): Int = (v.toFloat() * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pal = Palette.of(this)
        styleWindow()
        setContentView(buildUi())
        actionBar?.hide()
        Notifier.ensureChannels(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        exactWarn.visibility = if (Notifier.canExact(this)) View.GONE else View.VISIBLE
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    // ---- state plumbing ------------------------------------------------------

    private fun act(block: (Engine, Long) -> Unit) {
        val e = Store.load(this)
        val now = System.currentTimeMillis()
        block(e, now)
        Store.save(this, e)
        Notifier.sync(this, e, now)
        render(e, now)
    }

    private fun tick() {
        val e = Store.load(this)
        val now = System.currentTimeMillis()
        if (e.catchUp(now) > 0) {
            Store.save(this, e)
            Notifier.alert(this, e)
            Notifier.sync(this, e, now)
        }
        render(e, now)
    }

    // ---- rendering -------------------------------------------------------------

    private fun render(e: Engine, now: Long) {
        val seg = e.current
        val done = e.st.done
        val col = if (done) pal.long else pal.phase(seg.phase)
        val rem = e.remaining(now)

        header.text = "Focus Taper · ${Plans.def(e.cfg.plan).name}"
        val (n, of) = e.workPosition()
        chip.text = when {
            done -> "PLAN COMPLETE"
            seg.phase == Phase.WORK -> "● WORK · $n OF $of"
            else -> "● " + seg.phase.label.uppercase()
        }
        chip.setTextColor(col)
        digits.text = if (done) "00:00" else Fmt.mmss(rem)
        progress.progress = (e.progress(now) * 1000).toInt()
        progress.progressTintList = ColorStateList.valueOf(col)

        val nx = e.next
        val nextText = when {
            done -> "Tap Restart to run it again"
            nx != null -> "Next: ${nx.phase.label} ${nx.min} min"
            else -> "Last block"
        }
        val endText = when {
            done -> ""
            e.running -> "Ends ≈ " + DateFormat.getTimeFormat(this).format(Date(now + e.planRemaining(now)))
            else -> Fmt.dur(e.planRemaining(now)) + " left in plan"
        }
        meta.text = if (endText.isEmpty()) nextText else "$nextText    $endText"

        toggleBtn.text = when {
            done -> "Restart"
            e.running -> "Pause"
            e.st.remMs < e.st.lenMs -> "Resume"
            else -> "Start"
        }
        toggleBtn.backgroundTintList = ColorStateList.valueOf(col)
        timeline.set(e.segs, e.st.idx, e.progress(now), done)

        cards.forEach { (k, c) ->
            val s = Plans.build(k, e.cfg)
            val sub = "${Plans.def(k).sub} · ${Fmt.dur(Plans.totalMin(s) * 60_000L)}"
            if (c.sub.text.toString() != sub) c.sub.text = sub
            c.mini.set(s, -1, 0f, false)
            val sel = k == e.cfg.plan
            if (c.selected != sel) { c.root.background = cardBg(sel); c.selected = sel }
        }
        val cs = Plans.build("custom", e.cfg)
        val c = e.cfg
        customSub.text = "${c.customWork}/${c.customBreak} × ${c.customReps}" +
            (if (c.longBreak > 0) " + long ${c.longBreak}" else "") +
            " · ${Fmt.dur(Plans.totalMin(cs) * 60_000L)}"
        customMini.set(cs, -1, 0f, false)
        val customSel = c.plan == "custom"
        customBox.background = cardBg(customSel)
        useCustom.text = if (customSel) "In use" else "Use custom"
    }

    // ---- UI construction -------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun styleWindow() {
        window.statusBarColor = pal.bg
        window.navigationBarColor = pal.bg
        if (!pal.night) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    private fun cardBg(selected: Boolean) = GradientDrawable().apply {
        setColor(pal.surface)
        cornerRadius = dp(10).toFloat()
        setStroke(if (selected) dp(2) else dp(1), if (selected) pal.fg else pal.line)
    }

    private fun pill(fill: Int, strokeCol: Int) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(999).toFloat(); setStroke(dp(1), strokeCol)
    }

    private fun text(size: Float, color: Int = pal.fg, bold: Boolean = false) = TextView(this).apply {
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun label(s: String) = text(12f, pal.muted, bold = true).apply {
        this.text = s.uppercase()
        letterSpacing = 0.09f
        setPadding(0, dp(20), 0, dp(8))
    }

    private fun button(s: String, primary: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = s
        isAllCaps = false
        textSize = 15f
        stateListAnimator = null
        minHeight = dp(44); minimumHeight = dp(44)
        setPadding(dp(16), 0, dp(16), 0)
        if (primary) {
            background = pill(pal.work, pal.work)
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            minWidth = dp(120); minimumWidth = dp(120)
        } else {
            background = pill(pal.surface, pal.line)
            setTextColor(pal.fg)
        }
        setOnClickListener { onClick() }
    }

    private fun row(gravity: Int = Gravity.CENTER) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        this.gravity = gravity
    }

    private fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
                   top: Int = 0, end: Int = 0, weight: Float = 0f) =
        LinearLayout.LayoutParams(w, h, weight).apply { topMargin = top; marginEnd = end }

    /** Number field that commits on Done or focus loss, clamped to [lo, hi]. */
    private fun numberField(caption: String, value: Int, lo: Int, hi: Int, commit: (Engine, Int) -> Unit): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(text(12f, pal.muted).apply { text = caption })
        val et = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE
            setText(value.toString())
            textSize = 16f
            typeface = Typeface.MONOSPACE
            setTextColor(pal.fg)
            minWidth = dp(80); minimumWidth = dp(80)
            backgroundTintList = ColorStateList.valueOf(pal.muted)
        }
        fun commitValue() {
            val v = et.text.toString().toIntOrNull()?.coerceIn(lo, hi)
            if (v == null) return
            if (et.text.toString() != v.toString()) et.setText(v.toString())
            act { e, _ -> commit(e, v); e.rebuild() }
        }
        et.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { commitValue(); et.clearFocus() }
            false
        }
        et.setOnFocusChangeListener { _, has -> if (!has) commitValue() }
        box.addView(et)
        return box
    }

    private fun switch(caption: String, value: Boolean, commit: (Engine, Boolean) -> Unit) = Switch(this).apply {
        text = caption
        textSize = 15f
        setTextColor(pal.fg)
        isChecked = value
        setPadding(0, dp(6), 0, dp(6))
        setOnCheckedChangeListener { _, checked -> act { e, _ -> commit(e, checked) } }
    }

    private fun planCard(def: PlanDef): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
            isClickable = true
            setOnClickListener { act { e, _ -> e.selectPlan(def.key) } }
        }
        root.addView(text(16f, bold = true).apply { text = def.name })
        val sub = text(12f, pal.muted).apply { typeface = Typeface.MONOSPACE }
        root.addView(sub, lp(top = dp(2)))
        val mini = TimelineView(this, pal, mini = true)
        root.addView(mini, lp(h = dp(8), top = dp(10)))
        cards[def.key] = PlanCard(root, sub, mini)
        return root
    }

    private fun buildUi(): View {
        val e = Store.load(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(40))
        }

        header = text(17f, bold = true)
        col.addView(header)

        // Clock
        chip = text(13f, pal.work, bold = true).apply { letterSpacing = 0.09f; gravity = Gravity.CENTER }
        col.addView(chip, lp(top = dp(28)))
        digits = TextView(this).apply {
            textSize = 84f
            setTextColor(pal.fg)
            typeface = Typeface.create(Typeface.MONOSPACE, 300, false)
            letterSpacing = -0.04f
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        col.addView(digits, lp(top = dp(8)))
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressBackgroundTintList = ColorStateList.valueOf(pal.line)
        }
        col.addView(progress, lp(h = dp(6), top = dp(14)))
        meta = text(14f, pal.muted).apply { gravity = Gravity.CENTER }
        col.addView(meta, lp(top = dp(10)))

        val controls = row()
        controls.addView(button("−1") { act { en, now -> en.adjust(-1, now) } }, lp(ViewGroup.LayoutParams.WRAP_CONTENT, end = dp(8)))
        toggleBtn = button("Start", primary = true) { act { en, now -> en.toggle(now) } }
        controls.addView(toggleBtn, lp(ViewGroup.LayoutParams.WRAP_CONTENT, end = dp(8)))
        controls.addView(button("Next") { act { en, now -> en.skip(now) } }, lp(ViewGroup.LayoutParams.WRAP_CONTENT, end = dp(8)))
        controls.addView(button("+1") { act { en, now -> en.adjust(1, now) } }, lp(ViewGroup.LayoutParams.WRAP_CONTENT))
        col.addView(controls, lp(top = dp(16)))
        val controls2 = row()
        controls2.addView(button("Restart block") { act { en, now -> en.restartBlock(now) } }, lp(ViewGroup.LayoutParams.WRAP_CONTENT, end = dp(8)))
        controls2.addView(button("Reset plan") { act { en, _ -> en.reset() } }, lp(ViewGroup.LayoutParams.WRAP_CONTENT))
        col.addView(controls2, lp(top = dp(8)))

        exactWarn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(text(13f, pal.work).apply { text = "Exact alarms are off, so block ends may be late while the screen is off." })
            addView(button("Allow exact alarms") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                    } catch (_: ActivityNotFoundException) {}
                }
            }, lp(ViewGroup.LayoutParams.WRAP_CONTENT, top = dp(6)))
        }
        col.addView(exactWarn, lp(top = dp(12)))

        // Timeline
        timeline = TimelineView(this, pal, mini = false, onJump = { i -> act { en, now -> en.jump(i, now) } })
        col.addView(timeline, lp(h = dp(44), top = dp(24)))
        col.addView(text(12f, pal.muted).apply { text = "Tap a block to jump to it" }, lp(top = dp(6)))

        // Plans
        col.addView(label("Tapering"))
        Plans.all.filter { it.group == "taper" }.forEach { col.addView(planCard(it), lp(top = dp(8))) }
        col.addView(label("Classic"))
        Plans.all.filter { it.group == "classic" }.forEach { col.addView(planCard(it), lp(top = dp(8))) }

        customBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
        }
        val head = row(Gravity.CENTER_VERTICAL)
        val headText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headText.addView(text(16f, bold = true).apply { text = "Custom" })
        customSub = text(12f, pal.muted).apply { typeface = Typeface.MONOSPACE }
        headText.addView(customSub)
        head.addView(headText, lp(0, weight = 1f))
        useCustom = button("Use custom") { act { en, _ -> en.selectPlan("custom") } }
        head.addView(useCustom, lp(ViewGroup.LayoutParams.WRAP_CONTENT))
        customBox.addView(head)
        customMini = TimelineView(this, pal, mini = true)
        customBox.addView(customMini, lp(h = dp(8), top = dp(10)))
        val fields = row(Gravity.START)
        fields.addView(numberField("Work (min)", e.cfg.customWork, 1, 240) { en, v -> en.cfg.customWork = v }, lp(ViewGroup.LayoutParams.WRAP_CONTENT, end = dp(16)))
        fields.addView(numberField("Break (min)", e.cfg.customBreak, 0, 120) { en, v -> en.cfg.customBreak = v }, lp(ViewGroup.LayoutParams.WRAP_CONTENT, end = dp(16)))
        fields.addView(numberField("Repeat ×", e.cfg.customReps, 1, 20) { en, v -> en.cfg.customReps = v }, lp(ViewGroup.LayoutParams.WRAP_CONTENT))
        customBox.addView(fields, lp(top = dp(10)))
        col.addView(customBox, lp(top = dp(10)))
        col.addView(text(13f, pal.muted).apply {
            text = "Switching plans resets the timer. The long break replaces the final break in every plan; set it to 0 to end on the last work block."
        }, lp(top = dp(8)))

        // Settings
        col.addView(label("Settings"))
        val nums = row(Gravity.START)
        nums.addView(numberField("Long break (min)", e.cfg.longBreak, 0, 180) { en, v -> en.cfg.longBreak = v }, lp(ViewGroup.LayoutParams.WRAP_CONTENT, end = dp(16)))
        nums.addView(numberField("Taper break (min)", e.cfg.taperBreak, 0, 60) { en, v -> en.cfg.taperBreak = v }, lp(ViewGroup.LayoutParams.WRAP_CONTENT))
        col.addView(nums)
        col.addView(switch("Auto-start next block", e.cfg.autoStart) { en, v -> en.cfg.autoStart = v }, lp(top = dp(8)))
        col.addView(switch("Alert when a block ends", e.cfg.chime) { en, v -> en.cfg.chime = v })
        col.addView(button("Notification sounds…") {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }, lp(ViewGroup.LayoutParams.WRAP_CONTENT, top = dp(8)))

        return ScrollView(this).apply {
            setBackgroundColor(pal.bg)
            isFillViewport = true
            addView(col)
        }
    }
}
