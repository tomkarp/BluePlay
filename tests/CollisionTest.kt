import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

private fun image(width: Int, height: Int, vararg pixels: Triple<Int, Int, Int>): Image {
    val image = Image(width, height)
    image.setColor(255, 255, 255)
    for ((x, y, alpha) in pixels) {
        check(alpha == 255) { "Test images use global transparency for partial alpha." }
        image.fillRect(x, y, 1, 1)
    }
    return image
}

private fun image(source: BufferedImage): Image {
    val image = Image(source.width, source.height)
    val awtImageField = Image::class.java.getDeclaredField("awtImage")
    awtImageField.isAccessible = true
    awtImageField.set(image, source)
    return image
}

private fun assertOverlap(
    expected: Boolean,
    firstImage: Image,
    firstCenterX: Double,
    firstCenterY: Double,
    firstRotation: Int,
    secondImage: Image,
    secondCenterX: Double,
    secondCenterY: Double,
    secondRotation: Int,
    description: String,
    firstTransparency: Int = 255,
    secondTransparency: Int = 255
) {
    try {
        firstImage.setTransparency(firstTransparency)
        secondImage.setTransparency(secondTransparency)
        val overlapMethod = Image::class.java.declaredMethods.single {
            it.name.startsWith("overlaps\$")
        }
        val actual = overlapMethod.invoke(
            firstImage,
            firstCenterX,
            firstCenterY,
            firstRotation,
            secondImage,
            secondCenterX,
            secondCenterY,
            secondRotation
        ) as Boolean
        check(actual == expected) {
            "$description: expected $expected, got $actual"
        }
    } finally {
        firstImage.setTransparency(255)
        secondImage.setTransparency(255)
    }
}

fun main(args: Array<String>) {
    val opaquePixel = image(1, 1, Triple(0, 0, 255))
    val transparentPixel = image(1, 1)

    assertOverlap(
        true,
        opaquePixel, 10.5, 10.5, 0,
        opaquePixel, 10.5, 10.5, 0,
        "opaque pixels at the same position overlap"
    )
    assertOverlap(
        false,
        transparentPixel, 10.5, 10.5, 0,
        opaquePixel, 10.5, 10.5, 0,
        "transparent pixels do not collide"
    )

    val oppositeCornersA = image(8, 8, Triple(0, 0, 255))
    val oppositeCornersB = image(8, 8, Triple(7, 7, 255))
    assertOverlap(
        false,
        oppositeCornersA, 20.0, 20.0, 0,
        oppositeCornersB, 20.0, 20.0, 0,
        "overlapping image rectangles without overlapping visible pixels do not collide"
    )

    val horizontal = image(3, 1, Triple(2, 0, 255))
    assertOverlap(
        true,
        horizontal, 10.5, 10.5, 90,
        opaquePixel, 10.5, 11.5, 0,
        "rotation is included in collision detection"
    )
    assertOverlap(
        false,
        horizontal, 10.5, 10.5, 90,
        opaquePixel, 11.5, 10.5, 0,
        "the unrotated pixel position is not used after rotation"
    )

    val nearlyTransparentPixel = image(1, 1, Triple(0, 0, 255))
    val visiblePixel = image(1, 1, Triple(0, 0, 255))
    assertOverlap(
        false,
        nearlyTransparentPixel, 10.5, 10.5, 0,
        opaquePixel, 10.5, 10.5, 0,
        "nearly transparent pixels are ignored",
        firstTransparency = 16
    )
    assertOverlap(
        true,
        visiblePixel, 10.5, 10.5, 0,
        opaquePixel, 10.5, 10.5, 0,
        "pixels above the alpha threshold collide",
        firstTransparency = 17
    )
    assertOverlap(
        false,
        opaquePixel, 10.5, 10.5, 0,
        image(1, 1, Triple(0, 0, 255)), 10.5, 10.5, 0,
        "an invisible image does not collide",
        firstTransparency = 0
    )

    assertOverlap(
        true,
        opaquePixel, 25.0, 35.0, 0,
        opaquePixel, 25.0, 35.0, 0,
        "integer world centres are handled like pixel positions"
    )

    val imageDirectory = File(args.single(), "images")
    val cat = image(ImageIO.read(File(imageDirectory, "cat.png")))
    val dog = image(ImageIO.read(File(imageDirectory, "dog.png")))
    assertOverlap(
        true,
        cat, 100.0, 100.0, 0,
        dog, 100.0, 100.0, 0,
        "the real cat and dog images collide at the same position"
    )
    assertOverlap(
        false,
        cat, 100.0, 100.0, 0,
        dog, 150.0, 100.0, 0,
        "transparent padding in the real cat and dog images does not collide"
    )

    println("Collision tests passed")
}
