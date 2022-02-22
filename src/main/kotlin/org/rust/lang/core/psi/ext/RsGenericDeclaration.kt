/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.*
import org.rust.lang.core.types.consts.Const
import org.rust.lang.core.types.consts.CtConstParameter
import org.rust.lang.core.types.regions.ReEarlyBound
import org.rust.lang.core.types.regions.Region
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyTypeParameter

interface RsGenericDeclaration : RsElement {
    val typeParameterList: RsTypeParameterList?
    val whereClause: RsWhereClause?
}

fun RsGenericDeclaration.getGenericParameters(
    includeLifetimes: Boolean = true,
    includeTypes: Boolean = true,
    includeConsts: Boolean = true
): List<RsGenericParameter> = typeParameterList?.getGenericParameters(
    includeLifetimes,
    includeTypes,
    includeConsts
).orEmpty()

val RsGenericDeclaration.typeParameters: List<RsTypeParameter>
    get() = typeParameterList?.typeParameterList.orEmpty()

val RsGenericDeclaration.lifetimeParameters: List<RsLifetimeParameter>
    get() = typeParameterList?.lifetimeParameterList.orEmpty()

val RsGenericDeclaration.constParameters: List<RsConstParameter>
    get() = typeParameterList?.constParameterList.orEmpty()

val RsGenericDeclaration.requiredGenericParameters: List<RsGenericParameter>
    get() = getGenericParameters().filter {
        when (it) {
            is RsTypeParameter -> it.typeReference == null
            is RsConstParameter -> it.expr == null
            else -> false
        }
    }

val RsGenericDeclaration.defaultRegionArguments: List<Region>
    get() = lifetimeParameters.map { param -> ReEarlyBound(param) }

val RsGenericDeclaration.defaultTypeArguments: List<Ty>
    get() = typeParameters.map { param -> TyTypeParameter.named(param) }

val RsGenericDeclaration.defaultConstArguments: List<Const>
    get() = constParameters.map { param -> CtConstParameter(param) }
