/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.types

import org.jetbrains.kotlin.types.model.*

open class TypeApproximatorConfiguration {
    enum class IntersectionStrategy {
        ALLOWED,
        TO_FIRST,
        TO_COMMON_SUPERTYPE,
        TO_UPPER_BOUND_IF_SUPERTYPE
    }

    open val keepFlexible: Boolean get() = false // simple flexible types (FlexibleTypeImpl)
    open val keepDynamic: Boolean get() = false // DynamicType
    open val keepRawTypes: Boolean get() = false // RawTypeImpl
    open val keepErrorTypes: Boolean get() = false

    open val approximateIntegerLiteralConstantTypes: Boolean get() = false // IntegerLiteralTypeConstructor
    open val approximateIntegerConstantOperatorTypes: Boolean get() = false
    open val expectedTypeForIntegerLiteralType: KotlinTypeMarker? get() = null

    open val keepDefinitelyNotNullTypes: Boolean get() = true
    open val intersectionStrategy: IntersectionStrategy = IntersectionStrategy.TO_COMMON_SUPERTYPE
    open val approximateIntersectionTypesInContravariantPositions = false
    open val approximateLocalTypes = false

    /**
     * Is only expected to be true for FinalApproximationAfterResolutionAndInference
     * But it's only used for K2 to reproduce K1 behavior for the approximation of resolved calls
     */
    open val convertToNonRawVersionAfterApproximationInK2 get() = false

    /**
     * Whether to approximate anonymous type. This flag does not have any effect if `localTypes` is true because all anonymous types are
     * local.
     */
    open val approximateAnonymous = false

    /**
     * This function determines the approximator behavior if a type variable based type is encountered.
     *
     * @param marker type variable encountered
     * @param isK2 true for K2 compiler, false for K1 compiler
     * @return true if the type variable based type should be kept, false if it should be approximated
     */
    internal open fun shouldKeepTypeVariableBasedType(marker: TypeVariableTypeConstructorMarker, isK2: Boolean): Boolean = false

    open fun shouldApproximateCapturedType(ctx: TypeSystemInferenceExtensionContext, type: CapturedTypeMarker): Boolean =
        true  // false means that this type we can leave as is

    abstract class AllFlexibleSameValue : TypeApproximatorConfiguration() {
        abstract val keepAllFlexible: Boolean

        override val keepFlexible: Boolean get() = keepAllFlexible
        override val keepDynamic: Boolean get() = keepAllFlexible
        override val keepRawTypes: Boolean get() = keepAllFlexible
    }

    object LocalDeclaration : AllFlexibleSameValue() {
        override val keepAllFlexible: Boolean get() = true
        override val intersectionStrategy: IntersectionStrategy get() = IntersectionStrategy.ALLOWED
        override val keepErrorTypes: Boolean get() = true
        override val approximateIntegerLiteralConstantTypes: Boolean get() = true
        override val approximateIntersectionTypesInContravariantPositions: Boolean get() = true

        // Probably, it's worth thinking of returning true only for delegated property accessors, see KT-61090
        override fun shouldKeepTypeVariableBasedType(marker: TypeVariableTypeConstructorMarker, isK2: Boolean): Boolean = isK2
    }

    open class PublicDeclaration(override val approximateLocalTypes: Boolean, override val approximateAnonymous: Boolean) : AllFlexibleSameValue() {
        override val keepAllFlexible: Boolean get() = true
        override val keepErrorTypes: Boolean get() = true
        override val keepDefinitelyNotNullTypes: Boolean get() = false
        override val approximateIntegerLiteralConstantTypes: Boolean get() = true
        override val approximateIntersectionTypesInContravariantPositions: Boolean get() = true

        // Probably, it's worth thinking of returning true only for delegated property accessors, see KT-61090
        override fun shouldKeepTypeVariableBasedType(marker: TypeVariableTypeConstructorMarker, isK2: Boolean): Boolean = isK2

        object SaveAnonymousTypes : PublicDeclaration(approximateLocalTypes = false, approximateAnonymous = false)
        object ApproximateAnonymousTypes : PublicDeclaration(approximateLocalTypes = false, approximateAnonymous = true)
    }

    sealed class AbstractCapturedTypesApproximation(val approximatedCapturedStatus: CaptureStatus?) :
        AllFlexibleSameValue() {
        override val keepAllFlexible: Boolean get() = true
        override val keepErrorTypes: Boolean get() = true

        // i.e. will be approximated only approximatedCapturedStatus captured types
        override fun shouldApproximateCapturedType(ctx: TypeSystemInferenceExtensionContext, type: CapturedTypeMarker): Boolean =
            approximatedCapturedStatus != null && type.captureStatus(ctx) == approximatedCapturedStatus

        override val intersectionStrategy: IntersectionStrategy get() = IntersectionStrategy.ALLOWED
        override fun shouldKeepTypeVariableBasedType(marker: TypeVariableTypeConstructorMarker, isK2: Boolean): Boolean = true
    }

