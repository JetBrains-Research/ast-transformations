/*
 * Copyright (c) 2020.  Anastasiia Birillo, Elena Lyulina
 */

package org.jetbrains.research.ml.ast.transformations.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.apache.log4j.PropertyConfigurator
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.runners.Parameterized
import java.io.File
import java.util.logging.Logger
import kotlin.reflect.KFunction

@Ignore
open class TransformationsTest(private val testDataRoot: String) : BasePlatformTestCase() {
    protected val LOG = Logger.getLogger(javaClass.name)

    // We should define the root resources folder
    override fun getTestDataPath() = testDataRoot

    @JvmField
    @Parameterized.Parameter(0)
    var inFile: File? = null

    @JvmField
    @Parameterized.Parameter(1)
    var outFile: File? = null

    companion object {
        // We can not get the root of the class resources automatically
        private const val resourcesRoot: String = "data"

        fun getInAndOutArray(
            cls: KFunction<TransformationsTest>,
            resourcesRootName: String = resourcesRoot,
        ): List<Array<File>> {
            val inAndOutFilesMap = Util.getInAndOutFilesMap(getResourcesRootPath(cls, resourcesRootName))
            return inAndOutFilesMap.entries.map { (inFile, outFile) -> arrayOf(inFile, outFile) }
        }

        fun getResourcesRootPath(
            cls: KFunction<TransformationsTest>,
            resourcesRootName: String = resourcesRoot
        ): String = cls.javaClass.getResource(resourcesRootName).path
    }

    /*
    *  Older JUnit was calling this setUp method automatically, and newer one stopped to do that, and now requires
    *  an explicit @Before annotation.
    * */
    @Before
    fun mySetUp() {
        // Configure log4j
        PropertyConfigurator.configure(getResourcesRootPath(::TransformationsTest, "log4j.properties"))
        super.setUp()
    }

    @After
    fun myDispose() {
        super.tearDown()
    }

    protected fun assertCodeTransformation(
        inFile: File,
        outFile: File,
        transformation: (PsiElement, Boolean) -> Unit
    ) {
        LOG.info("The current input file is: ${inFile.path}")
        LOG.info("The current output file is: ${outFile.path}")
        val expectedSrc = Util.getContentFromFile(outFile)
        LOG.info("The expected code is:\n$expectedSrc")
        val psiInFile = myFixture.configureByFile(inFile.name)
        ApplicationManager.getApplication().invokeAndWait {
            transformation(psiInFile, true)
        }
        val actualSrc = ApplicationManager.getApplication().runReadAction<String> {
            val actualSrc = psiInFile.text
            LOG.info("The actual code is:\n$actualSrc")
            actualSrc
        }
        assertEquals(expectedSrc, actualSrc)
    }
}
