package org.jetbrains.research.ml.ast.transformations.inputDescriptionElimination

import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyReferenceExpression
import org.jetbrains.research.ml.ast.transformations.PerformedCommandStorage
import org.jetbrains.research.ml.ast.transformations.safePerformCommand

internal class InputDescriptionEliminationVisitor(private val commandsStorage: PerformedCommandStorage?) :
    PyElementVisitor() {
    override fun visitPyCallExpression(node: PyCallExpression) {
        if (node.isInputCall() && node.argumentList?.arguments?.isNotEmpty() == true) {
            commandsStorage.safePerformCommand(
                {
                    node.argumentList?.arguments?.forEach { it.delete() }
                },
                "Delete all arguments from \"input\" call"
            )
        }
    }
}

private fun PyCallExpression.isInputCall(): Boolean {
    val callee = callee
    if (callee != null && callee is PyReferenceExpression) {
        return callee.text == "input"
    }
    return false
}
