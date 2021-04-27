package org.jetbrains.research.ml.ast.transformations.anonymization

import org.jetbrains.research.ml.ast.transformations.util.TransformationsTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class AnonymizationTransformationTest : TransformationsTest(getResourcesRootPath(::AnonymizationTransformationTest)) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: ({0}, {1})")
        fun getTestData() =
            getInAndOutArray(::AnonymizationTransformationTest, resourcesRoot)
                .filter { it[0].name.contains("fake") }
//                .filter { it[0].name.contains("in_3_o") }
    }

    @Test
    fun testForwardTransformation() {
        val transformation = AnonymizationTransformation()
        assertCodeTransformation(
            inFile!!,
            outFile!!,
            transformation::forwardApply
        )
    }

    @Test
    fun testDeanonymization() {
        val transformation = AnonymizationTransformation()
        assertInverseCodeTransformation(
            inFile!!,
            inFile!!,
            transformation::forwardApply,
            transformation::inverseApply
        )
    }
}
