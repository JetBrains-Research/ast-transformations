package org.jetbrains.research.ml.ast.transformations.augmentedAssignment

import com.jetbrains.python.psi.PyAugAssignmentStatement
import com.jetbrains.python.psi.PyElementVisitor
import org.jetbrains.research.ml.ast.transformations.*
import org.jetbrains.research.ml.ast.transformations.commands.Command
import org.jetbrains.research.ml.ast.transformations.commands.ICommandPerformer

internal class AugmentedAssignmentVisitor(private val commandsPerformer: ICommandPerformer) : PyElementVisitor() {
    override fun visitPyAugAssignmentStatement(node: PyAugAssignmentStatement) {
        handleAugAssignment(node)
        super.visitPyAugAssignmentStatement(node)
    }

    private fun handleAugAssignment(node: PyAugAssignmentStatement) {
        val newAssignment = PyUtils.createAssignment(node)
//      Todo: replace
        commandsPerformer.performCommand(
            Command({ node.replace(newAssignment) }, { }, "Replace AugAssignment")
        )
    }
}
