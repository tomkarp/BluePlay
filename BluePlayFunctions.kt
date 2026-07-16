// BluePlay – a small game engine for BlueJ (Kotlin).

import java.awt.AlphaComposite
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.SwingUtilities

// =====================================================================
//  BluePlay – game engine (window, simulation clock, keyboard/mouse).
//  This file contains NO class - only functions. The few public functions
//  here are the student API (e.g. isKeyDown("up") in act()); everything
//  further down is internal/private plumbing and hidden from BlueJ.
// =====================================================================

// ----- For students: usable directly in act() -----

/**
 * true while the key is held down. Key names: "left", "right", "up", "down",
 * "space", "enter", "escape", "shift", "control", "tab", "backspace",
 * and single characters such as "a" or "7".
 */
fun isKeyDown(key: String): Boolean = keysDown.contains(key.lowercase())

/** Plays a sound file (WAV) once; looked up in the folder "sounds/" or under the given path. */
fun playSound(fileName: String) {
    val (format, bytes) = soundCache.getOrPut(fileName) { loadSound(fileName) }
    val clip = AudioSystem.getClip()
    clip.open(format, bytes, 0, bytes.size)
    clip.addLineListener { e -> if (e.type == LineEvent.Type.STOP) clip.close() }
    clip.start()
}

// =====================================================================
//  Called internally by World / Actor / Image (display, mapping, control).
//  All @JvmSynthetic so they stay usable from Kotlin but do NOT clutter the
//  BlueJ menus (BlueJ hides synthetic methods).
// =====================================================================

@get:JvmSynthetic
internal val simLock = Any()

// Mapping actor -> world. Lives here (not as a field in Actor) so that Actor
// has no internal members that would show up name-mangled in the BlueJ menus.
@JvmSynthetic
internal fun worldOf(actor: Actor): World? = synchronized(simLock) { actorWorlds[actor] }

@JvmSynthetic
internal fun setWorldOf(actor: Actor, world: World?) {
    synchronized(simLock) {
        if (world == null) actorWorlds.remove(actor) else actorWorlds[actor] = world
    }
}

// Text layer per world (managed by World.showText). Kept here (not as a field in
// World) so that World has no internal members that would show up in the BlueJ menus.
@JvmSynthetic
internal fun setTextOf(world: World, x: Int, y: Int, text: String) {
    synchronized(simLock) {
        val map = worldTexts.getOrPut(world) { LinkedHashMap() }
        if (text.isEmpty()) map.remove(Pair(x, y)) else map[Pair(x, y)] = text
    }
    repaintWorld()
}

@JvmSynthetic
internal fun textsOf(world: World): List<Triple<Int, Int, String>> = synchronized(simLock) {
    worldTexts[world]?.map { Triple(it.key.first, it.key.second, it.value) } ?: emptyList()
}

@JvmSynthetic
internal fun showWorld(world: World) {
    stop()
    currentWorld = world
    SwingUtilities.invokeLater {
        canvas.preferredSize = Dimension(world.width * world.cellSize, world.height * world.cellSize)
        val firstShow = !frame.isVisible
        frame.pack()
        // only center on the very first show; keep the position on reset / re-run
        if (firstShow) frame.setLocationRelativeTo(null)
        frame.isVisible = true
        canvas.repaint()
    }
}

/** Speed of the simulation (1..100). */
fun getSpeed(): Int = speedValue

/** Sets the speed of the simulation (1..100). */
fun setSpeed(value: Int) {
    speedValue = value.coerceIn(1, 100)
    SwingUtilities.invokeLater { if (speedSlider.value != speedValue) speedSlider.value = speedValue }
}

/** Starts the game: repeatedly runs act() for all objects. */
fun start() { running = true; updateControls() }
/** Stops the game (can be started again with start()). */
fun stop() { running = false; updateControls() }
/** Runs exactly one step (one act() for every object). */
fun step() { if (!running) oneStep() }

// ----- control bar behaviour (Act / Run-Pause / Reset / speed) -----

private fun toggleRun() {
    if (running) stop() else { start(); canvas.requestFocusInWindow() }
}

