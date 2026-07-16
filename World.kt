// BluePlay – a small game engine for BlueJ (Kotlin).

import java.awt.Color

/**
 * The world that Actor objects live in - a grid of width x height cells,
 * each cell cellSize pixels in size.
 *
 * A world is not shown automatically; call show() to display it in the game window.
 */
open class World(val width: Int, val height: Int, val cellSize: Int) {

    private val actors = mutableListOf<Actor>()

    /**
     * Background image of the world. Drawn as-is (top-left corner, unscaled);
     * setBackground(fileName) scales the image to the world size.
     */
    var background: Image = Image(width * cellSize, height * cellSize).apply { color = Color.WHITE; fill() }
        set(value) { field = value; repaintWorld() }

    /** Shows this world in the game window. A world is not shown automatically. */
    fun show() {
        showWorld(this)
    }

    /** Called on every simulation step. Meant to be overridden. */
    open fun act() {}

    /** Adds an actor to the world at position (x, y). Removes it from its previous world, if any. */
    fun addObject(actor: Actor, x: Int, y: Int) {
        synchronized(simLock) {
            val oldWorld = worldOf(actor)
            if (oldWorld != null && oldWorld !== this) oldWorld.removeObject(actor)
            setWorldOf(actor, this)
            if (!actors.contains(actor)) actors.add(actor)
        }
        actor.x = x
        actor.y = y
        repaintWorld()
    }

    /** Removes an actor from the world. */
    fun removeObject(actor: Actor) {
        synchronized(simLock) { if (actors.remove(actor)) setWorldOf(actor, null) }
        repaintWorld()
    }

    /** All actor objects in the world (a copy). */
    fun allObjects(): List<Actor> = synchronized(simLock) { ArrayList(actors) }

    /** All actor objects of type T (call: getObjects<Ball>()). */
    inline fun <reified T : Actor> getObjects(): List<T> = allObjects().filterIsInstance<T>()

    /** All actor objects in cell (x, y). */
    fun getObjectsAt(x: Int, y: Int): List<Actor> = allObjects().filter { it.x == x && it.y == y }

    /** Number of actor objects currently in the world. */
    val numberOfObjects: Int get() = synchronized(simLock) { actors.size }

    /** Sets an image as background and scales it to the world size. */
    fun setBackground(fileName: String) {
        val img = Image(fileName)
        img.scale(width * cellSize, height * cellSize)
        background = img
    }

    /** Fills the background with the given color (r, g, b: 0..255 each). */
    fun setBackground(r: Int, g: Int, b: Int) {
        background = Image(width * cellSize, height * cellSize).apply { color = Color(r, g, b); fill() }
    }

    /** true if the world itself is being clicked right now (not one of its actors). */
    val isClicked: Boolean
        get() = isWorldClicked()

    /**
     * Shows text at cell (x, y), on top of everything else.
     * New text replaces text shown at the same position before; "" removes it.
     * Text shown at other positions stays visible - so showing text at a moving
     * position leaves the old text behind unless it is removed there with "".
     */
    fun showText(text: String, x: Int, y: Int) {
        setTextOf(this, x, y, text)
    }
}
