package org.jetbrains.research.ml.ast.transformations.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.research.ml.ast.transformations.commands.Command
import org.jetbrains.research.ml.ast.transformations.commands.CommandPerformer
import org.jetbrains.research.ml.ast.transformations.commands.ICommandPerformer
import org.jetbrains.research.ml.ast.util.FileTestUtil
import org.jetbrains.research.ml.ast.util.ParametrizedBaseTest
import org.jetbrains.research.ml.ast.util.PsiFileHandler
import java.io.File
import java.nio.charset.Charset
import java.util.logging.Logger
import kotlin.reflect.KFunction

typealias TransformationDelayed = (PsiElement) -> Unit

/**
 * We want to ensure that all tests classes for transformations have the required functionality
 */
interface ITransformationsTest {
    var codeStyleManager: CodeStyleManager

    fun assertCodeTransformation(
        inFile: File,
        outFile: File,
        transformation: TransformationDelayed
    )
}

/**
 * We moved out common functions to make classes easier for tests with SDK and without SDK
 */
object TransformationsTestHelper {
    private val logger = Logger.getLogger(javaClass.name)

    fun getInAndOutArray(cls: KFunction<ParametrizedBaseTest>, resourcesRootName: String): List<Array<File?>> {
        val inAndOutFilesMap = FileTestUtil.getInAndOutFilesMap(
            ParametrizedBaseTest.getResourcesRootPath(cls, resourcesRootName)
        )
        return inAndOutFilesMap.entries.map { (inFile, outFile) -> arrayOf(inFile, outFile) }
    }

    fun assertCodeTransformation(
        inFile: File,
        outFile: File,
        transformation: TransformationDelayed,
        fileHandler: PsiFileHandler
    ) {
        logger.info("The current input file is: ${inFile.path}")
        logger.info("The current output file is: ${outFile.path}")
        val psiInFile = fileHandler.getPsiFile(inFile)
        val expectedPsiInFile = if (inFile.path == outFile.path) {
            psiInFile
        } else {
            fileHandler.getPsiFile(outFile)
        }
        val expectedSrc = expectedPsiInFile.text
        logger.info("The expected code is:\n$expectedSrc")
        ApplicationManager.getApplication().invokeAndWait {
            transformation(psiInFile)
//            PsiTestUtil.checkFileStructure(psiInFile)
        }
        fileHandler.formatPsiFile(psiInFile)
        val actualSrc = psiInFile.text
        logger.info("The actual code is:\n$actualSrc")
        BasePlatformTestCase.assertEquals(expectedSrc, actualSrc)
    }

    fun getBackwardTransformationWrapper(
        forwardTransformation: (PsiElement, cs: ICommandPerformer) -> Unit
    ): TransformationDelayed =
        { psi: PsiElement ->
            val commandStorage = CommandPerformer(psi, true)
            forwardTransformation(psi, commandStorage)
            PsiTestUtil.checkFileStructure(psi as PsiFile)
//            Todo: call WriteCommandAction inside command?
//            WriteCommandAction.runWriteCommandAction(psi.project) {
                commandStorage.undoAllPerformedCommands()
//            }
        }

    fun getCommandStorageTransformationWrapper(
        commandPerformerToTest: (PsiElement, Boolean) -> ICommandPerformer,
        forwardTransformation: (PsiElement, cs: ICommandPerformer) -> Unit
    ): TransformationDelayed =
        { psi: PsiElement ->
//          This commandStorage will perform all commands as commandStorageToTest does but with additional check between commands
            val commandStorage = TestCommandPerformer(commandPerformerToTest(psi, true))
            forwardTransformation(psi, commandStorage)
//            PsiTestUtil.checkFileStructure(psi as PsiFile)

//            Todo: call WriteCommandAction inside command?
//            WriteCommandAction.runWriteCommandAction(psi.project) {
                commandStorage.undoAllPerformedCommands()
//            }
        }
}


class TestCommandPerformer(private val performerToTest: ICommandPerformer) : ICommandPerformer {
    override val psiTree: PsiElement = performerToTest.psiTree
    private val codeStyleManager = CodeStyleManager.getInstance(psiTree.project)


    private data class PsiText(private val psiTree: PsiElement) {
        val psiText = psiTree.text.trim()
        val virtualFileText =
            psiTree.containingFile.virtualFile.contentsToByteArray().toString(Charset.defaultCharset()).trim()
    }

    override fun <T>performCommand(command: Command<T>): T {
        val textBeforeCommand = PsiText(psiTree)
        performerToTest.performCommand(command)

//      perform undo and assert equals
        undoPerformedCommands(1)
        val textAfterUndoCommand = PsiText(psiTree)

//      not sure should I assert virtual file texts?
        BasePlatformTestCase.assertEquals(
            "Psi texts after undoing ${command.description} don't match",
            textBeforeCommand.psiText,
            textAfterUndoCommand.psiText
        )

//      perform the command again
        return performerToTest.performCommand(command)
    }

    override fun undoPerformedCommands(n: Int) {
        performerToTest.undoPerformedCommands(n)
    }

    override fun undoAllPerformedCommands() {
        performerToTest.undoAllPerformedCommands()
    }
}
