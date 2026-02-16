package io.sebi.conversion

val DEFAULT_CONVERSION_OPTIONS = ConversionOptions(
    maxWidth = 1920,
    maxHeight = 1080,
    fps = 30,
    crf = 21,
    audioBitrateKbps = 160,
)

val DEFAULT_CONVERSION_MODE = ConversionMode.IMMEDIATE

const val DEFAULT_CONVERT_AND_DELETE_ORIGINAL = true
