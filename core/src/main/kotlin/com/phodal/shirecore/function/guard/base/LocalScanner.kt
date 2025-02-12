package com.phodal.shirecore.function.guard.base

/**
 * GuardScanner is an interface for scanning user input for security vulnerabilities.
 */
interface GuardScanner {
    fun scan(prompt: String): ScanResult
}

interface Masker {
    fun mask(prompt: String): String
}

interface LocalScanner : GuardScanner

abstract class EntityRecognizer {
    abstract fun load()

    abstract fun analyze(text: String, entities: List<String>): List<ScanResult>
}
