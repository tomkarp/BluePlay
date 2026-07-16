// BluePlay – a small game engine for BlueJ (Kotlin).

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * An Actor is an object that lives in a World and has an image.
 * Your own figures inherit from Actor and override act().
 *
 * The position (x, y) is given in cells and can be set directly; the values
 * are automatically clamped to the world boundaries.
 */
open class Actor {

    /** The world this actor lives in. Error if the actor has not been added to a world. */
    val world: World
        get() = worldOf(this) ?: throw IllegalStateException(
            "The actor is not in a world (add it with addObject first).")

    /** The appearance of the actor (null = a generic image is drawn). */
    var image: Image? = null
        set(value) { field = value; repaintWorld() }

    /** Column (x) in cells. Clamped to the world boundaries. */
    var x: Int = 0
        set(value) { field = worldOf(this)?.let { value.coerceIn(0, it.width - 1) } ?: value; repaintWorld() }

    /** Row (y) in cells. Clamped to the world boundaries. */
    var y: Int = 0
        set(value) { field = worldOf(this)?.let { value.coerceIn(0, it.height - 1) } ?: value; repaintWorld() }

    /** Heading in degrees (0 = right, clockwise). */
    var rotation: Int = 0
        set(value) { field = ((value % 360) + 360) % 360; repaintWorld() }

    /** Called on every simulation step. Meant to be overridden. */
    open fun act() {}

    /** Moves the actor by distance cells in its heading (rotation). */
    fun move(distance: Int) {
        val rad = Math.toRadians(rotation.toDouble())
        x += (cos(rad) * distance).roundToInt()
        y += (sin(rad) * distance).roundToInt()
    }

    /** Turns the heading by degrees. */
    fun turn(degrees: Int) { rotation += degrees }

    /** Turns the heading towards the cell (x, y), e.g. towards another actor's position. */
    fun turnTowards(x: Int, y: Int) {
        if (x == this.x && y == this.y) return
        rotation = Math.toDegrees(atan2((y - this.y).toDouble(), (x - this.x).toDouble())).roundToInt()
    }

    /** Distance to another actor in cells (center to center). */
    fun distanceTo(other: Actor): Int {
        val dx = (other.x - x).toDouble()
        val dy = (other.y - y).toDouble()
        return sqrt(dx * dx + dy * dy).roundToInt()
    }

    /** true if the actor is at the edge of the world. */
    val isAtEdge: Boolean
        get() = worldOf(this)?.let { x <= 0 || y <= 0 || x >= it.width - 1 || y >= it.height - 1 } ?: false

    /** true if this actor is being clicked right now. */
    val isClicked: Boolean
        get() = isActorClicked(this)

    /** true if the image rectangles of this and the other actor overlap. */
    fun intersects(other: Actor): Boolean {
        val a = bounds(); val b = other.bounds()
        return a[0] < b[0] + b[2] && a[0] + a[2] > b[0] &&
               a[1] < b[1] + b[3] && a[1] + a[3] > b[1]
    }

    private fun bounds(): IntArray {
        // same size the actor is drawn and clicked with (placeholder if no image)
        val cs = worldOf(this)?.cellSize ?: 1
        val img = imageOrPlaceholder(this)
        val cx = x * cs + cs / 2; val cy = y * cs + cs / 2
        return intArrayOf(cx - img.width / 2, cy - img.height / 2, img.width, img.height)
    }

    // ----- Collision / search via reified generics (call: getOneIntersecting<Wall>()) -----

    /** All intersecting actors of type T. */
    inline fun <reified T : Actor> getIntersecting(): List<T> {
        return world.allObjects().filter { it !== this && it is T && intersects(it) }.map { it as T }
    }

    /** One intersecting actor of type T (or null). */
    inline fun <reified T : Actor> getOneIntersecting(): T? = getIntersecting<T>().firstOrNull()

    /** true if at least one actor of type T intersects. */
    inline fun <reified T : Actor> isTouching(): Boolean = getIntersecting<T>().isNotEmpty()

    /** Removes one intersecting actor of type T from the world. */
    inline fun <reified T : Actor> removeTouching() {
        getOneIntersecting<T>()?.let { world.removeObject(it) }
    }
}
