/**
 * A minimal world. It places a figure when it is created.
 */
class MyWorld : World(600, 400, 1) {

    init {
        addObject(Figure(), 100, 200)
    }
}
