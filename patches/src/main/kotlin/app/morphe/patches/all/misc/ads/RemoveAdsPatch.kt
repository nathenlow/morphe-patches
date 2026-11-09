/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * Original code hard forked from:
 * https://github.com/inotia00/revanced-patches/blob/54ce1d4808b12903602a1a0d9a721ee835093c38/patches/src/main/kotlin/app/revanced/patches/all/misc/network/OverrideCertificatePinningPatch.kt#L4
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

package app.morphe.patches.all.misc.ads

import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceXml
import app.morphe.patcher.util.returnEarly
import com.android.tools.smali.dexlib2.iface.ClassDef
import java.util.logging.Logger

/**
 * Package prefixes of common ad SDKs. Everything under these is treated as ad code,
 * which is the same assumption Lucky Patcher's ad removal makes.
 */
private val AD_SDK_PREFIXES = listOf(
    "com.google.android.gms.ads.",
    "com.google.ads.",
    "com.applovin.",
    "com.unity3d.ads.",
    "com.unity3d.services.ads.",
    "com.facebook.ads.",
    "com.ironsource.",
    "com.vungle.",
    "com.chartboost.",
    "com.inmobi.",
    "com.startapp.",
    "com.mbridge.msdk.",
    "com.bytedance.sdk.openadsdk.",
    "com.adcolony.",
    "com.fyber.",
    "com.mopub.",
    "com.smaato.",
    "com.yandex.mobile.ads.",
)

private val AD_SDK_TYPE_PREFIXES = AD_SDK_PREFIXES.map { "L" + it.replace('.', '/') }

/** Void methods that start, load or display an ad. These are replaced with a bare return. */
private val AD_ENTRY_METHOD = Regex(
    "^(load|show|init|initialize|start|preload|fetch|request|display|render|open)([A-Z0-9_].*)?$"
)

private val MANIFEST_COMPONENT_TAGS = listOf("activity", "service", "receiver", "provider")

@Suppress("unused")
val removeAdsPatch = patch(
    name = "Remove ads",
    description = "Removes ads from common ad SDKs (AdMob, AppLovin, Unity Ads, Meta Audience " +
            "Network, ironSource, Vungle, Chartboost, InMobi, StartApp, Mintegral, Pangle, " +
            "AdColony, Fyber, MoPub, Smaato, Yandex). Works on any app, but cannot remove ads " +
            "an app serves itself or ads from SDKs that are not listed.",
    default = false
) {
    execute {
        val logger = Logger.getLogger("RemoveAdsPatch")

        // Step 1: disable every ad component declared in the manifest, so the system can
        // never launch an ad activity or start an ad service/receiver/provider.
        var disabledComponents = 0
        resourceXml("AndroidManifest.xml").use { manifest ->
            MANIFEST_COMPONENT_TAGS.forEach { tag ->
                manifest.elements(tag).forEach { component ->
                    val name = component.attribute("android:name") ?: return@forEach
                    if (AD_SDK_PREFIXES.any { name.startsWith(it) }) {
                        component.setAttribute("android:enabled", "false")
                        disabledComponents++
                    }
                }
            }
        }

        // Step 2: stub the entry points of the ad SDK classes themselves.
        // Collect first, then mutate, to avoid changing the class list while iterating it.
        val adClasses = mutableListOf<ClassDef>()
        classDefForEach { classDef ->
            if (AD_SDK_TYPE_PREFIXES.any { classDef.type.startsWith(it) }) adClasses += classDef
        }

        var stubbedMethods = 0
        adClasses.forEach { classDef ->
            mutableClassDefBy(classDef).methods.forEach { method ->
                if (method.returnType == "V" &&
                    method.implementation != null && // skip abstract and native
                    !method.name.startsWith("<") && // skip constructors and static initializers
                    AD_ENTRY_METHOD.matches(method.name)
                ) {
                    method.returnEarly()
                    stubbedMethods++
                }
            }
        }

        logger.info("Disabled $disabledComponents ad components, stubbed $stubbedMethods ad methods.")
    }
}
