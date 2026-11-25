package com.example.domain

import com.example.data.local.CardEntity

internal object PlaytestMana {
    data class Source(val id: Long, val choices: List<List<String>>)
    data class Payment(val pool: List<String>, val sources: Set<Long>)
    data class Cost(val generic: Int, val symbols: List<Set<String>>)

    private val colors = listOf("W", "U", "B", "R", "G", "C")
    private val basicTypes = mapOf("Plains" to "W", "Island" to "U", "Swamp" to "B", "Mountain" to "R", "Forest" to "G")
    private val token = Regex("""\{([^{}]+)\}""")
    private val manaLine = Regex("""\{T\}: Add (.+)\.""", RegexOption.IGNORE_CASE)

    fun isLand(card: CardEntity): Boolean = cardType(card, "Land")

    fun isCreature(card: CardEntity): Boolean = cardType(card, "Creature")

    fun entersTapped(card: CardEntity): Boolean {
        val name = Regex.escape(card.name.substringBefore(" // "))
        val selfEntersTapped = Regex(
            """^(?:this [a-z]+|$name) enters(?: the battlefield)? tapped\b""",
            RegexOption.IGNORE_CASE
        )
        return card.oracleText.substringBefore("\n//\n").lineSequence().any {
            selfEntersTapped.containsMatchIn(it.trim())
        }
    }

    fun sourceChoices(card: CardEntity, commanderColors: Set<String> = emptySet()): List<List<String>> {
        val choices = card.oracleText.lineSequence().flatMap { line ->
            val text = manaLine.matchEntire(line.trim().removeSurrounding("(", ")"))?.groupValues?.get(1)
                ?: return@flatMap emptySequence()
            if (text.equals("one mana of any color", ignoreCase = true)) {
                colors.take(5).asSequence().map { listOf(it) }
            } else if (text.equals("one mana of any color in your commander's color identity", ignoreCase = true)) {
                colors.take(5).filter(commanderColors::contains).asSequence().map { listOf(it) }
            } else {
                text.split(Regex("""\s*,?\s+or\s+|,\s*""", RegexOption.IGNORE_CASE)).asSequence().mapNotNull { option ->
                    val symbols = parseTokens(option) ?: return@mapNotNull null
                    symbols.takeIf { it.all(colors::contains) }
                }
            }
        }.toMutableList()
        if (isLand(card)) {
            val subtypes = card.typeLine.substringBefore(" // ").substringAfter("—", "").trim().split(' ')
            choices += subtypes.mapNotNull { basicTypes[it]?.let(::listOf) }
        }
        return choices.distinct()
    }

    fun cost(manaCost: String, tax: Int = 0): Cost? {
        val symbols = parseTokens(manaCost) ?: return null
        var generic = tax
        val colored = mutableListOf<Set<String>>()
        for (symbol in symbols) {
            val number = symbol.toIntOrNull()
            if (number != null && number in 0..100) {
                generic += number
            } else {
                val options = symbol.split('/').toSet()
                if (options.isEmpty() || !options.all(colors::contains)) return null
                colored += options
            }
        }
        return Cost(generic, colored.sortedBy(Set<String>::size))
    }

    fun pay(cost: Cost, pool: List<String>, sources: List<Source>): Payment? {
        if (pool.size + sources.sumOf { it.choices.maxOf(List<String>::size) } < cost.generic + cost.symbols.size) return null

        fun resolve(
            index: Int,
            remaining: List<String>,
            available: List<Source>,
            tapped: Set<Long>
        ): Payment? {
            if (index == cost.symbols.size) {
                val finalPool = remaining.toMutableList()
                val used = tapped.toMutableSet()
                for (source in available.sortedBy { it.choices.size }) {
                    if (finalPool.size >= cost.generic) break
                    val output = source.choices.maxBy(List<String>::size)
                    finalPool += output
                    used += source.id
                }
                return if (finalPool.size >= cost.generic) Payment(finalPool.drop(cost.generic), used) else null
            }

            val required = cost.symbols[index]
            for (color in remaining.distinct().filter(required::contains)) {
                resolve(index + 1, remaining - color, available, tapped)?.let { return it }
            }
            for (source in available.distinctBy(Source::choices)) {
                for (choice in source.choices) {
                    for (color in choice.distinct().filter(required::contains)) {
                        resolve(index + 1, remaining + (choice - color), available - source, tapped + source.id)
                            ?.let { return it }
                    }
                }
            }
            return null
        }

        return resolve(0, pool, sources, emptySet())
    }

    private fun cardType(card: CardEntity, type: String): Boolean =
        type in card.typeLine.substringBefore(" // ").substringBefore('—').trim().split(' ')

    private fun parseTokens(value: String): List<String>? {
        val text = value.trim()
        val matches = token.findAll(text).toList()
        if (matches.isEmpty() || matches.joinToString("") { it.value } != text) return null
        return matches.map { it.groupValues[1].uppercase() }
    }
}
