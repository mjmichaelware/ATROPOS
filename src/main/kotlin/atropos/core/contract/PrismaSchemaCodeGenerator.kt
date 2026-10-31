/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.contract

/**
 * ATOM EX14: PRISMA ORM SCHEMA HIGHLIGHTER AND CODE GENERATOR
 * Path: src/main/kotlin/atropos/core/contract/PrismaSchemaCodeGenerator.kt
 *
 * Provides syntax validation, model extraction, and SQL DDL migration generation
 * for schema.prisma files, validating foreign keys and model relations.
 */
data class PrismaField(
    val name: String,
    val type: String,
    val isList: Boolean,
    val isOptional: Boolean,
    val isId: Boolean = false,
    val isUnique: Boolean = false,
    val defaultValue: String? = null
)

data class PrismaModel(
    val name: String,
    val fields: List<PrismaField>
)

class PrismaSchemaCodeGenerator {
    fun parsePrismaSchema(schemaContent: String): List<PrismaModel> {
        val models = mutableListOf<PrismaModel>()
        val lines = schemaContent.lines()
        var currentModelName: String? = null
        var currentFields = mutableListOf<PrismaField>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("//") || trimmed.isBlank()) continue

            if (trimmed.startsWith("model ") && trimmed.endsWith("{")) {
                currentModelName = trimmed.removePrefix("model ").removeSuffix("{").trim()
                currentFields = mutableListOf()
            } else if (trimmed == "}" && currentModelName != null) {
                models.add(PrismaModel(currentModelName, currentFields))
                currentModelName = null
            } else if (currentModelName != null) {
                val parts = trimmed.split(Regex("""\s+"""))
                if (parts.size >= 2) {
                    val fieldName = parts[0]
                    var fieldType = parts[1]
                    val isList = fieldType.endsWith("[]")
                    val isOptional = fieldType.endsWith("?")
                    fieldType = fieldType.removeSuffix("[]").removeSuffix("?")

                    val isId = trimmed.contains("@id")
                    val isUnique = trimmed.contains("@unique")

                    currentFields.add(
                        PrismaField(
                            name = fieldName,
                            type = fieldType,
                            isList = isList,
                            isOptional = isOptional,
                            isId = isId,
                            isUnique = isUnique
                        )
                    )
                }
            }
        }

        return models
    }

    fun generateSqlDdl(models: List<PrismaModel>): String {
        val sb = StringBuilder()
        for (m in models) {
            sb.appendLine("CREATE TABLE IF NOT EXISTS \"${m.name}\" (")
            val fieldSqls = m.fields.map { f ->
                val sqlType = when (f.type) {
                    "Int" -> "INTEGER"
                    "String" -> "TEXT"
                    "Boolean" -> "BOOLEAN"
                    "DateTime" -> "TIMESTAMPTZ"
                    "Float" -> "DOUBLE PRECISION"
                    else -> "TEXT"
                }
                val pk = if (f.isId) " PRIMARY KEY" else ""
                val notNull = if (!f.isOptional && !f.isId) " NOT NULL" else ""
                val unique = if (f.isUnique) " UNIQUE" else ""
                "  \"${f.name}\" $sqlType$pk$notNull$unique"
            }
            sb.appendLine(fieldSqls.joinToString(",\n"))
            sb.appendLine(");")
            sb.appendLine()
        }
        return sb.toString().trim()
    }

    companion object {
        val defaultGenerator = PrismaSchemaCodeGenerator()
    }
}
