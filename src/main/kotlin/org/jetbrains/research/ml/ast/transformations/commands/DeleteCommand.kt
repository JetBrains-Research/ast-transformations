package org.jetbrains.research.ml.ast.transformations.commands

import com.intellij.application.options.CodeStyle
import com.intellij.formatting.FormattingContext
import com.intellij.formatting.FormattingModel
import com.intellij.lang.LanguageFormatting
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiParserFacade
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator
import java.util.concurrent.Callable

/**
 * Represents a range of adjacent siblings from [startPsi] (inclusive) to [endPsi] (inclusive),
 * siblings are the first (prev, next) siblings that are not indents.
 */
class RestorablePsiElement(private var psiElement: PsiElement) {

    enum class Type {
        ONLY_CHILD, LEFT_CHILD, RIGHT_CHILD, MIDDLE_CHILD
    }

    private val psiText: String = psiElement.text
    private val project = psiElement.project

    private var prevSibling: PsiElement? = findNextSiblingOnCondition(
        { it.prevSibling },
        { this.prevSibling = it.newPsi }
    )
    private var nextSibling: PsiElement? = findNextSiblingOnCondition(
        { it.nextSibling },
        { this.nextSibling = it.newPsi }
    )
    private var parent = findParent()
    private val type = findType()
    private val indents = RestorableIndents()

    private fun isIndent(psiElement: PsiElement) = psiElement is PsiWhiteSpace

    /**
     * Besides [psiElement], that we want to delete and restore, there may also be indents(PsiWhiteSpace) before
     * (we store it in [prevIndent]) or after (in [nextIndent]) the [psiElement] that also may be changed during
     * the [psiElement] deletion, since formatting is applied. So we want to store them and restore after deletion.
     * The exact steps are as follows:
     * 1. Store [prevIndent] and [nextIndent] (they may be null), see #findIndentBetween
     * 2. Delete [psiElement]
     * 3. Add [psiElement]
     * 4. Find the new prevIndent and nextIndent after insertion of [psiElement], see #findIndentBetween
     * 5. compare new indents with old indents and replace new one if they don't match see #checkNewIndent
     */
    inner class RestorableIndents {
        private val prevIndent = findIndentBetween(prevSibling, psiElement)
        private val nextIndent = findIndentBetween(psiElement, nextSibling)

        /**
         * We need to replace whitespaces, and we cannot use the usual PsiElement#replace since it changes formatting
         * and doesn't always work with whitespaces as expected. Instead, we can use FormattingModel that can replace
         * whitespaces exactly like we need it.
         */
        private val formattingModel = createFormattingModel()

        private fun createFormattingModel(): FormattingModel {
            val builder = LanguageFormatting.INSTANCE.forContext(psiElement)
            require(builder != null) { "LanguageFormatting is null for $psiText" }
            val settings: CodeStyleSettings = CodeStyle.getSettings(project)
            return builder.createModel(FormattingContext.create(psiElement, settings))
        }

        private fun generateIndentFromText(text: String): PsiElement {
            return PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText(text)
        }

        private fun PsiElement.replaceIndent(newIndentText: String) {
            formattingModel.replaceWhiteSpace(this.textRange, newIndentText)
            formattingModel.commitChanges()
        }

        /**
         * Finds indent between [leftPsi] and [rightPsi] by finding non-null psi among them and returning
         * nextSibling of [leftPsi] if it's not null, and prevSibling of [rightPsi] otherwise
         *
         * leftPsi -- indent -- rightPsi
         *
         * leftPsi -- indent -- null    =>   indent = leftPsi.nextSibling
         * null -- indent -- rigthPsi   =>   indent = rightPsi.prevSibling
         * null -- ??? -- null          =>   error("Cannot get indent between two nulls")
         */
        private fun findIndentBetween(leftPsi: PsiElement?, rightPsi: PsiElement?): PsiElement? {
            fun findIndent(firstPsi: PsiElement, secondPsi: PsiElement?, getNextPsi: (PsiElement) -> PsiElement?): PsiElement? {
                val nextPsi = getNextPsi(firstPsi)
                return nextPsi?.let {
                    if (isIndent(it)) {
                        require(getNextPsi(it) === secondPsi) { "There is more than one indent between left and right psi" }
                        it
                    } else {
                        require(it === secondPsi) { "There is not an indent between left and right psi" }
                        null
                    }
                }
            }

            return if (leftPsi != null) {
                findIndent(leftPsi, rightPsi) { it.nextSibling }
            } else if (rightPsi != null) {
                findIndent(rightPsi, leftPsi) { it.prevSibling }
            } else {
                error("Cannot get indent between two nulls")
            }
        }

        fun addElement(psiElementToAdd: PsiElement, add: (PsiElement) -> PsiElement): PsiElement {
            val addedPsiElement = WriteCommandAction.runWriteCommandAction<PsiElement>(project) {
                add(psiElementToAdd)
            }
            val newPrevIndent = findIndentBetween(prevSibling, addedPsiElement)
            val newNextIndent = findIndentBetween(addedPsiElement, nextSibling)
            checkNewIndent(newPrevIndent, prevIndent) { parent.addBefore(it, addedPsiElement) }
            checkNewIndent(newNextIndent, nextIndent) { parent.addAfter(it, addedPsiElement) }
            return addedPsiElement
        }

        private fun checkNewIndent(newIndent: PsiElement?, oldIndent: PsiElement?, addIndent: (PsiElement) -> Unit) {
            if (oldIndent == null && newIndent == null)
                return
            if (newIndent == null) {
                val indentToAdd = generateIndentFromText(oldIndent!!.text)
                WriteCommandAction.runWriteCommandAction(project) {
                    addIndent(indentToAdd)
                }
                return
            }
            if (oldIndent == null) {
                error("New indent after deletion")
            }
            if (oldIndent.text != newIndent.text) {
                WriteCommandAction.runWriteCommandAction(project) {
                    newIndent.replaceIndent(oldIndent.text)
                }
            }
        }
    }



