import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator

fun Navigator.toMyNavigator(): WindowCapableNavigator<Screen> {
    val voy = this
    return object: WindowCapableNavigator<Screen> {
        override fun goBack() {
            voy.pop()
        }

        override fun goNewWindow(screen: Screen) {
            voy.push(screen)
        }

        override fun goForward(screen: Screen) {
            voy.push(screen)
        }

    }
}