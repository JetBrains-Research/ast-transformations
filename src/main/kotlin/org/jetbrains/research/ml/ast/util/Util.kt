package org.jetbrains.research.ml.ast.util

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import java.util.concurrent.Callable

fun <T>runInWCA(project: Project, action: () -> T): Callable<T> {
    return Callable {
        WriteCommandAction.runWriteCommandAction<T>(project) {
            action()
        }
    }
}
