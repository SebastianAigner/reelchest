package io.sebi.tagging

import java.io.File

object AutoTagger : Tagger { // todo ew

    fun getUserRulesFiles(): Collection<File> {
        return File("userConfig").listFiles { f ->
            f.nameWithoutExtension.endsWith("tags")
        }?.toList() ?: emptyList()
    }

    class Rule(from: List<String>, to: List<String>) {
        val from = from.map(String::lowercase)
        val to = to.map(String::lowercase)

        data class NormalizedRule(val from: String, val to: List<String>)

        fun decompose(): List<NormalizedRule> {
            return from.map { NormalizedRule(it, to) }
        }
    }

    fun splitRule(rule: String): Rule {
        val (replaceFrom, replaceWith) = rule.split("->")

        fun String.cleanupText() = this.split(",").map { it.trim() }

        val indivRepFrom = replaceFrom.cleanupText()
        val indivRepTo = replaceWith.cleanupText()
        return Rule(indivRepFrom, indivRepTo)
    }

    fun Set<String>.toNormalizedRule(): List<Rule.NormalizedRule> {
        return this.map {
            Rule.NormalizedRule(it, listOf(it))
        }
    }

    val userRules = getUserRulesFiles().flatMap { it.readLines() }.map(::splitRule).flatMap(Rule::decompose)

    val optimizedRules = userRules.groupBy { it.from }.map { (k, v) ->
        Rule.NormalizedRule(k, v.flatMap { it.to })
    }

    override fun tag(name: String, tags: Set<String>): Set<String> {
        val name = name.lowercase()
        return buildList {
            for (rule in optimizedRules) {
                if (name.contains(rule.from) || tags.contains(rule.from)) {
                    addAll(rule.to)
                }
            }
        }.toSet()
    }
}