// Reset reloads the start situation by running the top-level main() again.
// Convention: there must be a file Main.kt with a parameterless fun main().
private fun reset() {
    stop()
    try {
        Class.forName("MainKt").getDeclaredMethod("main").invoke(null)
    } catch (e: ClassNotFoundException) {
        println("Reset needs a file 'Main.kt' with a parameterless 'fun main()'.")
    } catch (e: NoSuchMethodException) {
        println("Reset needs a parameterless 'fun main()' in Main.kt.")
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun updateControls() {
    SwingUtilities.invokeLater {
        runButton.text = if (running) "Stop" else "Start"
        runButton.toolTipText =
            if (running) "stop(): pause the game"
            else "start(): run the game (calling act over and over)"
        stepButton.isEnabled = !running
    }
}

@JvmSynthetic
internal fun repaintWorld() { canvas.repaint() }

// The image an actor is drawn with: its own image or the shared placeholder.
// Used by the renderer, the click detection and Actor.bounds() so that display,
// clicking and collision all agree on the same size.
@JvmSynthetic
internal fun imageOrPlaceholder(actor: Actor): Image = actor.image ?: placeholderImage

/** true if a pending click hit exactly this actor (consumes it only then). */
@JvmSynthetic
internal fun isActorClicked(actor: Actor): Boolean {
    if (clickPending && clickActor === actor) {
        clickPending = false
        return true
    }
    return false
}

/** true if a pending click hit the world directly (not an actor); consumes it only then. */
@JvmSynthetic
internal fun isWorldClicked(): Boolean {
    if (clickPending && clickActor == null) {
        clickPending = false
        return true
    }
    return false
}

// =====================================================================
//  Internal implementation (private = only visible in this file)
// =====================================================================

// weak keys: actors that are no longer referenced anywhere disappear automatically
private val actorWorlds = WeakHashMap<Actor, World>()

// texts shown on top of each world (see World.showText); weak keys like actorWorlds
private val worldTexts = WeakHashMap<World, LinkedHashMap<Pair<Int, Int>, String>>()

private val textFont = Font("SansSerif", Font.BOLD, 16)

// loaded sound files (format + raw samples); a fresh Clip per play allows overlapping sounds
private val soundCache = ConcurrentHashMap<String, Pair<AudioFormat, ByteArray>>()

private fun loadSound(fileName: String): Pair<AudioFormat, ByteArray> {
    for (f in listOf(File(fileName), File("sounds", fileName))) {
        if (f.exists()) {
            AudioSystem.getAudioInputStream(f).use { input ->
                return Pair(input.format, input.readAllBytes())
            }
        }
    }
    throw IllegalArgumentException("Sound file not found: $fileName (expected e.g. in the folder 'sounds/')")
}

// decoded image files (loading from disk each time would be slow, e.g. when an
// actor sets its image in act()); every Image object still gets its own copy
private val imageCache = ConcurrentHashMap<String, BufferedImage>()

@JvmSynthetic
internal fun cachedImage(fileName: String): BufferedImage =
    imageCache.getOrPut(fileName) { loadImage(fileName) }

// Same lookup rule as loadSound: the given path (relative to the project
// folder, or absolute), then the default folder.
private fun loadImage(fileName: String): BufferedImage {
    for (f in listOf(File(fileName), File("images", fileName))) {
        if (f.exists()) ImageIO.read(f)?.let { return it }
    }
    throw IllegalArgumentException("Image file not found: $fileName (expected e.g. in the folder 'images/')")
}

// backing field for getSpeed()/setSpeed(); real functions instead of a top-level
// var so BlueJ shows getSpeed/setSpeed and not [auto-generated] accessors
private var speedValue: Int = 50

private val keysDown = ConcurrentHashMap.newKeySet<String>()
private var currentWorld: World? = null
@Volatile private var running = false
// click state: written on the Swing event thread, read on the simulation thread
@Volatile private var clickPending = false
@Volatile private var clickActor: Actor? = null
@Volatile private var mouseOverActor: Actor? = null

// generic placeholder image (created in code) when an actor has no image
private val placeholderImage: Image = run {
    val img = Image(30, 30)
    img.color = java.awt.Color(180, 180, 190); img.fill()
    img.color = java.awt.Color(90, 90, 100); img.drawRect(0, 0, 29, 29); img.drawString("?", 12, 20)
    img
}

// Draws an image honouring its transparency (stored in Image, not baked into the pixels).
private fun drawImage(g2: Graphics2D, img: Image, x: Int, y: Int) {
    if (img.transparency < 255) {
        val old = g2.composite
        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, img.transparency / 255f)
        g2.drawImage(img.awtImage, x, y, null)
        g2.composite = old
    } else {
        g2.drawImage(img.awtImage, x, y, null)
    }
}

// drawing surface as an anonymous JPanel (no separate class)
private val canvas = object : JPanel() {
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val w = currentWorld ?: return
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val cs = w.cellSize
        drawImage(g2, w.background, 0, 0)
        for (a in w.allObjects()) {
            val img = imageOrPlaceholder(a)
            val centerX = a.x * cs + cs / 2.0
            val centerY = a.y * cs + cs / 2.0
            val old = g2.transform
            g2.rotate(Math.toRadians(a.rotation.toDouble()), centerX, centerY)
            drawImage(g2, img, (centerX - img.width / 2.0).toInt(), (centerY - img.height / 2.0).toInt())
            g2.transform = old
        }
        // text layer (showText) on top of everything: white with a black outline,
        // left-aligned at the cell position, readable on any background
        g2.font = textFont
        val fm = g2.fontMetrics
        for ((tx, ty, text) in textsOf(w)) {
            val px = (tx * cs).toInt()
            val py = (ty * cs + cs / 2.0 + (fm.ascent - fm.descent) / 2.0).toInt()
            g2.color = Color.BLACK
            for (dx in -1..1) for (dy in -1..1) {
                if (dx != 0 || dy != 0) g2.drawString(text, px + dx, py + dy)
            }
            g2.color = Color.WHITE
            g2.drawString(text, px, py)
        }
    }
}

