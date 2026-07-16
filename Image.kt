// BluePlay – a small game engine for BlueJ (Kotlin).

import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * An image that can be drawn on and that serves as the appearance of an Actor
 * or the background of a World. Internally backed by a BufferedImage.
 */
class Image {

    // Backing AWT image. Hidden from the BlueJ menus (like 'color') so the
    // java.awt type does not leak into the student API.
    @get:JvmSynthetic
    var awtImage: BufferedImage
        private set

    // Current drawing color. Set it with setColor(...). The accessors are @JvmSynthetic
    // so the java.awt.Color type does not leak into the BlueJ menus; internal framework
    // code (World, BluePlay) still uses this property from Kotlin.
    @get:JvmSynthetic @set:JvmSynthetic
    var color: Color = Color.BLACK

    // Opacity 0..255, applied wherever the image is drawn. Stored (not baked into
    // the pixels) so setTransparency is absolute and repeatable - calling it every
    // step in act() must not fade the image out, and full opacity can be restored.
    @get:JvmSynthetic @set:JvmSynthetic
    internal var transparency: Int = 255

    /** Sets the drawing color from red, green, blue components (0..255 each). */
    fun setColor(r: Int, g: Int, b: Int) { color = Color(r, g, b) }

    /** Empty, transparent image of the given size (in pixels). */
    constructor(width: Int, height: Int) {
        awtImage = BufferedImage(maxOf(1, width), maxOf(1, height), BufferedImage.TYPE_INT_ARGB)
    }

    /** Loads an image file (e.g. PNG); looked up in the folder "images/" or under the given path. */
    constructor(fileName: String) {
        awtImage = copyOf(cachedImage(fileName))
    }

    /** Copy of an existing image. */
    constructor(other: Image) {
        awtImage = copyOf(other.awtImage)
        transparency = other.transparency
    }

    private fun copyOf(source: BufferedImage): BufferedImage {
        val copy = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
        val g = copy.createGraphics(); g.drawImage(source, 0, 0, null); g.dispose()
        return copy
    }

    val width: Int get() = awtImage.width
    val height: Int get() = awtImage.height

    private fun graphics(): Graphics2D {
        val g = awtImage.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = color
        return g
    }

    fun fill() = fillRect(0, 0, width, height)
    fun fillRect(x: Int, y: Int, w: Int, h: Int) { val g = graphics(); g.fillRect(x, y, w, h); g.dispose() }
    fun drawRect(x: Int, y: Int, w: Int, h: Int) { val g = graphics(); g.drawRect(x, y, w, h); g.dispose() }
    fun fillOval(x: Int, y: Int, w: Int, h: Int) { val g = graphics(); g.fillOval(x, y, w, h); g.dispose() }
    fun drawOval(x: Int, y: Int, w: Int, h: Int) { val g = graphics(); g.drawOval(x, y, w, h); g.dispose() }
    fun drawLine(x1: Int, y1: Int, x2: Int, y2: Int) { val g = graphics(); g.drawLine(x1, y1, x2, y2); g.dispose() }
    fun drawString(text: String, x: Int, y: Int) { val g = graphics(); g.drawString(text, x, y); g.dispose() }
    fun drawImage(image: Image, x: Int, y: Int) {
        val g = graphics()
        if (image.transparency < 255) {
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, image.transparency / 255f)
        }
        g.drawImage(image.awtImage, x, y, null)
        g.dispose()
    }

    /** Makes the image fully transparent. */
    fun clear() {
        val g = awtImage.createGraphics()
        g.composite = AlphaComposite.Clear
        g.fillRect(0, 0, width, height)
        g.dispose()
    }

    /** Scales the image to a new size. */
    fun scale(width: Int, height: Int) {
        val w = maxOf(1, width); val h = maxOf(1, height)
        val scaled = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = scaled.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(awtImage, 0, 0, w, h, null)
        g.dispose()
        awtImage = scaled
    }

    /** Opacity from 0 (invisible) to 255 (opaque); applied whenever the image is drawn. */
    fun setTransparency(value: Int) {
        transparency = value.coerceIn(0, 255)
    }
}
