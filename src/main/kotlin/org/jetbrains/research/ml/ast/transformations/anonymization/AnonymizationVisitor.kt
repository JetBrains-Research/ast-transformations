package org.jetbrains.research.ml.ast.transformations.anonymization

import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyRecursiveElementVisitor
import org.jetbrains.research.ml.ast.transformations.commands.ICommandPerformer
import org.jetbrains.research.ml.ast.transformations.commands.RenamablePsiElement
import org.jetbrains.research.ml.ast.transformations.commands.RenameCommand

class AnonymizationVisitor(file: PyFile) : PyRecursiveElementVisitor() {
    private val project = file.project
    private val anonymizer = ElementAnonymizer()

    override fun visitElement(element: PsiElement) {
        anonymizer.registerElement(element)
        super.visitElement(element)
    }

    fun performAllRenames(commandsPerformer: ICommandPerformer) {
        val allRenames = anonymizer.getAllRenames()

        val renamablePsiElements = allRenames.map { (psi, newName) -> RenamablePsiElement(psi, newName) }
        renamablePsiElements.forEach {
            commandsPerformer.performCommand(RenameCommand(it).getCommand("Anonymize element"))
        }
    }
}
