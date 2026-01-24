package dev.openrune.central.storage.mongo

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import org.bson.Document
import dev.openrune.central.config.MongoStorageConfig
import dev.openrune.central.storage.JsonBucket
import dev.openrune.central.storage.JsonStorage
import dev.openrune.central.storage.StorageNaming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MongoJsonStorage(private val cfg: MongoStorageConfig) : JsonStorage {
    private val client: MongoClient = MongoClients.create(cfg.connectionString)
    private val db = client.getDatabase(cfg.database)

    override suspend fun upsert(bucket: JsonBucket, id: String, json: String) {
        val col = recordsCollection(bucket)
        val doc = Document.parse(json)
        doc["_id"] = id
        withContext(Dispatchers.IO) {
            col.replaceOne(Filters.eq("_id", id), doc, ReplaceOptions().upsert(true))
        }
    }

    override suspend fun get(bucket: JsonBucket, id: String): String? {
        val col = recordsCollection(bucket)
        return withContext(Dispatchers.IO) {
            val doc = col.find(Filters.eq("_id", id)).first() ?: return@withContext null
            doc.toJson()
        }
    }

    override suspend fun append(bucket: JsonBucket, json: String) {
        val col = logsCollection(bucket)
        val doc = Document.parse(json)
        withContext(Dispatchers.IO) {
            col.insertOne(doc)
        }
    }

    override suspend fun append(bucket: JsonBucket, partition: String, json: String) {
        val col = partitionedLogsCollection(bucket, partition)
        val doc = Document.parse(json)
        withContext(Dispatchers.IO) {
            col.insertOne(doc)
        }
    }

    override fun close() {
        client.close()
    }

    private fun recordsCollection(bucket: JsonBucket): MongoCollection<Document> =
        db.getCollection(collectionNameFor(bucket, records = true))

    private fun logsCollection(bucket: JsonBucket): MongoCollection<Document> =
        db.getCollection(collectionNameFor(bucket, records = false))

    private fun partitionedLogsCollection(bucket: JsonBucket, partition: String): MongoCollection<Document> =
        db.getCollection(partitionedLogCollectionName(bucket, partition))

    private fun collectionNameFor(bucket: JsonBucket, records: Boolean): String {
        val base = StorageNaming.bucketSnake(bucket)
        return if (records) base else "${base}_logs"
    }

    private fun partitionedLogCollectionName(bucket: JsonBucket, partition: String): String {
        val part = StorageNaming.toSnakeCase(partition)
        val base =
            when (bucket) {
                JsonBucket.LOGS -> StorageNaming.logPartitionName(partition)
                else -> StorageNaming.bucketSnake(bucket) + "_" + part + "_logs"
            }
        return base
    }
}

