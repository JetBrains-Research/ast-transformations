package org.jetbrains.research.ml.ast.transformations.inputDescriptionElimination

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyCallExpression
import org.jetbrains.research.ml.ast.transformations.PerformedCommandStorage
import org.jetbrains.research.ml.ast.transformations.Transformation
import org.jetbrains.research.ml.ast.transformations.util.PsiUtil

object InputDescriptionEliminationTransformation : Transformation() {
    override val key: String = "InputDescriptionElimination"

    override fun forwardApply(psiTree: PsiElement, commandsStorage: PerformedCommandStorage?) {
        val callExpressions = PsiTreeUtil.collectElementsOfType(psiTree, PyCallExpression::class.java)
        val visitor = InputDescriptionEliminationVisitor(commandsStorage)
        PsiUtil.acceptStatements(psiTree.project, callExpressions, visitor)
    }
}
