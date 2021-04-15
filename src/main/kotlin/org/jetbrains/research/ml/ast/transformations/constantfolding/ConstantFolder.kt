package org.jetbrains.research.ml.ast.transformations.constantfolding

import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFile
import org.jetbrains.research.ml.ast.transformations.*
import org.jetbrains.research.ml.ast.transformations.commands.Command
import org.jetbrains.research.ml.ast.transformations.commands.ICommandPerformer

class ConstantFolder(
    private val commandsPerformer: ICommandPerformer,
    private val generator: PyElementGenerator,
    file: PyFile
) {
    private val evaluator = PyEvaluatorImproved(file)

    fun simplifyAllSubexpressionsDelayed(element: PsiElement): () -> PsiElement {
        if (element is PyExpression) {
            simplifyByEvaluation(element)?.let { return it }
        }

        // Otherwise, simply fold all constants in subtrees
        val simplifyChildren = element.children.map { simplifyAllSubexpressionsDelayed(it) }
        return {
            simplifyChildren.forEach { it() }
            element
        }
    }

    private fun simplifyByEvaluation(expression: PyExpression): (() -> PsiElement)? =
        when (val result = evaluator.evaluate(expression)) {
            is PyEvaluatorImproved.PyIntLike -> { ->
                // Todo: replace
                commandsPerformer.performCommand(
                    Command<PsiElement>(
                        { expression.replace(generator.createIntOrBoolExpression(result)) },
                        { },
                        "Evaluate integer-like constant"
                    )
                )
            }
            is PyEvaluatorImproved.PyString ->
                { ->
                    // Todo: replace
                    commandsPerformer.performCommand(
                        Command<PsiElement>(
                            { expression.replace(generator.createStringLiteralFromString(result.string)) },
                            { },
                            "Evaluate string"
                        )
                    )
                }
            is PyEvaluatorImproved.PySequence -> run {
                val emptyLiteral = when (result.kind ?: return@run null) {
                    PyEvaluatorImproved.PySequence.PySequenceKind.LIST -> generator.createListLiteral()
                    PyEvaluatorImproved.PySequence.PySequenceKind.TUPLE ->
                        generator.createExpressionFromText(LanguageLevel.getDefault(), "()")
                }
                val simplifyElements = result.elements.map { simplifyAllSubexpressionsDelayed(it) }
                return@run {
                    // Todo: replace???
                    commandsPerformer.performCommand(
                        Command<PsiElement>(
                            {
                                val newList = expression.replace(emptyLiteral) as PyExpression
                                var anchor: PyExpression? = null
                                for (simplifyElement in simplifyElements) {
                                    anchor = generator.insertItemIntoListRemoveRedundantCommas(
                                        newList,
                                        anchor,
                                        simplifyElement() as PyExpression
                                    ) as PyExpression
                                }
                                newList
                            },
                            { },
                            "Evaluate sequence"
                        )
                    )
                }
            }
            is PyEvaluatorImproved.PyExpressionResult -> run {
                val simplifyNewExpression = simplifyAllSubexpressionsDelayed(result.expression)
                return@run {
                    // Todo: replace
                    commandsPerformer.performCommand(
                        Command<PsiElement>(
                            { expression.replace(simplifyNewExpression()) },
                            { },
                            "Evaluate to subexpression"
                        )
                    )
                }
            }
            is PyEvaluatorImproved.PyOperandSequence -> run {
                val simplifyUnevaluated =
                    result.unevaluatedAtoms.map { simplifyAllSubexpressionsDelayed(it.expression) }
                return@run {
                    commandsPerformer.performCommand(
                        // Todo: replace???
                        Command<PsiElement>(
                            {
                                val valueOperand =
                                    listOfNotNull(
                                        result.evaluatedValue?.let { generator.createIntOrBoolExpression(it) }
                                    )
                                val newExpression = PyUtils.braceExpression(
                                    generator.createBinaryOperandList(
                                        result.operator,
                                        simplifyUnevaluated
                                            .map { it() as PyExpression }
                                            .mapIndexed { i, expr ->
                                                if (result.unevaluatedAtoms[i].negate) {
                                                    generator.createPrefixExpression(
                                                        "-",
                                                        PyUtils.braceExpression(expr)
                                                    )
                                                } else {
                                                    expr
                                                }
                                            } + valueOperand

                                    )
                                )
                                expression.replace(newExpression)
                            },
                            { },
                            "Evaluate commutative operator partially"
                        )
                    )
                }
            }
            else -> null
        }
}
