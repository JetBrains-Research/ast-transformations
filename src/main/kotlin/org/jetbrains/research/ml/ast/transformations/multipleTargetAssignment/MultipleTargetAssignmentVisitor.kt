package org.jetbrains.research.ml.ast.transformations.multipleTargetAssignment

import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyElementVisitor
import org.jetbrains.research.ml.ast.transformations.PyUtils
import org.jetbrains.research.ml.ast.transformations.commands.Command
import org.jetbrains.research.ml.ast.transformations.commands.DeleteCommand
import org.jetbrains.research.ml.ast.transformations.commands.ICommandPerformer
import org.jetbrains.research.ml.ast.util.runInWCA

class MultipleTargetAssignmentVisitor(private val commandsPerformer: ICommandPerformer) : PyElementVisitor() {
    override fun visitPyAssignmentStatement(node: PyAssignmentStatement) {
        processAssignment(node)
        super.visitPyAssignmentStatement(node)
    }

    private fun processAssignment(node: PyAssignmentStatement) {
        val targets = node.targets.toList()
        val assignedValue = node.assignedValue ?: return
        val firstTarget = targets.first()
        // Todo: addBefore
        commandsPerformer.performCommand(
            Command(
                runInWCA(node.project){ node.parent.addBefore(PyUtils.createAssignment(firstTarget, assignedValue), node) },
                { },
                "Add first assignment from multiple target assignment"
            )
        )
        for ((value, target) in targets.windowed(2)) {
            // Todo: addBefore
            commandsPerformer.performCommand(
                Command(
                    runInWCA(node.project){ node.parent.addBefore(PyUtils.createAssignment(target, value), node) },
                    { },
                    "Add the next assignment from multiple target assignment"
                )
            )
        }
        commandsPerformer.performCommand(DeleteCommand(node).getCommand("Delete multiple target assignment node"))
    }
}
