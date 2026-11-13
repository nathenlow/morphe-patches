/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * File-Specific License Notice (GPLv3 Section 7 Terms)
 *
 * This file is part of the Morphe project and is licensed under
 * the GNU General Public License version 3 (GPLv3), with the Additional
 * Terms under Section 7 described in the LICENSE file.
 *
 * https://www.gnu.org/licenses/gpl-3.0.html
 *
 * Section 7b: Notice Preservation
 * -------------------------------
 * This entire comment block must be preserved in all copies,
 * distributions, and derivative works of this file, in both
 * original and modified source forms.
 *
 * Portions of this software are provided "AS IS" by the Morphe software project.
 * Any express or implied warranties, including the implied warranties of
 * merchantability and fitness for a particular purpose, are disclaimed.
 */

package app.morphe.patches.mewatch.tv.ads

import app.morphe.patcher.patch.patch
import app.morphe.patches.all.misc.ads.removeAdsPatch
import app.morphe.patches.mewatch.shared.Constants.COMPATIBILITY_MEWATCH_TV

@Suppress("unused")
val mewatchTvRemoveAdsPatch = patch(
    name = "Remove ads (mewatch TV)",
    description = "Removes ads from mewatch on Android TV by blocking ad servers, disabling ad " +
            "components and making the video player skip ad requests. Ads stitched into live " +
            "streams are not covered.",
) {
    dependsOn(removeAdsPatch)

    compatibleWith(COMPATIBILITY_MEWATCH_TV)
}