    private fun findType(): Type {
        if (prevSibling == null && nextSibling == null) {
            return Type.ONLY_CHILD
        }
        if (prevSibling == null) {
            return Type.LEFT_CHILD
        }
        if (nextSibling == null) {
            return Type.RIGHT_CHILD
        }
        return Type.MIDDLE_CHILD
    }

    private fun findNextSiblingOnCondition(
        getSibling: (PsiElement) -> PsiElement?,
        onUpdate: ((PsiUpdatesPublisher.UpdatedPsi) -> Unit)? = null,
        initPsi: PsiElement = psiElement
    ): PsiElement? {
        var nextSibling = getSibling(initPsi)
        if (nextSibling != null && isIndent(nextSibling)) {
            nextSibling = getSibling(nextSibling)
        }
        require(nextSibling == null || !isIndent(nextSibling)) { "More than one indents between psiElement and its sibling" }
        return nextSibling?.also { s -> onUpdate?.let { PsiUpdatesPublisher.subscribe(s, onUpdate) } }
    }

    private fun findParent(): PsiElement {
        require(psiElement.parent != null) { "We cannot restore psi element without parent" }
        return psiElement.parent!!.also { PsiUpdatesPublisher.subscribe(it) { this.parent = it.newPsi } }
    }


    fun delete() {
        WriteCommandAction.runWriteCommandAction(project) {
            psiElement.delete()
        }
    }

    private fun generateFromText(): PsiElement {
        val generator = PyElementGenerator.getInstance(project)
        return generator.createFromText(LanguageLevel.getDefault(), PsiElement::class.java, psiText)
    }


    fun restore() {
        val psiElementToAdd = generateFromText()
        val addedPsiElement = when (type) {
            Type.ONLY_CHILD -> indents.addElement(psiElementToAdd) { parent.add(it) }
            Type.LEFT_CHILD, Type.MIDDLE_CHILD -> indents.addElement(psiElementToAdd) { parent.addBefore(it, nextSibling) }
            Type.RIGHT_CHILD -> indents.addElement(psiElementToAdd) { parent.addAfter(it, prevSibling) }
        }
        PsiUpdatesPublisher.notify(PsiUpdatesPublisher.UpdatedPsi(psiElement, addedPsiElement))
    }
}



// Note, you cannot redo it one more time after undo
class DeleteCommand(private val psiElement: PsiElement) : CommandProvider<Unit>() {
    //  Creates restorablePsiElement just before psiElement deletion
    lateinit var restorablePsiElement: RestorablePsiElement

    override fun redo(): Callable<Unit> {
        restorablePsiElement = RestorablePsiElement(psiElement)
        return Callable { restorablePsiElement.delete() }
    }

    override fun undo(): Callable<*> {
        return Callable { restorablePsiElement.restore() }
    }
}

// Todo: make PsiUpdatesPublisher per PsiFile
object PsiUpdatesPublisher {
    data class UpdatedPsi(val oldPsi: PsiElement, val newPsi: PsiElement)

    /**
     * Stores all actions need to be performed when some Psi has changes from old to new
     */
    private val subscribedPsi: MutableMap<PsiElement, MutableList<(UpdatedPsi) -> Unit>> = hashMapOf()

    /**
     * Subscribes on [psiElement] update event, performs [onUpdate] once it happens
     */
    fun subscribe(psiElement: PsiElement, onUpdate: (UpdatedPsi) -> Unit) {
        subscribedPsi.getOrPut(psiElement, { arrayListOf() }).add(onUpdate)
    }

    /**
     * Notify all subscribers that some Psi is updated, so all stored onUpdates are called
     */
    fun notify(updatedPsi: UpdatedPsi) {
        subscribedPsi[updatedPsi.oldPsi]?.let {
            it.forEach { onUpdate -> onUpdate(updatedPsi) }
//          Change key from oldPsi to newPsi
            subscribedPsi[updatedPsi.newPsi] = it
            subscribedPsi.remove(updatedPsi.oldPsi)
        }
    }
}


