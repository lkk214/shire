package com.phodal.shirelang.compiler.variable.resolver

import com.phodal.shirecore.provider.variable.model.SystemInfoVariable
import com.phodal.shirelang.compiler.variable.resolver.base.VariableResolver
import com.phodal.shirelang.compiler.variable.resolver.base.VariableResolverContext

/**
 * SystemInfoVariableResolver is a class that provides a way to resolve system information variables.
 */
class SystemInfoVariableResolver(
    private val context: VariableResolverContext,
) : VariableResolver {
    override suspend fun resolve(initVariables: Map<String, Any>): Map<String, Any> {
        return SystemInfoVariable.all().associate {
            it.variableName to it.value!!
        }
    }
}