    object IncorporationConfiguration : AbstractCapturedTypesApproximation(CaptureStatus.FOR_INCORPORATION)
    object SubtypeCapturedTypesApproximation : AbstractCapturedTypesApproximation(CaptureStatus.FOR_SUBTYPING)

    class TopLevelIntegerLiteralTypeApproximationWithExpectedType(
        override val expectedTypeForIntegerLiteralType: KotlinTypeMarker?,
    ) : AllFlexibleSameValue() {
        override val keepAllFlexible: Boolean get() = true
        override val approximateIntegerLiteralConstantTypes: Boolean get() = true
        override val approximateIntegerConstantOperatorTypes: Boolean get() = true
    }

    object InternalTypesApproximation : AbstractCapturedTypesApproximation(CaptureStatus.FROM_EXPRESSION) {
        override val approximateIntegerLiteralConstantTypes: Boolean get() = true
        override val approximateIntegerConstantOperatorTypes: Boolean get() = true
        override val approximateIntersectionTypesInContravariantPositions: Boolean get() = true
    }

    object FinalApproximationAfterResolutionAndInference :
        AbstractCapturedTypesApproximation(CaptureStatus.FROM_EXPRESSION) {
        override val approximateIntegerLiteralConstantTypes: Boolean get() = true
        override val approximateIntegerConstantOperatorTypes: Boolean get() = true
        override val approximateIntersectionTypesInContravariantPositions: Boolean get() = true

        override val convertToNonRawVersionAfterApproximationInK2: Boolean get() = true
    }

    object IntermediateApproximationToSupertypeAfterCompletionInK2 :
        AbstractCapturedTypesApproximation(null) {
        override val approximateIntegerLiteralConstantTypes: Boolean get() = true
        override val approximateIntegerConstantOperatorTypes: Boolean get() = true
        override val approximateIntersectionTypesInContravariantPositions: Boolean get() = true

        override val convertToNonRawVersionAfterApproximationInK2: Boolean get() = true

        override fun shouldApproximateCapturedType(ctx: TypeSystemInferenceExtensionContext, type: CapturedTypeMarker): Boolean {
            /**
             * Only approximate captured types when they contain a raw supertype.
             * This is an awful hack required to keep K1 compatibility.
             * See [convertToNonRawVersionAfterApproximationInK2].
             */
            return type.captureStatus(ctx) == CaptureStatus.FROM_EXPRESSION && with(ctx) { type.hasRawSuperType() }
        }
    }

    object TypeArgumentApproximation : AbstractCapturedTypesApproximation(null) {
        override val approximateIntegerLiteralConstantTypes: Boolean get() = true
        override val approximateIntegerConstantOperatorTypes: Boolean get() = true
        override val approximateIntersectionTypesInContravariantPositions: Boolean get() = true
    }

    object IntegerLiteralsTypesApproximation : AllFlexibleSameValue() {
        override val approximateIntegerLiteralConstantTypes: Boolean get() = true
        override val keepAllFlexible: Boolean get() = true
        override val intersectionStrategy: IntersectionStrategy get() = IntersectionStrategy.ALLOWED
        override fun shouldKeepTypeVariableBasedType(marker: TypeVariableTypeConstructorMarker, isK2: Boolean): Boolean = true
        override val keepErrorTypes: Boolean get() = true

        override fun shouldApproximateCapturedType(ctx: TypeSystemInferenceExtensionContext, type: CapturedTypeMarker): Boolean = false
    }

    object UpperBoundAwareIntersectionTypeApproximator : AllFlexibleSameValue() {
        override val keepAllFlexible: Boolean get() = true
        override val intersectionStrategy: IntersectionStrategy = IntersectionStrategy.TO_UPPER_BOUND_IF_SUPERTYPE
    }

    object FrontendToBackendTypesApproximation : AllFlexibleSameValue() {
        override val keepAllFlexible: Boolean get() = true
        override val keepErrorTypes: Boolean get() = true
        override val approximateIntegerLiteralConstantTypes: Boolean get() = true
        override val approximateIntegerConstantOperatorTypes: Boolean get() = true
        override val approximateIntersectionTypesInContravariantPositions: Boolean get() = true
    }
}
