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

package app.morphe.patches.all.misc.ads

import app.morphe.patcher.StringComparisonType
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceXml
import app.morphe.patcher.string
import app.morphe.patcher.util.matchAllMethodIndicesForEach
import app.morphe.patcher.util.returnEarly
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import java.util.logging.Logger

/**
 * Ad server domains. Any string constant containing one of these has the domain replaced with
 * [BLOCKED_HOST], so the ad SDK's request fails to resolve and the SDK reports a normal ad error
 * to the app. This is the same idea as the ad block list in Lucky Patcher: the ad SDK stays in
 * place and answers, it just never gets an ad.
 */
private val AD_DOMAINS = listOf(
    // Google AdMob / Ad Manager
    "doubleclick.net",
    "googlesyndication.com",
    "googleadservices.com",
    "admob.com",
    "adservice.google.com",
    // AppLovin
    "applovin.com",
    // Unity Ads
    "unityads.unity3d.com",
    // Meta Audience Network
    "an.facebook.com",
    // ironSource / LevelPlay
    "supersonicads.com",
    "ironsrc.com",
    // Vungle / Liftoff
    "vungle.com",
    // Chartboost
    "chartboost.com",
    // InMobi
    "inmobi.com",
    // StartApp
    "startappservice.com",
    "startappexchange.com",
    // Mintegral
    "mintegral.com",
    "mintegral.net",
    // Pangle / TikTok
    "pangle.io",
    "pangleglobal.com",
    // AdColony
    "adcolony.com",
    // Fyber
    "fyber.com",
    // MoPub
    "mopub.com",
    // Smaato
    "smaato.net",
    // Yandex Ads
    "yandexadexchange.net",
)

/** `.invalid` is reserved by RFC 2606 and never resolves, so requests fail immediately. */
private const val BLOCKED_HOST = "ads-blocked.invalid"

private val AD_DOMAIN_REGEX = Regex(AD_DOMAINS.joinToString("|") { Regex.escape(it) })

/** Package prefixes of ad SDK components that are disabled in the manifest. */
private val AD_COMPONENT_PREFIXES = listOf(
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

/**
 * ExoPlayer and Media3 ask an AdsLoader.Provider for an ad loader whenever a media item has ads.
 * Returning null makes the player skip ads and play the content.
 */
private val ADS_LOADER_PROVIDER_TYPES = setOf(
    "Landroidx/media3/exoplayer/source/ads/AdsLoader\$Provider;",
    "Lcom/google/android/exoplayer2/source/ads/AdsLoader\$Provider;",
)

private val MANIFEST_COMPONENT_TAGS = listOf("activity", "service", "receiver", "provider")

@Suppress("unused")
val removeAdsPatch = patch(
    name = "Remove ads",
    description = "Removes ads from common ad SDKs (AdMob, AppLovin, Unity Ads, Meta Audience " +
            "Network, ironSource, Vungle, Chartboost, InMobi, StartApp, Mintegral, Pangle, " +
            "AdColony, Fyber, MoPub, Smaato, Yandex) by blocking their ad servers and " +
            "disabling their components. Works on any app, but cannot remove ads an app " +
            "serves itself, ads stitched into the video stream, or ads from SDKs not listed.",
    default = false
) {
    execute {
        val logger = Logger.getLogger("RemoveAdsPatch")

        // Step 1: point every ad server domain in the app's string constants at a host that
        // never resolves. Each SDK then fails its request and reports an ad error.
        var blockedStrings = 0
        AD_DOMAINS.forEach { domain ->
            string(domain, StringComparisonType.CONTAINS).matchAllMethodIndicesForEach(
                requireMatches = false
            ) { index ->
                val instruction = getInstruction<ReferenceInstruction>(index)
                val string = (instruction.reference as? StringReference)?.string
                    ?: return@matchAllMethodIndicesForEach
                if (!AD_DOMAIN_REGEX.containsMatchIn(string)) return@matchAllMethodIndicesForEach

                replaceInstruction(
                    index,
                    BuilderInstruction21c(
                        Opcode.CONST_STRING,
                        (instruction as OneRegisterInstruction).registerA,
                        ImmutableStringReference(AD_DOMAIN_REGEX.replace(string, BLOCKED_HOST))
                    )
                )
                blockedStrings++
            }
        }

        // Step 2: disable every ad component declared in the manifest, so the system can
        // never launch an ad activity or start an ad service, receiver or provider.
        var disabledComponents = 0
        resourceXml("AndroidManifest.xml").use { manifest ->
            MANIFEST_COMPONENT_TAGS.forEach { tag ->
                manifest.elements(tag).forEach { component ->
                    val name = component.attribute("android:name") ?: return@forEach
                    if (AD_COMPONENT_PREFIXES.any { name.startsWith(it) }) {
                        component.setAttribute("android:enabled", "false")
                        disabledComponents++
                    }
                }
            }
        }

        // Step 3: make video players skip ads by returning no ad loader from any provider.
        // Collect first, then mutate, to avoid changing the class list while iterating it.
        val providerClasses = mutableListOf<ClassDef>()
        classDefForEach { classDef ->
            if (classDef.interfaces.any { it in ADS_LOADER_PROVIDER_TYPES }) providerClasses += classDef
        }

        var skippedProviders = 0
        providerClasses.forEach { classDef ->
            mutableClassDefBy(classDef).methods.forEach { method ->
                if (method.name == "getAdsLoader" &&
                    method.returnType.endsWith("/AdsLoader;") &&
                    method.implementation != null
                ) {
                    method.returnEarly(null)
                    skippedProviders++
                }
            }
        }

        logger.info(
            "Blocked $blockedStrings ad server strings, disabled $disabledComponents ad " +
                    "components, removed $skippedProviders ad loader providers."
        )
    }
}
