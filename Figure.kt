/**
 * A simple figure that slowly walks to the right.
 */
class Figure : Actor() {

    init {
        image = Image("figure.png")
    }

    override fun act() {
        move(1)
    }
}
