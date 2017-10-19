import parity.JsonRpcClient
import groovy.json.JsonSlurper
import groovyx.gpars.ParallelEnhancer
import org.joda.time.DateTime
import org.joda.time.Period
import org.joda.time.PeriodType
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.ConcurrentHashMap

import static groovyx.gpars.GParsPool.withPool

class EthSendRawTransactionBasic10000SpockTest extends Specification {

    JsonRpcClient jsonRpcClient = new JsonRpcClient("http://localhost:8545/")
    @Shared
    String fileContents = new File('src/test/resources/eth_sendRawTransaction_basic_10000.rpc').getText('UTF-8')

    @Ignore("Useful for development, but not enough for stable results, use Submit 10k Transactions")
    @Unroll("Transactions #from to #to eth_sendRawTransaction over JSON-RPC should return zero #errorResults errors and throughput should be better that #throughputEst TPS")
    def "Submit 100 Transactions"() {
        expect:
        def lines = fileContents.readLines().subList(from, to)
        ConcurrentHashMap errors = new ConcurrentHashMap()
        ConcurrentHashMap results = new ConcurrentHashMap()
        ArrayList<DateTime> blockTimes = new ArrayList<DateTime>()
        ParallelEnhancer.enhanceInstance(lines)
        long start
        long now

        def benchmark = { closure ->
            start = System.currentTimeMillis()
            withPool(clientThreads) {
                lines.each {
                    def json = new JsonSlurper().parseText(it)
                    def result = jsonRpcClient.eth_sendRawTransaction(json.params[0])
                    if (result.error) {
                        errors.put(json.id, result.error)
                    } else {
                        results.put(json.id, result.result)
                    }
                }
            }
            now = System.currentTimeMillis()
            now - start
        }

        def duration = benchmark {
            (0..10000).inject(0) { sum, item ->
                sum + item
            }
        }
        println "Transactions submitted in ${duration} ms with ${errors.size()} errors"

        errors.size() == 0
        results.size() == 100

        def (Period blockPeriodMillis, throughputTps) = getResults(results, blockTimes)

        blockPeriodMillis.seconds <= submitTime
        throughputTps >= throughputEst

        where:
        from | to  | clientThreads | submitTime | throughputEst
        100  | 200 | 3             | 10         | 1
        200  | 300 | 3             | 10         | 1
        300  | 400 | 3             | 10         | 1
        400  | 500 | 3             | 10         | 1
    }

    def "Submit 10k Transactions"() {
        expect:
        def lines = fileContents.readLines().subList(from, to)
        ConcurrentHashMap errors = new ConcurrentHashMap()
        ConcurrentHashMap results = new ConcurrentHashMap()
        ArrayList<DateTime> blockTimes = new ArrayList<DateTime>()
        ParallelEnhancer.enhanceInstance(lines)
        long start
        long now

        def benchmark = { closure ->
            start = System.currentTimeMillis()
            withPool(clientThreads) {
                lines.each {
                    def json = new JsonSlurper().parseText(it)
                    def result = jsonRpcClient.eth_sendRawTransaction(json.params[0])
                    if (result.error) {
                        errors.put(json.id, result.error)
                    } else {
                        results.put(json.id, result.result)
                    }
                }
            }
            now = System.currentTimeMillis()
            now - start
        }

        def duration = benchmark {
            (0..10000).inject(0) { sum, item ->
                sum + item
            }
        }
        println "Transactions submitted in ${duration} ms with ${errors.size()} errors"

        errors.size() == 0

        def (Period blockPeriodMillis, throughputTps) = getResults(results, blockTimes)

        blockPeriodMillis.seconds <= submitTime
        throughputTps >= throughputEst

        where:
        from | to    | clientThreads | submitTime | throughputEst
        0    | 10000 | 3             | 10         | 1
    }

    List getResults(ConcurrentHashMap results, ArrayList<DateTime> blockTimes) {
        results.each {
            def blockHash = jsonRpcClient.eth_getTransactionByHash(it.value).result.blockHash
            def transactionBlockTimeStamp = jsonRpcClient.eth_getBlockByHash(blockHash, true).result.timestamp
            blockTimes.add(new DateTime((new BigInteger(transactionBlockTimeStamp.substring(2), 16) as long) * 1000L))
        }

        Collections.sort(blockTimes)
        Period blockPeriodMillis = new Period(blockTimes.first(), blockTimes.last(), PeriodType.millis());
        Period blockPeriodSecs = new Period(blockTimes.first(), blockTimes.last(), PeriodType.seconds());
        def throughputTps = (to - from) / blockPeriodSecs.seconds

        println "first block time ${blockTimes.first()}, last block time ${blockTimes.last()} \n"
        println "time to process all blocks ${blockPeriodSecs.seconds} s \n"
        println "Average throughput ${throughputTps} TPS \n"
        [blockPeriodMillis, throughputTps]
    }
}

