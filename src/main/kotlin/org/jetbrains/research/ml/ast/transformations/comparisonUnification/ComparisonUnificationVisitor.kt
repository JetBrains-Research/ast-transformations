package org.jetbrains.research.ml.ast.transformations.comparisonUnification

import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyElementType
import com.jetbrains.python.psi.PyElementVisitor
import org.jetbrains.research.ml.ast.transformations.commands.Command
import org.jetbrains.research.ml.ast.transformations.commands.ICommandPerformer

internal class ComparisonUnificationVisitor(private val commandsPerformer: ICommandPerformer) :
    PyElementVisitor() {
    override fun visitPyBinaryExpression(node: PyBinaryExpression) {
        handleBinaryExpression(node)
        super.visitPyBinaryExpression(node)
    }

    private fun handleBinaryExpression(node: PyBinaryExpression) {
        if (node.isReplacable()) {
            node.swapExpressions()
        }
    }

    private fun PyBinaryExpression.isComparison(): Boolean = PyTokenTypes.COMPARISON_OPERATIONS.contains(operator)

    private fun PyBinaryExpression.isMultipleOperatorComparison(): Boolean {
        when (operator) {
            PyTokenTypes.AND_KEYWORD, PyTokenTypes.OR_KEYWORD -> return false
            else -> {
                val leftBinaryExpression = leftExpression as? PyBinaryExpression ?: return false
                return leftBinaryExpression.isComparison() && this.isComparison()
            }
        }
    }

    private fun PyBinaryExpression.isReplacable(): Boolean =
        comparisonTokenMap.containsKey(operator) && !isMultipleOperatorComparison()

    private fun PyBinaryExpression.swapExpressions() {
        val binOperator = comparisonTokenMap[operator] ?: return
        val left = leftExpression
        val right = rightExpression ?: return
        val generator = PyElementGenerator.getInstance(project)
        val newBinaryExpression = generator.createBinaryExpression(binOperator, right, left)
        // Todo: replace
        commandsPerformer.performCommand(Command({ replace(newBinaryExpression) }, { }, "Replace binary expression"))
    }

    companion object {
        private val comparisonTokenMap: Map<PyElementType, String> = mapOf(
            PyTokenTypes.LT to ">",
            PyTokenTypes.LE to ">="
        )
    }
}
