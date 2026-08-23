package com.seolhwa.armyrist.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object ApkUpdateValidator {
    const val EXPECTED_PACKAGE = "com.seolhwa.armyrist"

    fun validate(
        context: Context,
        apkFile: File,
        metadata: UpdateReleaseMetadata
    ): UpdateValidationResult {
        if (!apkFile.isFile || apkFile.length() <= 0L) {
            return UpdateValidationResult.Invalid("다운로드한 APK 파일이 없습니다.")
        }

        val actualSha = runCatching { sha256(apkFile) }.getOrElse {
            return UpdateValidationResult.Invalid("다운로드 파일의 SHA-256을 계산할 수 없습니다.")
        }
        if (!actualSha.equals(metadata.apkSha256, ignoreCase = true)) {
            return UpdateValidationResult.Invalid("다운로드한 업데이트 파일을 확인할 수 없습니다. (SHA-256 불일치)")
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val archive = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: return UpdateValidationResult.Invalid("유효한 Android APK로 확인할 수 없습니다.")

        if (archive.packageName != EXPECTED_PACKAGE) {
            return UpdateValidationResult.Invalid("Armyrist와 다른 패키지의 APK입니다.")
        }

        val archiveCode = packageVersionCode(archive)
        val archiveName = archive.versionName.orEmpty()
        if (archiveCode != metadata.versionCode || archiveName != metadata.versionName) {
            return UpdateValidationResult.Invalid("APK 버전 정보가 Release Metadata와 일치하지 않습니다.")
        }

        val installed = runCatching {
            context.packageManager.getPackageInfo(context.packageName, flags)
        }.getOrNull()
            ?: return UpdateValidationResult.Invalid("현재 Armyrist의 서명 정보를 확인할 수 없습니다.")

        val installedSigners = signerDigests(installed)
        val archiveSigners = signerDigests(archive)
        if (installedSigners.isEmpty() || archiveSigners.isEmpty()) {
            return UpdateValidationResult.Invalid("APK 서명 정보를 확인할 수 없습니다.")
        }
        if (installedSigners != archiveSigners) {
            return UpdateValidationResult.Invalid("현재 Armyrist와 업데이트 APK의 서명이 일치하지 않습니다.")
        }

        return UpdateValidationResult.Valid
    }

    fun installedVersion(context: Context): InstalledVersion {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return InstalledVersion(
            versionName = info.versionName.orEmpty(),
            versionCode = packageVersionCode(info)
        )
    }

    internal fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun packageVersionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            info.signatures ?: emptyArray()
        }

        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.toSet()
    }
}