// control bar (Greenfoot-style). Buttons are non-focusable so they do not
// swallow the arrow keys; clicking Run gives the window focus for the keyboard.
// Button labels match the function names (step / start / stop). Reset has no
// matching function (it re-runs main()).
private val stepButton = JButton("Step").apply {
    isFocusable = false
    toolTipText = "step(): run a single step (one act for the world and every actor)"
    addActionListener { step() }
}
private val runButton = JButton("Start").apply {
    isFocusable = false
    preferredSize = preferredSize   // Breite an "Start" (laengeres Wort) fixieren
    toolTipText = "start(): run the game (calling act over and over)"
    addActionListener { toggleRun() }
}
private val resetButton = JButton("Reset").apply {
    isFocusable = false
    toolTipText = "Reload the start situation (runs main() again)"
    addActionListener { reset() }
}
private val speedSlider = JSlider(1, 100, speedValue).apply {
    isFocusable = false
    toolTipText = "Simulation speed"
    addChangeListener { setSpeed(value) }
}
private val controlBar = JPanel().apply {
    add(stepButton); add(runButton); add(resetButton)
    add(JLabel("  Speed:")); add(speedSlider)
}

private val frame = JFrame("BluePlay").apply {
    name = "BluePlayGameWindow"
    defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
    add(canvas, BorderLayout.CENTER)
    add(controlBar, BorderLayout.SOUTH)
    isResizable = false
}

// one-time initialization: input listeners + simulation thread
private val boot: Boolean = run {
    // Close a leftover game window from an earlier class-load in the same VM.
    // BlueJ reloads the project classes with a new class loader after recompiling
    // (without restarting the VM), so an old BluePlay window would otherwise stay
    // open and we would end up with two game windows.
    for (f in java.awt.Frame.getFrames()) {
        if (f !== frame && f.name == "BluePlayGameWindow") f.dispose()
    }
    java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
        .addKeyEventDispatcher { e ->
            when (e.id) {
                KeyEvent.KEY_PRESSED -> keysDown.add(keyName(e))
                KeyEvent.KEY_RELEASED -> keysDown.remove(keyName(e))
            }
            false
        }
    val m = object : MouseAdapter() {
        override fun mouseMoved(e: MouseEvent) { updateMouse(e) }
        override fun mouseDragged(e: MouseEvent) { updateMouse(e) }
        // register the click on press, not on mouseClicked: Swing only fires
        // mouseClicked if the mouse barely moves between press and release, which
        // makes clicks on moving targets feel unreliable (especially for kids)
        override fun mousePressed(e: MouseEvent) { updateMouse(e); clickPending = true; clickActor = mouseOverActor }
    }
    canvas.isFocusable = true
    canvas.addMouseListener(m)
    canvas.addMouseMotionListener(m)
    Thread(::simulationLoop, "game-sim").apply { isDaemon = true; start() }
    true
}

private fun currentDelayMs(): Long = (100 - speedValue).coerceAtLeast(1).toLong()

private fun simulationLoop() {
    while (true) {
        if (running) {
            oneStep()
            try { Thread.sleep(currentDelayMs()) } catch (_: InterruptedException) {}
        } else {
            try { Thread.sleep(20) } catch (_: InterruptedException) {}
        }
    }
}

private fun oneStep() {
    val w = currentWorld ?: return
    try {
        synchronized(simLock) {
            w.act()
            for (a in w.allObjects()) {
                if (worldOf(a) === w) a.act()
            }
        }
    } catch (e: Throwable) {
        // Throwable, not Exception: a StackOverflowError (e.g. from a recursive
        // act()) must not kill the simulation thread for good - print and stop.
        e.printStackTrace()
        stop()
    }
    canvas.repaint()
}

private fun updateMouse(e: MouseEvent) {
    mouseOverActor = actorAt(e.x, e.y)
}

private fun actorAt(px: Int, py: Int): Actor? {
    val w = currentWorld ?: return null
    val cs = w.cellSize
    for (a in w.allObjects().reversed()) {
        val img = imageOrPlaceholder(a)
        val x0 = a.x * cs + cs / 2 - img.width / 2
        val y0 = a.y * cs + cs / 2 - img.height / 2
        if (px in x0 until (x0 + img.width) && py in y0 until (y0 + img.height)) return a
    }
    return null
}

private fun keyName(e: KeyEvent): String = when (e.keyCode) {
    KeyEvent.VK_LEFT -> "left"
    KeyEvent.VK_RIGHT -> "right"
    KeyEvent.VK_UP -> "up"
    KeyEvent.VK_DOWN -> "down"
    KeyEvent.VK_SPACE -> "space"
    KeyEvent.VK_ENTER -> "enter"
    KeyEvent.VK_ESCAPE -> "escape"
    KeyEvent.VK_SHIFT -> "shift"
    KeyEvent.VK_CONTROL -> "control"
    KeyEvent.VK_TAB -> "tab"
    KeyEvent.VK_BACK_SPACE -> "backspace"
    else -> {
        val c = e.keyChar
        if (c != KeyEvent.CHAR_UNDEFINED && !c.isISOControl()) c.lowercaseChar().toString()
        else KeyEvent.getKeyText(e.keyCode).lowercase()
    }
}
