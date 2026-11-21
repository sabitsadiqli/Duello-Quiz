package com.sabit.duelloquiz.backend.exam

import com.sabit.duelloquiz.backend.ApiJson
import com.sabit.duelloquiz.backend.common.resolve
import com.sabit.duelloquiz.backend.image.MediaUrls

/** Builds the key-free views of a version (package) shared by the service and the importer. */
class ExamViews(private val mediaUrls: MediaUrls, private val config: ExamConfig) {

    fun media(audio: AudioRecord) = ExamMediaDto(
        url = mediaUrls.url(audio.storageKey),
        bytes = audio.bytes,
        sha256 = audio.fileSha256,
        contentType = audio.contentType,
        durationMs = audio.durationMs,
    )

    fun sections(template: ExamTemplate, language: String) =
        template.sections.mapIndexed { i, s -> ExamSectionDto(s.id, s.title.resolve(language), i + 1) }

    /** EXAM-03 / EXAM-21: tasks and media only, never correctness data or scripts. */
    fun packageOf(template: ExamTemplate, content: VersionContent, language: String, packageBytes: Long = content.packageBytes) =
        ExamPackageResponse(
            variantId = content.variantId.toString(),
            version = content.version,
            templateId = template.id,
            language = content.language,
            durationMs = content.durationMs,
            graceMs = content.graceMs,
            optionCount = template.optionCount,
            listeningMaxPlays = config.listeningMaxPlays,
            listeningPlayCountsAfterMs = config.listeningPlayCountsAfterMs,
            maxTextLength = config.maxTextLength,
            sections = sections(template, language),
            groups = content.groups.map { g ->
                ExamPackageGroupDto(
                    id = g.id.toString(),
                    kind = g.kind.db,
                    sectionId = g.section,
                    position = g.position,
                    title = g.title,
                    instructions = g.instructions?.resolve(language),
                    passage = g.passage,
                    audio = g.audio?.let(::media),
                )
            },
            tasks = content.tasks.map { t ->
                ExamPackageTaskDto(
                    id = t.id.toString(),
                    position = t.position,
                    sectionId = t.section,
                    groupId = t.groupId?.toString(),
                    type = t.type.db,
                    answerFormat = t.answerFormat,
                    prompt = t.prompt,
                    options = t.options.map { ExamPackageOptionDto(it.id.toString(), it.letter, it.text) },
                )
            },
            media = content.groups.mapNotNull { it.audio }.distinctBy { it.id }.map(::media),
            packageBytes = packageBytes,
        )

    /** Size shown in the catalogue (EXAM-01): package JSON (az) plus every media file. */
    fun packageBytes(template: ExamTemplate, content: VersionContent): Long {
        val json = ApiJson.encodeToString(ExamPackageResponse.serializer(), packageOf(template, content, "az", 0))
        return json.encodeToByteArray().size.toLong() + content.groups.mapNotNull { it.audio }.distinctBy { it.id }.sumOf { it.bytes.toLong() }
    }
}
