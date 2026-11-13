package com.example.model

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

object JsonInspectorParser {

  private val titleKeys =
    listOf("title", "name", "appletName", "applet_name", "app", "appletId", "applet_id", "path", "url", "endpoint", "id")
  private val subtitleKeys =
    listOf("user", "email", "userEmail", "user_email", "actor", "ip", "device", "description", "message", "resource")
  private val userIdKeys =
    listOf("userId", "user_id", "uid", "actorId", "actor_id", "accountId", "account_id", "user", "userEmail", "user_email", "email", "actor")
  private val timeKeys =
    listOf("timestamp", "accessedAt", "accessed_at", "createdAt", "created_at", "updatedAt", "updated_at", "time", "date", "lastAccessed")
  private val actionKeys =
    listOf("action", "actionName", "action_name", "event", "eventName", "event_name", "operation", "method", "status", "type", "outcome", "role", "level")

  fun parse(
    fileName: String,
    rawText: String,
    isBundledAttachment: Boolean = false,
  ): ParsedJsonDocument {
    val trimmed = rawText.trim()
    val byteSize = rawText.toByteArray(Charsets.UTF_8).size

    if (trimmed.isEmpty()) {
      return ParsedJsonDocument(
        fileName = fileName,
        isBundledAttachment = isBundledAttachment,
        rawText = rawText,
        prettyText = "",
        compactText = "",
        rootNode = null,
        stats = JsonStats(JsonRootType.INVALID, byteSize, 0, 0, 0, 0, 0, isEmptyContainer = true),
        records = emptyList(),
        topLevelKeys = emptyList(),
        parseError = "File is empty (0 bytes of JSON content).",
      )
    }

    return try {
      val tokener = JSONTokener(trimmed)
      val firstValue = tokener.nextValue()
      val trailing = tokener.nextClean()
      val parsed =
        if (trailing == '\u0000') {
          firstValue
        } else {
          parseJsonLines(trimmed)
            ?: throw JSONException("Unexpected trailing character '$trailing' after root JSON value")
        }

      val rootNode = buildNode(parsed, path = "$", keyName = "$", depth = 0)
      val stats = computeStats(rootNode, byteSize)
      val prettyText = formatValue(parsed, indent = 2)
      val compactText = formatValue(parsed, indent = null)
      val records = extractRecords(rootNode)
      val topLevelKeys =
        when (rootNode) {
          is JsonNode.ObjectNode -> rootNode.children.map { it.keyName to it.kind }
          is JsonNode.ArrayNode -> rootNode.children.map { it.keyName to it.kind }
          else -> emptyList()
        }

      ParsedJsonDocument(
        fileName = fileName,
        isBundledAttachment = isBundledAttachment,
        rawText = rawText,
        prettyText = prettyText,
        compactText = compactText,
        rootNode = rootNode,
        stats = stats,
        records = records,
        topLevelKeys = topLevelKeys,
        parseError = null,
      )
    } catch (e: JSONException) {
      ParsedJsonDocument(
        fileName = fileName,
        isBundledAttachment = isBundledAttachment,
        rawText = rawText,
        prettyText = rawText,
        compactText = rawText,
        rootNode = null,
        stats = JsonStats(JsonRootType.INVALID, byteSize, 0, 0, 0, 0, 0, isEmptyContainer = false),
        records = emptyList(),
        topLevelKeys = emptyList(),
        parseError = e.message ?: "Invalid JSON syntax",
      )
    }
  }

  private fun buildNode(value: Any?, path: String, keyName: String, depth: Int): JsonNode {
    return when (value) {
      is JSONObject -> {
        val keys = mutableListOf<String>()
        val iter = value.keys()
        while (iter.hasNext()) keys.add(iter.next())
        val children =
          keys.map { childKey ->
            val childPath = "$path.$childKey"
            buildNode(value.opt(childKey), childPath, childKey, depth + 1)
          }
        JsonNode.ObjectNode(path = path, keyName = keyName, depth = depth, children = children)
      }
      is JSONArray -> {
        val children =
          (0 until value.length()).map { idx ->
            val childPath = "$path[$idx]"
            buildNode(value.opt(idx), childPath, "[$idx]", depth + 1)
          }
        JsonNode.ArrayNode(path = path, keyName = keyName, depth = depth, children = children)
      }
      null, JSONObject.NULL -> JsonNode.NullNode(path = path, keyName = keyName, depth = depth)
      is Boolean -> JsonNode.BooleanNode(path = path, keyName = keyName, depth = depth, value = value)
      is Number -> JsonNode.NumberNode(path = path, keyName = keyName, depth = depth, value = value.toString())
      else -> JsonNode.StringNode(path = path, keyName = keyName, depth = depth, value = value.toString())
    }
  }

