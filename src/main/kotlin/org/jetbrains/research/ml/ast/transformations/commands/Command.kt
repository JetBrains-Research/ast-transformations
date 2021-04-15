package org.jetbrains.research.ml.ast.transformations.commands

import com.intellij.psi.PsiElement
import java.util.concurrent.Callable


class Command<T>(val redo: Callable<T>, val undo: Callable<*>, val description: String)

// maybe there is a better way
abstract class CommandProvider<T> {
    protected abstract fun redo(): Callable<T>
    protected abstract fun undo(): Callable<*>

    fun getCommand(description: String) = Command(redo(), undo(), description)
}

/* Todo: add commands for:
- delete
- add
- replace
- rename
- addBefore
- addRangeBefore
- addRangeAfter
- deleteChildRange

 */


