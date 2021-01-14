package org.jetbrains.research.ml.ast.gumtree.diff

import com.github.gumtreediff.io.TreeIoUtils
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiFile
import org.jetbrains.research.ml.ast.gumtree.Util
import org.jetbrains.research.ml.ast.gumtree.tree.PostOrderNumbering
import org.jetbrains.research.ml.ast.util.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class PsiElementTransformerTest : ParametrizedBaseTest(getResourcesRootPath(::PsiElementTransformerTest)) {
    private val numbering = PostOrderNumbering

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: ({0}, {1})")
        fun getTestData(): List<Array<Any>> {
            val files = FileTestUtil.getInAndOutFilesMap(
                getResourcesRootPath(::PsiElementTransformerTest),
                inFormat = TestFileFormat("src", Extension.Py, Type.Input),
                outFormat = TestFileFormat("dst", Extension.Py, Type.Output)
            )
            return files.map { f -> arrayOf(f.key, f.value) }
        }
    }

    @JvmField
    @Parameterized.Parameter(0)
    var srcFile: File? = null

    @JvmField
    @Parameterized.Parameter(1)
    var dstFile: File? = null

    // TODO: does not work for case
    //  src:
    //  a1 = False or False
    //  a2 = False and False
    //  and dst:
    //  a1 = False and False
    //  a2 = False or False
    @Test
    fun `apply src to dst actions`() = convertSrcToDst(srcFile!!, dstFile!!)

    @Test
    fun `apply dst to src actions`() = convertSrcToDst(dstFile!!, srcFile!!)

    private fun convertSrcToDst(srcFile: File, dstFile: File) {
        val srcPsi = myFixture.getPsiFile(srcFile)
        val dstPsi = myFixture.getPsiFile(dstFile)
        val expectedCode = dstPsi.text
        val srcContext = Util.getTreeContext(srcPsi, numbering)
        val dstContext = Util.getTreeContext(dstPsi, numbering)
        val matcher = Matcher(srcContext, dstContext)
        val actions = matcher.getEditActions()
        val srcXml = TreeIoUtils.toXml(srcContext).toString().removeSuffix("\n")
        val dstXml = TreeIoUtils.toXml(dstContext).toString().removeSuffix("\n")
        val w = PsiElementTransformer(project, srcPsi, dstPsi, numbering)
        WriteCommandAction.runWriteCommandAction(project) {
            w.applyActions(actions)
//            actions.forEach {
//                w.applyAction(it)
//            }
        }
        assertEquals(expectedCode, srcPsi.text)
    }
}
