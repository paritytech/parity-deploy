import parity.JsonRpcClient
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovyx.gpars.ParallelEnhancer
import org.cliffc.high_scale_lib.NonBlockingHashMap
import org.glassfish.tyrus.client.ClientManager
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static groovyx.gpars.GParsPool.withPool

class EthSendRawTransactionBasic10000DumpBlocksSpockTest extends Specification {

    public static final String parityUrl = "http://localhost:8545/"
    public static final String DEFAULT_ACCOUNT = '0x00a329c0648769a73afac7f9381e08fb43dbea72'
    @Shared
    String fileContents = new File('src/test/resources/eth_sendRawTransaction_basic_10000.rpc').getText('UTF-8')
    @Shared
    ClientManager manager = ClientManager.createClient()
    @Shared
    String localParity = "ws://localhost:8546"
    @Shared
    ArrayList<String> account = [DEFAULT_ACCOUNT]

    @Unroll("Subscription to #method should not throw error")
    def "test eth_subscribe"() {
        setup:
        def lines = fileContents.readLines().subList(from, to)
        NonBlockingHashMap errors = new NonBlockingHashMap()
        NonBlockingHashMap results = new NonBlockingHashMap()
        ParallelEnhancer.enhanceInstance(lines)
        long start
        long now

        when:
        def benchmark = { closure ->
            start = System.currentTimeMillis()
            withPool(clientThreads) {
                lines.eachParallel {
                    JsonRpcClient jsonRpcClientInstance = new JsonRpcClient(parityUrl)
                    def json = new JsonSlurper().parseText(it)
                    def result = jsonRpcClientInstance.eth_sendRawTransaction(json.params[0])
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
        println "Transactions submitted in ${duration} ms with ${errors.size()} errors \n\n"

        dumpResults(minimumBlocks)

        then:
        errors.size() == 0
        println "End of batch \n\n"

        where:
        from | to    | clientThreads | submitTime | minimumBlocks
        0    | 10000 | 150           | 10         | 1000
    }

    List dumpResults(Integer minimumBlocks) {
        JsonRpcClient jsonRpcClientInstance = new JsonRpcClient(parityUrl)

        def latestBlockNumber = 0

        while (latestBlockNumber < minimumBlocks) {
            latestBlockNumber = new BigInteger(jsonRpcClientInstance.eth_getBlockByNumber('latest', false).result.number.substring(2), 16)
            sleep(3000l)
        }

        File jsonReport = new File('build/testReport.json')
        if (jsonReport.exists()) {
            assert jsonReport.delete()
            assert jsonReport.createNewFile()
        }

        boolean append = true
        FileWriter fileWriter = new FileWriter(jsonReport, append)
        BufferedWriter buffWriter = new BufferedWriter(fileWriter)
        ArrayList jsonArray = new ArrayList()

        def range = 0..latestBlockNumber
        ParallelEnhancer.enhanceInstance(range)

        range.eachParallel {
            JsonRpcClient jsonRpcClientLoopInstance = new JsonRpcClient(parityUrl)
            String theBlock = "0x${Long.toHexString(it.value)}"
            def transactionCount = new BigInteger(jsonRpcClientLoopInstance.eth_getBlockTransactionCountByNumber(theBlock).result.substring(2), 16)
            def blockJson = jsonRpcClientLoopInstance.eth_getBlockByNumber(theBlock, false).result

            println "block_number: ${it.value}, transactionCount: $transactionCount"

            jsonArray.add([
                    block_number    : "$it.value",
                    transactionCount: "$transactionCount",
                    blockJson       : "$blockJson"
            ])
        }

        buffWriter.writeLine JsonOutput.toJson(jsonArray)
        buffWriter.flush()
        buffWriter.close()
    }

}



