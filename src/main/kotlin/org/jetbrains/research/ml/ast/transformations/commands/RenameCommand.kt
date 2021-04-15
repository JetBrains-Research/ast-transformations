package org.jetbrains.research.ml.ast.transformations.commands

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.usageView.UsageInfo
import java.util.concurrent.Callable


class RenamablePsiElement(private val psiElement: PsiElement, newName: String) {
    private val oldName: String =
        (psiElement as PsiNamedElement).name ?: error("Element ${psiElement.text} does not have name")
    private val delayedNewRenames = renameElementDelayed(psiElement, newName)
    private val delayedOldRenames = renameElementDelayed(psiElement, oldName)

    fun performNewRenames() {
        WriteCommandAction.runWriteCommandAction(psiElement.project) {
            delayedNewRenames()
        }
    }

    fun performOldRenames() {
        WriteCommandAction.runWriteCommandAction(psiElement.project) {
            delayedOldRenames()
        }
    }

    private fun renameElementDelayed(definition: PsiElement, newName: String): () -> Unit {
        val processor = RenamePsiElementProcessor.forElement(definition)
        val allRenames = mutableMapOf(definition to newName)
        processor.prepareRenaming(definition, newName, allRenames)
        val delayedRenames = allRenames.map { renameSingleElementDelayed(it.key, it.value) }
        return { delayedRenames.forEach { it() } }
    }

    private fun renameSingleElementDelayed(definition: PsiElement, newName: String): () -> Unit {
        val processor = RenamePsiElementProcessor.forElement(definition)
        val useScope = definition.useScope
        val references = processor.findReferences(definition, useScope, false)
        val usages = references.map { UsageInfo(it) }.toTypedArray()
        return {
            WriteCommandAction.runWriteCommandAction(definition.project) {
                processor.renameElement(definition, newName, usages, null)
            }
        }
    }
}


/**
 * Rename psiElement and all references
 * NOTE: all renamablePsiElements should be initialized BEFORE the first RenameCommand performing
 */
class RenameCommand(private val renamablePsiElement: RenamablePsiElement) : CommandProvider<Unit>() {

    override fun redo(): Callable<Unit> {
        return Callable {
            renamablePsiElement.performNewRenames()
        }
    }

    override fun undo(): Callable<*> = Callable { renamablePsiElement.performOldRenames() }
}
