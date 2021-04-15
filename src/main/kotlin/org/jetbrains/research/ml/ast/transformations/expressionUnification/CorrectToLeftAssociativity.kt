package org.jetbrains.research.ml.ast.transformations.expressionUnification

import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyRecursiveElementVisitor
import org.jetbrains.research.ml.ast.transformations.commands.Command
import org.jetbrains.research.ml.ast.transformations.commands.ICommandPerformer
import org.jetbrains.research.ml.ast.util.fold1

internal class CorrectToLeftAssociativity(private val commandPerformer: ICommandPerformer) :
    PyRecursiveElementVisitor() {
    override fun visitPyBinaryExpression(node: PyBinaryExpression) {
        if (node.isCorrectionNeeded()) {
            val expressions = collectAllRightAssociativityExpression(node)
            if (expressions.isNotEmpty()) {
                val operator = ExpressionUnificationVisitor.commutativeTokenMap[node.operator] ?: return
                val generator = PyElementGenerator.getInstance(node.project)
                val newNode = expressions.fold1 { acc, element ->
                    generator.createBinaryExpression(operator, acc, element)
                }
                // Todo: replace
                commandPerformer.performCommand(Command({ node.replace(newNode) }, { },"Replace node with left associativity"))
            }
        }
        super.visitPyBinaryExpression(node)
    }

    private fun PyBinaryExpression.isCorrectionNeeded(): Boolean {
        val rightExpr = rightExpression
        return leftExpression !is PyBinaryExpression &&
            rightExpr is PyBinaryExpression &&
            operator == rightExpr.operator
    }

    private fun PyBinaryExpression.hasWrongAssociativity(): Boolean {
        val leftExpression = this.leftExpression
        val rightExpression = this.rightExpression
        val isLeftAssociativity = ExpressionUnificationVisitor.commutativeTokenMap.containsKey(operator)
        return leftExpression !is PyBinaryExpression &&
            rightExpression is PyBinaryExpression &&
            isLeftAssociativity &&
            operator == rightExpression.operator
    }

    private fun collectAllRightAssociativityExpression(node: PyBinaryExpression): List<PyExpression> {
        var current = node
        val list = mutableListOf<PyExpression>()
        fun isWrongAssociativity() = current.hasWrongAssociativity()
        while (isWrongAssociativity()) {
            list.add(current.leftExpression)
            current = current.rightExpression as PyBinaryExpression
        }

        if (list.isNotEmpty()) {
            list.addAll(listOfNotNull(current.leftExpression, current.rightExpression))
        }
        return list
    }
}
