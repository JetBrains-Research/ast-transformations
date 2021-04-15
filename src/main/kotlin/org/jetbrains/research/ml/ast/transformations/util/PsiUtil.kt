package org.jetbrains.research.ml.ast.transformations.util

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.parents
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyExpressionStatement
import com.jetbrains.python.psi.PyStringLiteralExpression
import org.jetbrains.research.ml.ast.transformations.util.PsiUtil.isTripleQuotedString

object PsiUtil {
    fun acceptStatements(project: Project, statements: Collection<PsiElement>, visitor: PsiElementVisitor) {
//        WriteCommandAction.runWriteCommandAction(project) {
            for (statement in statements) {
                statement.accept(visitor)
//            }
        }
    }

    val PyStringLiteralExpression.isTripleQuotedString: Boolean
        get() = this.stringNodes.size == 1 && stringNodes[0].elementType === PyTokenTypes.TRIPLE_QUOTED_STRING &&
            this.parents.toList().isNotEmpty() && this.parents.first() is PyExpressionStatement

    val PyStringLiteralExpression.isComment: Boolean
        get() = this.isDocString || this.isTripleQuotedString
}
