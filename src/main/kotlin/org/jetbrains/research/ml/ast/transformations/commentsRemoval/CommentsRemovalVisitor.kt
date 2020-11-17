package org.jetbrains.research.ml.ast.transformations.commentsRemoval

import com.intellij.psi.PsiComment
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyStringLiteralExpression

class CommentsRemovalVisitor : PyElementVisitor() {

    override fun visitComment(comment: PsiComment) {
        comment.delete()
        super.visitComment(comment)
    }

    override fun visitPyStringLiteralExpression(node: PyStringLiteralExpression?) {
        if (node != null) {
            if (node.isDocString || node.isTripleQuotedString) {
                node.delete()
            }
        }
        super.visitPyStringLiteralExpression(node)
    }

    private val PyStringLiteralExpression.isTripleQuotedString: Boolean
        get() = this.stringNodes.size == 1 && stringNodes[0].elementType === PyTokenTypes.TRIPLE_QUOTED_STRING
}
