package org.jetbrains.research.ml.ast.transformations.ifRedundantLinesRemoval

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyFile
import org.jetbrains.research.ml.ast.transformations.Transformation
import org.jetbrains.research.ml.ast.transformations.commands.ICommandPerformer

object IfRedundantLinesRemovalTransformation : Transformation() {
    override val key: String = "IfRedundantLinesRemoval"

    override fun forwardApply(psiTree: PsiElement, commandPerformer: ICommandPerformer) {
        val project = psiTree.project
        val remover = IfRedundantLinesRemover(
            commandPerformer,
            PyElementGenerator.getInstance(project),
            psiTree.containingFile as PyFile
        )
        val simplify = remover.simplifyAllDelayed(psiTree)
//      For some reason it fails if run without runWriteCommandAction (although all modification actions are wrapped into WCA)
        WriteCommandAction.runWriteCommandAction(project) { simplify() }
    }
}
