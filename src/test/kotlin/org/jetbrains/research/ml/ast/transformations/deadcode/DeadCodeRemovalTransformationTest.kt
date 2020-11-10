package org.jetbrains.research.ml.ast.transformations.deadcode

import org.jetbrains.research.ml.ast.transformations.util.TransformationsTest
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DeadCodeRemovalTransformationTest : TransformationsTest(getResourcesRootPath(::TransformationsTest)) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: ({0}, {1})")
        fun getTestData() = getInAndOutArray(::DeadCodeRemovalTransformationTest)
    }

    @Test
    fun testForwardTransformation() {
        assertCodeTransformation(inFile!!, outFile!!) { psiTree, toStoreMetadata ->
            val transformation = DeadCodeRemovalTransformation()
            transformation.apply(psiTree, toStoreMetadata)
        }
    }

    @Ignore
    @Test
    fun testInverseTransformation() {
        assertCodeTransformation(inFile!!, inFile!!) { psiTree, toStoreMetadata ->
            val transformation = DeadCodeRemovalTransformation()
            transformation.apply(psiTree, toStoreMetadata)
            transformation.inverseApply(psiTree)
        }
    }
}