  private fun computeStats(root: JsonNode, byteSize: Int): JsonStats {
    var totalKeys = 0
    var maxDepth = 0
    var objectCount = 0
    var arrayCount = 0
    var primitiveCount = 0

    fun walk(node: JsonNode) {
      if (node.depth > maxDepth) maxDepth = node.depth
      when (node) {
        is JsonNode.ObjectNode -> {
          objectCount++
          totalKeys += node.children.size
          node.children.forEach(::walk)
        }
        is JsonNode.ArrayNode -> {
          arrayCount++
          node.children.forEach(::walk)
        }
        else -> primitiveCount++
      }
    }

    walk(root)

    val rootType =
      when (root) {
        is JsonNode.ObjectNode -> JsonRootType.OBJECT
        is JsonNode.ArrayNode -> JsonRootType.ARRAY
        else -> JsonRootType.PRIMITIVE
      }

    val isEmptyContainer =
      when (root) {
        is JsonNode.ObjectNode -> root.children.isEmpty()
        is JsonNode.ArrayNode -> root.children.isEmpty()
        else -> false
      }

    return JsonStats(
      rootType = rootType,
      byteSize = byteSize,
      totalKeys = totalKeys,
      maxDepth = maxDepth,
      objectCount = objectCount,
      arrayCount = arrayCount,
      primitiveCount = primitiveCount,
      isEmptyContainer = isEmptyContainer,
    )
  }

  private fun formatValue(value: Any?, indent: Int?): String {
    return when (value) {
      is JSONObject -> if (indent != null) value.toString(indent) else value.toString()
      is JSONArray -> if (indent != null) value.toString(indent) else value.toString()
      null, JSONObject.NULL -> "null"
      is String -> JSONObject.quote(value)
      else -> value.toString()
    }
  }

  private fun extractRecords(root: JsonNode): List<AccessRecord> {
    val results = mutableListOf<AccessRecord>()

    fun collectFromArray(arrayNode: JsonNode.ArrayNode, collectionName: String) {
      arrayNode.children.forEachIndexed { idx, child ->
        if (child is JsonNode.ObjectNode && child.children.isNotEmpty()) {
          results.add(objectToRecord(index = idx + 1, collection = collectionName, obj = child))
        }
      }
    }

    when (root) {
      is JsonNode.ArrayNode -> collectFromArray(root, "root")
      is JsonNode.ObjectNode -> {
        // Check arrays directly under root or one level down
        root.children.forEach { child ->
          if (child is JsonNode.ArrayNode) {
            collectFromArray(child, child.keyName)
          } else if (child is JsonNode.ObjectNode) {
            child.children.forEach { nested ->
              if (nested is JsonNode.ArrayNode) {
                collectFromArray(nested, "${child.keyName}.${nested.keyName}")
              }
            }
          }
        }
        // Fallback: if no array of objects was found, treat object-valued top-level entries as records
        if (results.isEmpty()) {
          val objectChildren = root.children.filterIsInstance<JsonNode.ObjectNode>()
          if (objectChildren.isNotEmpty()) {
            objectChildren.forEachIndexed { idx, child ->
              results.add(objectToRecord(index = idx + 1, collection = child.keyName, obj = child))
            }
          }
        }
      }
      else -> Unit
    }

    return results
  }

  private fun objectToRecord(index: Int, collection: String, obj: JsonNode.ObjectNode): AccessRecord {
    val scalarFields =
      obj.children.map { child ->
        val displayValue =
          when (child) {
            is JsonNode.StringNode -> child.value
            is JsonNode.NumberNode -> child.value
            is JsonNode.BooleanNode -> child.value.toString()
            is JsonNode.NullNode -> "null"
            is JsonNode.ObjectNode -> "{${child.children.size} keys}"
            is JsonNode.ArrayNode -> "[${child.children.size} items]"
          }
        child.keyName to displayValue
      }

    fun pickFirst(candidates: List<String>): String? {
      for (candidate in candidates) {
        val match =
          scalarFields.firstOrNull { (k, v) ->
            k.equals(candidate, ignoreCase = true) && v.isNotBlank() && v != "null"
          }
        if (match != null) return match.second
      }
      return null
    }

    val title =
      pickFirst(titleKeys)
        ?: scalarFields.firstOrNull { (_, v) -> v.isNotBlank() }?.let { "${it.first}: ${it.second}" }
        ?: "Entry #$index"
    val subtitle = pickFirst(subtitleKeys)
    val userId = pickFirst(userIdKeys)
    val timestamp = pickFirst(timeKeys)
    val actionName = pickFirst(actionKeys)

    return AccessRecord(
      index = index,
      sourceCollection = collection,
      title = title,
      subtitle = subtitle,
      timestamp = timestamp,
      statusOrAction = actionName,
      userId = userId,
      actionName = actionName,
      fields = scalarFields,
    )
  }

  private fun parseJsonLines(text: String): JSONArray? {
    val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    if (lines.size < 2) return null
    val array = JSONArray()
    return try {
      for (line in lines) {
        val tokener = JSONTokener(line)
        val value = tokener.nextValue()
        if (tokener.nextClean() != '\u0000') return null
        array.put(value)
      }
      array
    } catch (_: JSONException) {
      null
    }
  }
}
