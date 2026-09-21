package com.alsaril.math.generator

import com.alsaril.math.Neg
import com.alsaril.math.Node
import com.alsaril.math.Op
import com.alsaril.math.Value
import com.alsaril.math.Var

internal class Counter(private var value: Int = 0) {
    fun inc() = value++
}

internal fun variables(ast: Node): Pair<Map<String, Int>, List<String>> {
    val vars = mutableSetOf<String>()
    val stack = ArrayDeque<Node>()
    stack.addLast(ast)

    while (stack.isNotEmpty()) {
        when (val node = stack.removeLast()) {
            is Value -> {}
            is Var -> vars.add(node.name)
            is Neg -> stack.addLast(node.arg)

            // the right side goes on first, so the left comes back off first
            is Op -> {
                stack.addLast(node.right)
                stack.addLast(node.left)
            }
        }
    }

    val list = vars.toList()
    return list.asSequence().mapIndexed { index, name -> name to index }.toMap() to list
}
