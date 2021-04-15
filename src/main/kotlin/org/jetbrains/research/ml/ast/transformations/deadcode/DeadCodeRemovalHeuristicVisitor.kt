/*
 * Copyright (c) 2020 Birillo A., Bobrov A., Lyulina E.
 */

package org.jetbrains.research.ml.ast.transformations.deadcode

import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyIfStatement
import com.jetbrains.python.psi.PyWhileStatement
import com.jetbrains.python.psi.impl.PyEvaluator
import org.jetbrains.research.ml.ast.transformations.PyUtils
import org.jetbrains.research.ml.ast.transformations.commands.Command
import org.jetbrains.research.ml.ast.transformations.commands.DeleteCommand
import org.jetbrains.research.ml.ast.transformations.commands.ICommandPerformer
import org.jetbrains.research.ml.ast.util.runInWCA

internal class DeadCodeRemovalHeuristicVisitor(private val commandPerformer: ICommandPerformer) :
    PyElementVisitor() {
    override fun visitPyIfStatement(node: PyIfStatement) {
        handleIfFalseStatement(node)
        super.visitPyIfStatement(node)
    }

    override fun visitPyWhileStatement(node: PyWhileStatement) {
        handleWhileFalseStatement(node)
        super.visitPyWhileStatement(node)
    }

    /**
     * Handles `if False:` case
     */
    private fun handleIfFalseStatement(node: PyIfStatement) {
        while (node.ifPart.condition?.evaluateBoolean() == false) {
            val firstElsePart = node.elifParts.firstOrNull()
            if (firstElsePart != null) {
                val newIfPart = PyUtils.createPyIfElsePart(firstElsePart)
                // Todo: replace
                commandPerformer.performCommand(
                    Command(
                    runInWCA(node.project){ node.ifPart.replace(newIfPart) },
                        { },
                    "Replace false condition from \"if\"-node with condition from first \"elif\"-node"
                    )
                )
                commandPerformer.performCommand(DeleteCommand(firstElsePart).getCommand("Delete first \"elif\"-node"))
//                commandPerformer.performCommand(Command(runInWCA(node.project){ firstElsePart.delete() }, { }, "Delete first \"elif\"-node"))
            } else {
                commandPerformer.performCommand(DeleteCommand(node).getCommand("Delete \"if\"-node with false condition"))
//                commandPerformer.performCommand(Command(runInWCA(node.project){ node.delete() }, {}, "Delete \"if\"-node with false condition"))
                break
            }
        }

        for (ifElsePart in node.elifParts) {
            if (ifElsePart.condition?.evaluateBoolean() == false) {
                commandPerformer.performCommand(DeleteCommand(ifElsePart).getCommand("Delete \"else\"-node with false condition"))
//                commandPerformer.performCommand(Command(runInWCA(node.project){ ifElsePart.delete() }, { },"Delete \"else\"-node with false condition"))
            }
        }
    }

    /**
     * Handles `while False:` case
     */
    private fun handleWhileFalseStatement(node: PyWhileStatement) {
        if (node.whilePart.condition?.evaluateBoolean() == false) {
            commandPerformer.performCommand(DeleteCommand(node).getCommand("Delete \"while\"-node with false condition"))
//            commandPerformer.performCommand(Command(runInWCA(node.project){ node.delete() }, { }, ))
        }
    }
}

private fun PyExpression.evaluateBoolean(): Boolean? = PyEvaluator.evaluateAsBoolean(this)


