package org.jetbrains.research.ml.ast.transformations.ifRedundantLinesRemoval

import com.intellij.psi.PsiElement
import com.intellij.psi.util.siblings
import com.jetbrains.python.psi.*
import org.jetbrains.research.ml.ast.transformations.*
import org.jetbrains.research.ml.ast.transformations.commands.Command
import org.jetbrains.research.ml.ast.transformations.commands.DeleteCommand
import org.jetbrains.research.ml.ast.transformations.commands.ICommandPerformer
import org.jetbrains.research.ml.ast.transformations.constantfolding.PyEvaluatorImproved
import org.jetbrains.research.ml.ast.util.runInWCA
import kotlin.test.fail

class IfRedundantLinesRemover(
    private val commandPerformer: ICommandPerformer,
    private val generator: PyElementGenerator,
    file: PyFile
) {
    private val evaluator = PyEvaluatorImproved(file)
    private val simplifier = StatementSimplifier()
    private val remover = StatementRemover()

    companion object {
        fun PyStatement.nextStatements() = siblings(forward = true, withSelf = true).filterIsInstance<PyStatement>()
    }

    private data class StatementRange(val first: PyStatement, val last: PyStatement) {
        fun move(
            anchor: PsiElement,
            doBefore: Boolean,
            commandPerformer: ICommandPerformer
        ): StatementRange {
            val indexOfLast = first.nextStatements().indexOf(last)
            val anchorParent = anchor.parent
            // Todo: addRangeBefore, addRangeAfter
            val newFirst = commandPerformer.performCommand(
                Command(
                    runInWCA(anchor.project){
                        if (doBefore) {
                            anchorParent.addRangeBefore(first, last, anchor)
                        } else {
                            anchorParent.addRangeAfter(first, last, anchor)
                        } as PyStatement
                    },
                    { },
                    "Insert duplicate statements to new position"
                )
            )
            val newLast = newFirst.nextStatements().elementAt(indexOfLast)
            // Todo: deleteChildRange
            commandPerformer.performCommand(
                Command(
                    runInWCA(first.project){ first.parent.deleteChildRange(first, last) },
                    { },
                    "Remove original duplicate statements"
                )
            )
            return StatementRange(newFirst, newLast)
        }
    }

    fun simplifyAllDelayed(element: PsiElement): () -> Unit = simplifier.simplifyAllDelayed(element)

    private inner class StatementRemover {
        fun removeDuplicates(statementLists: List<List<PyStatement>>, prefixLength: Int, suffixLength: Int) {
            for (statementList in statementLists) {
                if (prefixLength > 0) {
                    // Todo: deleteChildRange
                    commandPerformer.performCommand(
                        Command(
                            runInWCA(statementList.first().project){
                                statementList.first().parent.deleteChildRange(
                                    statementList.first(),
                                    statementList[prefixLength - 1]
                                )
                            },
                            { },
                            "Remove duplicate statements"
                        )
                    )
                }
                if (suffixLength > 0) {
                    // Todo: deleteChildRange
                    commandPerformer.performCommand(
                        Command(
                            runInWCA(statementList.first().project){
                                statementList.last().parent.deleteChildRange(
                                    statementList[statementList.size - suffixLength],
                                    statementList.last()
                                )
                            },
                            { },
                            "Remove duplicate statements"
                        )
                    )
                }
            }
        }

        fun removeIfParts(
            partsToRemoveIds: List<Int>,
            conditions: List<PyExpression>,
            statementParts: List<PyStatementPart>
        ) {
            for (index in partsToRemoveIds) {
                if (conditions.getOrNull(index)?.let { evaluator.canBeProvenPure(it) } != false) {
                    commandPerformer.performCommand(DeleteCommand(statementParts[index]).getCommand("Remove redundant part of if statement"))
                } else {
                    // Todo: add
                    commandPerformer.performCommand(
                        Command(
                            runInWCA(statementParts[index].project){
                                statementParts[index].add(generator.createPassStatement())
                            },
                            { },
                            "Replace statements with a single 'pass' in part of if with impure condition"
                        )
                    )
                }
            }
        }

        fun removeIfStatement(
            ifStatement: PyIfStatement,
            conditions: List<PyExpression>,
            firstStatement: PyStatement,
            allConditionsArePure: Boolean
        ): PyStatement {
            var newFirst = firstStatement
            // All statements have been selected as parts of a suffix
            if (allConditionsArePure) {
                newFirst = newFirst.nextStatements().first()
                commandPerformer.performCommand(DeleteCommand(ifStatement).getCommand("Delete redundant 'if' statement"))
            } else {
                val disjunction = generator.createBinaryOperandList("or", conditions)
                val disjunctionStatement = generator.createExpressionStatement(disjunction)
                // Todo: replace
                newFirst = commandPerformer.performCommand(
                    Command(
                        runInWCA(ifStatement.project){ ifStatement.replace(disjunctionStatement) },
                        { },
                        "Replace 'if' with just the conditions"
                    )
                ) as PyStatement
            }
            return newFirst
        }
    }

    private inner class StatementSimplifier {
        fun simplifyAllDelayed(element: PsiElement): () -> Unit {
            val simplify = (element as? PyStatement)?.let { simplifyStatementDelayed(it) }
                ?: simplifySubStatementsDelayed(element)
            return { simplify() }
        }

        private fun simplifySubStatementsDelayed(element: PsiElement): () -> Unit {
            val simplifySubStatements = element.children.map { simplifyAllDelayed(it) }
            return { simplifySubStatements.forEach { it() } }
        }

        private fun simplifyStatementDelayed(statement: PyStatement): () -> StatementRange {
            val ifStatement = statement as? PyIfStatement
            if (ifStatement != null && ifStatement.hasAllConditions) {
                val ifParts = listOf(ifStatement.ifPart) + ifStatement.elifParts
                require(ifStatement.elsePart != null) { "The elsePart of the ifStatement can not be null" }
                val statementParts = ifParts + ifStatement.elsePart!!
                val statementLists = statementParts.map { it.statementList.statements.toList() }

                // Finding a common suffix is always preferred
                val suffixLength = getCommonSuffixLength(statementLists)
                val maxAllowedPrefixLength = statementLists.map { it.size - suffixLength }.minOrNull() ?: 0
                val conditions = ifParts.mapNotNull { it.condition }
                val allConditionsArePure = conditions.all { evaluator.canBeProvenPure(it) }
                val prefixLength = getPrefixLength(statementLists, allConditionsArePure, maxAllowedPrefixLength)

                // Handle all sub-statements excluding duplicates
                val ifStatements = statementLists.first()
                val simplifyPrefix = simplifyStatementListDelayed(ifStatements.take(prefixLength), prefixLength)
                val simplifySuffix = simplifyStatementListDelayed(ifStatements.takeLast(suffixLength), suffixLength)

                val uniqueStatementLists = statementLists.map { it.drop(prefixLength).dropLast(suffixLength) }
                val simplifyUniqueStatements = uniqueStatementLists.map {
                    if (it.isNotEmpty()) simplifyStatementListDelayed(it) else null
                }
                val (partsToKeepIds, partsToRemoveIds) = statementLists.indices
                    .partition { prefixLength + suffixLength < statementLists[it].size }

                return {
                    // Handle common prefix and suffix
                    var newFirst = moveCommonPart(ifStatement, simplifyPrefix, StatementRange::first, true)
                    val newLast = moveCommonPart(ifStatement, simplifySuffix, StatementRange::last, false)

                    // Remove the duplicate parts from all if/else parts
                    remover.removeDuplicates(statementLists.drop(1), prefixLength, suffixLength)

                    // Remove the whole if when necessary
                    // and make sure the first remaining part is always "if ...:"
                    if (partsToKeepIds.isEmpty()) {
                        newFirst = remover.removeIfStatement(ifStatement, conditions, newFirst, allConditionsArePure)
                    } else {
                        val firstToKeepId = partsToKeepIds.first()
                        val firstToKeep = statementParts[firstToKeepId]
                        val simplifiedFirstRange = simplifyUniqueStatements.map { it?.let { it() } }[firstToKeepId]
                            ?: error("The simplifyUniqueStatements has null on the position $firstToKeepId")
                        restoreIfCorrectness(conditions, firstToKeep, simplifiedFirstRange)
                    }

                    // Remove whole parts when necessary
                    remover.removeIfParts(partsToRemoveIds, conditions, statementParts)
                    StatementRange(newFirst, newLast)
                }
            }

            val simplifySubStatements = simplifySubStatementsDelayed(statement)
            return {
                simplifySubStatements()
                StatementRange(statement, statement)
            }
        }

        private fun simplifyStatementListDelayed(
            statements: List<PyStatement>,
            prefixOrSuffixLength: Int
        ): (() -> StatementRange)? {
            return if (prefixOrSuffixLength > 0) {
                simplifyStatementListDelayed(statements)
            } else null
        }

        private fun simplifyStatementListDelayed(statements: List<PyStatement>): () -> StatementRange {
            val simplifyStatements = statements.map { simplifyStatementDelayed(it) }
            return {
                val simplifiedRanges = simplifyStatements.map { it() }
                StatementRange(simplifiedRanges.first().first, simplifiedRanges.last().last)
            }
        }

        private fun moveCommonPart(
            ifStatement: PyIfStatement,
            simplifyDelayed: (() -> StatementRange)?,
            statement: StatementRange.() -> PyStatement,
            doBefore: Boolean
        ): PyStatement {
            return if (simplifyDelayed != null) {
                simplifyDelayed().move(ifStatement, doBefore, commandPerformer).statement()
            } else ifStatement
        }

        private fun restoreIfCorrectness(
            conditions: List<PyExpression>,
            firstToKeep: PyStatementPart,
            simplifiedFirstRange: StatementRange
        ) {
            val replacementPart = when (firstToKeep) {
                is PyIfPart -> {
                    generator.createIfPartFromIfPart(firstToKeep)
                }
                is PyElsePart -> {
                    val disjunction = generator.createBinaryOperandList("or", conditions)
                    val notDisjunction = generator.createPrefixExpression(
                        "not",
                        PyUtils.braceExpression(disjunction)
                    )
                    generator.createIfPart(
                        notDisjunction,
                        simplifiedFirstRange.first.nextStatements()
                            .takeWhile { it != simplifiedFirstRange.last }
                            .toList() + simplifiedFirstRange.last
                    )
                }
                else -> fail("Unexpected type of if part encountered")
            }
            // Todo: replace
            commandPerformer.performCommand(
                Command(
                    runInWCA(firstToKeep.project){ firstToKeep.replace(replacementPart) },
                    { },
                    "Replace a part to restore 'if' correctness"
                )
            )
        }

        private val PyIfStatement.hasAllConditions: Boolean
            get() = this.elsePart != null &&
                this.ifPart.condition != null &&
                this.elifParts.all { it.condition != null }

        private fun getPrefixLength(
            statementLists: List<List<PyStatement>>,
            allConditionsArePure: Boolean,
            maxAllowedPrefixLength: Int
        ): Int {
            return if (allConditionsArePure) {
                minOf(getCommonPrefixLength(statementLists), maxAllowedPrefixLength)
            } else 0
        }

        private fun getCommonPrefixLength(statementLists: List<List<PyStatement>>): Int {
            var commonPrefixLength = 0
            while (areEqualNotNullStatements(statementLists.map { it.getOrNull(commonPrefixLength) })) {
                ++commonPrefixLength
            }
            return commonPrefixLength
        }

        private fun getCommonSuffixLength(statementLists: List<List<PyStatement>>): Int =
            getCommonPrefixLength(statementLists.map { it.reversed() })

        private fun areEqualNotNullStatements(statements: List<PyStatement?>): Boolean =
            statements.all { it != null && areStatementsEquivalent(it, statements.first()) }

        private fun areStatementsEquivalent(first: PyStatement, second: PyStatement?): Boolean {
            if (second == null) {
                return false
            }
            // PsiEquivalenceUtil.areElementsEquivalent(first, second)
            // TODO: replace with something more sensible
            return first.textMatches(second)
        }
    }
}
