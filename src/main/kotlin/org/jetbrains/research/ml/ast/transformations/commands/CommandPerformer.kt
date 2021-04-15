package org.jetbrains.research.ml.ast.transformations.commands

import com.intellij.psi.PsiElement
import java.util.logging.Logger


interface ICommandPerformer {
    val psiTree: PsiElement
    fun <T>performCommand(command: Command<T>): T
    fun undoPerformedCommands(n: Int)
    fun undoAllPerformedCommands()
}


class CommandPerformer(override val psiTree: PsiElement, private val toUndoCommands: Boolean) : ICommandPerformer {
    private val logger = Logger.getLogger(javaClass.name)
    private val commands = ArrayDeque<Command<*>>()

    //    Should be run in WriteCommandAction
//    Todo: it's better to wrap command.redo and command.undo, see CommentsRemovalVisitor
    override fun <T>performCommand(command: Command<T>): T {
        val result = command.redo.call()
        if (toUndoCommands) {
            commands.addLast(command)
        }
        return result
    }

    override fun undoPerformedCommands(n: Int) {
        require(n <= commands.size) { "Cannot undo $n commands, only ${commands.size} commands are stored" }
        repeat(n) {
            val commandToUndo = commands.removeLastOrNull() ?: error("No more commands stored for being undone")
            commandToUndo.undo.call()
        }
    }

    override fun undoAllPerformedCommands() {
        undoPerformedCommands(commands.size)
    }
}

