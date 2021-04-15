/*
 * Copyright (c) 2020 Birillo A., Bobrov A., Lyulina E.
 */

package org.jetbrains.research.ml.ast.transformations.deadcode

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyIfStatement
import com.jetbrains.python.psi.PyWhileStatement
import org.jetbrains.research.ml.ast.transformations.Transformation
import org.jetbrains.research.ml.ast.transformations.commands.Command
import org.jetbrains.research.ml.ast.transformations.commands.DeleteCommand
import org.jetbrains.research.ml.ast.transformations.commands.ICommandPerformer
import org.jetbrains.research.ml.ast.transformations.commands.RestorablePsiElement
import org.jetbrains.research.ml.ast.transformations.util.PsiUtil.acceptStatements

object DeadCodeRemovalTransformation : Transformation() {
    override val key: String = "DeadCodeRemoval"

    override fun forwardApply(psiTree: PsiElement, commandPerformer: ICommandPerformer) {
        val heuristicVisitor = DeadCodeRemovalHeuristicVisitor(commandPerformer)
        val ifStatements = PsiTreeUtil.collectElementsOfType(psiTree, PyIfStatement::class.java)
        val whileStatements = PsiTreeUtil.collectElementsOfType(psiTree, PyWhileStatement::class.java)
        acceptStatements(psiTree.project, ifStatements + whileStatements, heuristicVisitor)

        val cfgVisitor = DeadCodeRemovalCFGVisitor()
        psiTree.accept(cfgVisitor)

        for (unreachable in cfgVisitor.unreachableElements) {
            commandPerformer.performCommand(DeleteCommand(unreachable).getCommand("Delete unreachable element"))
        }
    }
}
