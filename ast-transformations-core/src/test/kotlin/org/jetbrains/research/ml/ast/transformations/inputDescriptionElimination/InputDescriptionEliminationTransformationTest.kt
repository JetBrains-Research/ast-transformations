package org.jetbrains.research.ml.ast.transformations.inputDescriptionElimination

import org.jetbrains.research.ml.ast.transformations.util.TransformationsTest
import org.jetbrains.research.ml.ast.transformations.util.TransformationsTestHelper
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class InputDescriptionEliminationTransformationTest :
    TransformationsTest(getResourcesRootPath(::InputDescriptionEliminationTransformationTest)) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: ({0}, {1})")
        fun getTestData() = getInAndOutArray(::InputDescriptionEliminationTransformationTest, resourcesRoot)
    }

    @Test
    fun testForwardTransformation() {
        assertCodeTransformation(
            inFile!!,
            outFile!!,
            InputDescriptionEliminationTransformation::forwardApply
        )
    }

    @Test
    fun testBackwardTransformation() {
        assertCodeTransformation(
            inFile!!,
            inFile!!,
            TransformationsTestHelper.getBackwardTransformationWrapper(
                InputDescriptionEliminationTransformation::forwardApply
            )
        )
    }
}
