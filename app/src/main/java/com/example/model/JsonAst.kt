package com.example.model

enum class JsonRootType(val label: String) {
  OBJECT("Object {}"),
  ARRAY("Array []"),
  PRIMITIVE("Primitive"),
  INVALID("Invalid JSON"),
}

enum class JsonNodeKind(val badge: String) {
  OBJECT("OBJ"),
  ARRAY("ARR"),
  STRING("STR"),
  NUMBER("NUM"),
  BOOLEAN("BOOL"),
  NULL("NULL"),
}

sealed class JsonNode {
  abstract val path: String
  abstract val keyName: String
  abstract val depth: Int
  abstract val kind: JsonNodeKind

  data class ObjectNode(
    override val path: String,
    override val keyName: String,
    override val depth: Int,
    val children: List<JsonNode>,
  ) : JsonNode() {
    override val kind: JsonNodeKind = JsonNodeKind.OBJECT
  }

  data class ArrayNode(
    override val path: String,
    override val keyName: String,
    override val depth: Int,
    val children: List<JsonNode>,
  ) : JsonNode() {
    override val kind: JsonNodeKind = JsonNodeKind.ARRAY
  }

  data class StringNode(
    override val path: String,
    override val keyName: String,
    override val depth: Int,
    val value: String,
  ) : JsonNode() {
    override val kind: JsonNodeKind = JsonNodeKind.STRING
  }

  data class NumberNode(
    override val path: String,
    override val keyName: String,
    override val depth: Int,
    val value: String,
  ) : JsonNode() {
    override val kind: JsonNodeKind = JsonNodeKind.NUMBER
  }

  data class BooleanNode(
    override val path: String,
    override val keyName: String,
    override val depth: Int,
    val value: Boolean,
  ) : JsonNode() {
    override val kind: JsonNodeKind = JsonNodeKind.BOOLEAN
  }

  data class NullNode(
    override val path: String,
    override val keyName: String,
    override val depth: Int,
  ) : JsonNode() {
    override val kind: JsonNodeKind = JsonNodeKind.NULL
  }
}

data class JsonStats(
  val rootType: JsonRootType,
  val byteSize: Int,
  val totalKeys: Int,
  val maxDepth: Int,
  val objectCount: Int,
  val arrayCount: Int,
  val primitiveCount: Int,
  val isEmptyContainer: Boolean,
)

data class AccessRecord(
  val index: Int,
  val sourceCollection: String,
  val title: String,
  val subtitle: String?,
  val timestamp: String?,
  val statusOrAction: String?,
  val userId: String? = null,
  val actionName: String? = null,
  val fields: List<Pair<String, String>>,
)

data class ParsedJsonDocument(
  val fileName: String,
  val isBundledAttachment: Boolean,
  val rawText: String,
  val prettyText: String,
  val compactText: String,
  val rootNode: JsonNode?,
  val stats: JsonStats,
  val records: List<AccessRecord>,
  val topLevelKeys: List<Pair<String, JsonNodeKind>>,
  val parseError: String? = null,
)
