package io.emeraldpay.dshackle.upstream.bitcoin

import com.fasterxml.jackson.databind.ObjectMapper
import io.emeraldpay.dshackle.data.BlockContainer
import io.emeraldpay.dshackle.data.BlockId
import io.emeraldpay.dshackle.data.TxId
import org.apache.commons.codec.binary.Hex
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.time.Instant

class ExtractBlock(
        private val objectMapper: ObjectMapper
) {

    companion object {
        private val log = LoggerFactory.getLogger(ExtractBlock::class.java)

        @JvmStatic
        fun getHeight(data: Map<String, Any>): Long? {
            val height = data["height"] as Number? ?: return null
            return height.toLong()
        }

        @JvmStatic
        fun getTime(data: Map<String, Any>): Instant? {
            val time = data["time"] as Number? ?: return null
            return Instant.ofEpochMilli(time.toLong() * 1000)
        }

        @JvmStatic
        fun getDifficulty(data: Map<String, Any>): BigInteger? {
            val chainwork = data["chainwork"] as String? ?: return null
            return BigInteger(1, Hex.decodeHex(chainwork))
        }
    }

    fun extract(json: ByteArray): BlockContainer {
        val data = objectMapper.readValue(json, Map::class.java) as Map<String, Any>

        val hash = data["hash"] as String? ?: throw IllegalArgumentException("Block JSON has no hash")
        val transactions = (data["tx"] as List<String>?)?.map(TxId.Companion::from) ?: emptyList()

        return BlockContainer(
                getHeight(data) ?: throw IllegalArgumentException("Block JSON has no height"),
                BlockId.from(hash),
                getDifficulty(data) ?: throw IllegalArgumentException("Block JSON has no chainwork"),
                getTime(data) ?: throw IllegalArgumentException("Block JSON has no time"),
                false,
                json,
                transactions
        )
    }


}