// "Change 'pairs' to '*pairs'" "true"
// WITH_RUNTIME

fun myMapOf(vararg pairs: Pair<String,String>) {
    val myMap = mapOf(<caret>pairs)
}