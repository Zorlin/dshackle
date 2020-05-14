/**
 * Copyright (c) 2020 EmeraldPay, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.emeraldpay.dshackle.upstream.ethereum

import io.emeraldpay.dshackle.cache.BlocksMemCache
import io.emeraldpay.dshackle.cache.Caches
import io.emeraldpay.dshackle.cache.HeightCache
import io.emeraldpay.dshackle.cache.TxMemCache
import io.emeraldpay.dshackle.data.BlockContainer
import io.emeraldpay.dshackle.data.BlockId
import io.emeraldpay.dshackle.data.TxContainer
import io.emeraldpay.dshackle.data.TxId
import io.emeraldpay.dshackle.test.EthereumUpstreamMock
import io.emeraldpay.dshackle.test.TestingCommons
import io.emeraldpay.dshackle.test.UpstreamsMock
import io.emeraldpay.dshackle.upstream.AggregatedUpstream
import io.emeraldpay.dshackle.upstream.Head
import io.emeraldpay.dshackle.upstream.Upstream
import io.emeraldpay.grpc.Chain
import io.infinitape.etherjar.domain.Address
import io.infinitape.etherjar.domain.BlockHash
import io.infinitape.etherjar.domain.TransactionId
import io.infinitape.etherjar.domain.Wei
import io.infinitape.etherjar.rpc.ReactorRpcClient
import io.infinitape.etherjar.rpc.RpcException
import io.infinitape.etherjar.rpc.RpcResponseError
import io.infinitape.etherjar.rpc.json.BlockJson
import io.infinitape.etherjar.rpc.json.TransactionJson
import io.infinitape.etherjar.rpc.json.TransactionRefJson
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Instant
import java.time.temporal.ChronoUnit

class EthereumReaderSpec extends Specification {

    def blockId = BlockId.from("f85b826fdf98ee0f48f7db001be00472e63ceb056846f4ecac5f0c32878b8ab2")
    def blockJson = new BlockJson<TransactionRefJson>().tap { blockJson ->
        blockJson.hash = BlockHash.from(blockId.value)
        blockJson.totalDifficulty = BigInteger.ONE
        blockJson.number = 101
        blockJson.timestamp = Instant.ofEpochSecond(100000000)
        blockJson.transactions = []
        blockJson.uncles = []
    }
    def txId = BlockId.from("a38e7b4d456777c94b46c61a1e4cf52fbdd92acc4444719d1fad77005698c221")
    def txJson = new TransactionJson().tap { json ->
        json.hash = TransactionId.from(txId.value)
        json.blockHash = blockJson.hash
        json.blockNumber = blockJson.number
    }

    def "Block by Id reads from cache"() {
        setup:
        def memCache = Mock(BlocksMemCache) {
            1 * read(blockId) >> Mono.just(BlockContainer.from(blockJson, TestingCommons.objectMapper()))
        }
        def caches = Caches.newBuilder()
                .setBlockByHash(memCache)
                .setObjectMapper(TestingCommons.objectMapper())
                .build()
        def reader = new EthereumReader(Stub(AggregatedUpstream), caches, TestingCommons.objectMapper())

        when:
        def act = reader.blocksById().read(blockId).block()

        then:
        act == blockJson
    }

    def "Block by Id reads from api if cache is empty"() {
        setup:
        def memCache = Mock(BlocksMemCache) {
            1 * read(blockId) >> Mono.empty()
        }
        def caches = Caches.newBuilder()
                .setBlockByHash(memCache)
                .setObjectMapper(TestingCommons.objectMapper())
                .build()
        def api = TestingCommons.api()
        api.answer("eth_getBlockByHash", ["0xf85b826fdf98ee0f48f7db001be00472e63ceb056846f4ecac5f0c32878b8ab2", false], blockJson)

        def upstream = TestingCommons.aggregatedUpstream(api)
        def reader = new EthereumReader(upstream, caches, TestingCommons.objectMapper())

        when:
        def act = reader.blocksById().read(blockId).block()

        then:
        act == blockJson
    }

    def "Block by Id reads from api if cache failed"() {
        setup:
        def memCache = Mock(BlocksMemCache) {
            1 * read(blockId) >> Mono.error(new IllegalStateException("Test error"))
        }
        def caches = Caches.newBuilder()
                .setBlockByHash(memCache)
                .setObjectMapper(TestingCommons.objectMapper())
                .build()
        def api = TestingCommons.api()
        api.answer("eth_getBlockByHash", ["0xf85b826fdf98ee0f48f7db001be00472e63ceb056846f4ecac5f0c32878b8ab2", false], blockJson)

        def upstream = TestingCommons.aggregatedUpstream(api)
        def reader = new EthereumReader(upstream, caches, TestingCommons.objectMapper())

        when:
        def act = reader.blocksById().read(blockId).block()

        then:
        act == blockJson
    }

    def "Block by Hash reads from cache"() {
        setup:
        def memCache = Mock(BlocksMemCache) {
            1 * read(blockId) >> Mono.just(BlockContainer.from(blockJson, TestingCommons.objectMapper()))
        }
        def caches = Caches.newBuilder()
                .setBlockByHash(memCache)
                .setObjectMapper(TestingCommons.objectMapper())
                .build()
        def reader = new EthereumReader(Stub(AggregatedUpstream), caches, TestingCommons.objectMapper())

        when:
        def act = reader.blocksByHash().read(blockJson.hash).block()

        then:
        act == blockJson
    }

    def "Block by Hash reads from api if cache is empty"() {
        setup:
        def memCache = Mock(BlocksMemCache) {
            1 * read(blockId) >> Mono.empty()
        }
        def caches = Caches.newBuilder()
                .setBlockByHash(memCache)
                .setObjectMapper(TestingCommons.objectMapper())
                .build()
        def api = TestingCommons.api()
        api.answer("eth_getBlockByHash", ["0xf85b826fdf98ee0f48f7db001be00472e63ceb056846f4ecac5f0c32878b8ab2", false], blockJson)
        def upstream = TestingCommons.aggregatedUpstream(api)
        def reader = new EthereumReader(upstream, caches, TestingCommons.objectMapper())

        when:
        def act = reader.blocksByHash().read(blockJson.hash).block()

        then:
        act == blockJson
    }

    def "Tx by Hash reads from cache"() {
        setup:
        def memCache = Mock(TxMemCache) {
            1 * read(txId) >> Mono.just(TxContainer.from(txJson, TestingCommons.objectMapper()))
        }
        def caches = Caches.newBuilder()
                .setTxByHash(memCache)
                .setObjectMapper(TestingCommons.objectMapper())
                .build()
        def reader = new EthereumReader(Stub(AggregatedUpstream), caches, TestingCommons.objectMapper())

        when:
        def act = reader.txByHash().read(txJson.hash).block()

        then:
        act == txJson
    }

    def "Tx by Hash reads from api if cache is empty"() {
        setup:
        def memCache = Mock(TxMemCache) {
            1 * read(txId) >> Mono.empty()
        }
        def caches = Caches.newBuilder()
                .setTxByHash(memCache)
                .setObjectMapper(TestingCommons.objectMapper())
                .build()

        def api = TestingCommons.api()
        api.answer("eth_getTransactionByHash", [txJson.hash.toHex()], txJson)
        def upstream = TestingCommons.aggregatedUpstream(api)
        def reader = new EthereumReader(upstream, caches, TestingCommons.objectMapper())

        when:
        def act = reader.txByHash().read(txJson.hash).block()

        then:
        act == txJson
    }

    def "Caches balance until block mined"() {
        setup:
        def api = TestingCommons.api()
        api.answerOnce("eth_getBalance", ["0x70b91ff87a902b53dc6e2f6bda8bb9b330ccd30c", "latest"], "0x10")
        api.answerOnce("eth_getBalance", ["0x70b91ff87a902b53dc6e2f6bda8bb9b330ccd30c", "latest"], "0xff")
        EthereumUpstreamMock upstream = new EthereumUpstreamMock(Chain.ETHEREUM, api)
        def upstreams = TestingCommons.aggregatedUpstream(upstream)
        def reader = new EthereumReader(upstreams, Caches.default(TestingCommons.objectMapper()), TestingCommons.objectMapper())
        reader.start()

        when:
        def act = reader.balance().read(Address.from("0x70b91ff87a902b53dc6e2f6bda8bb9b330ccd30c")).block()

        then:
        act == Wei.from("0x10")

        when:
        //now it should use cached value, without actual request
        act = reader.balance().read(Address.from("0x70b91ff87a902b53dc6e2f6bda8bb9b330ccd30c")).block()

        then:
        act == Wei.from("0x10")

        when:
        //move head forward, which should erase cache
        def block2 = blockJson.copy().tap {
            it.number++
            it.totalDifficulty = BigInteger.TWO
        }
        upstream.nextBlock(BlockContainer.from(block2, TestingCommons.objectMapper()))
        act = reader.balance().read(Address.from("0x70b91ff87a902b53dc6e2f6bda8bb9b330ccd30c")).block()

        then:
        act == Wei.from("0xff")
    }
}
