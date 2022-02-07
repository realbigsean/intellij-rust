/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsMetaItem

// TODO sealed class, meta item
sealed class RsStability {
    object Stable : RsStability()
    data class Unstable(val meta: RsMetaItem) : RsStability()
}
