import org.jfree.chart.JFreeChart
import org.jfree.data.xy.XYSeries
import org.jfree.data.xy.XYSeriesCollection
import parity.JsonRpcClient
import groovyx.gpars.ParallelEnhancer
import org.cliffc.high_scale_lib.NonBlockingHashMap
import file.report.BlockTime
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static file.report.ChartCreator.*
import static org.jfree.chart.ChartFactory.createScatterPlot
import static org.jfree.chart.plot.PlotOrientation.VERTICAL
import static parity.ParityClient.*
import static parity.DockerController.*
import static file.config.ConfigBuilder.*

class EthSendRawTransactionBasic10000ChartBlocksSpockTest extends Specification {

    public static final String parityUrl = "http://localhost:8545/"

    @Shared String fileContents = new File('src/test/resources/eth_sendRawTransaction_basic_10000.rpc').getText('UTF-8')
    @Shared JsonRpcClient jsonRpcClientInstance
    @Shared String engineSigner
    @Shared int transactionsProcessed = 0
    @Shared String password
    @Shared String workingDirPath = "./.."
    @Shared File workingDir = new File("${workingDirPath}")
    @Shared chainSpecFile = "${workingDirPath}/deployment/chain/spec.json" as File
    @Shared ArrayList<BlockTime> chartDataSet = new ArrayList<>()
    @Shared String chartTitle
    @Shared Number latestBlockNumber
    @Shared XYSeriesCollection xyDataset = new XYSeriesCollection()

    void setupSpec() {
        chartTitle = 'Txs / Block against Gas Limit and Step Duration'
        "docker pull parity/parity:nightly".execute()
        def sout = new StringBuilder(), serr = new StringBuilder()
        def proc = "./parity-deploy.sh --config aura --nodes 1 --name aura_test".execute(null, workingDir)
        proc.consumeProcessOutput(sout, serr)
        proc.waitForOrKill(1000)
        println "out> $sout err> $serr"

        jsonRpcClientInstance = new JsonRpcClient(parityUrl)
        def composeFile = "${workingDirPath}/docker-compose.yml" as File
        writeComposeFile(composeFile, 'host1')
        engineSigner = removeEngineSigner(workingDirPath)
        "sync".execute()
        password = getPasswordFromFile(workingDirPath)
        runParity(workingDir)
        waitForParityAlive(jsonRpcClientInstance)
    }

    @Unroll("Submission of #batchSize transaction with gasLimit of #gasLimit and gasLimitBoundDivisor #gasLimitBoundDivisor should not throw error")
    def "test eth_subscribe"() {

        setup:
        writeChainSpec(chainSpecFile, convertToHexFormat(gasLimit), convertToHexFormat(gasLimitBoundDivisor), stepDuration as String)
        "sync".execute()
        "docker restart host1".execute()
        waitForParityAlive(jsonRpcClientInstance)

        when:
        List lines = fileContents.readLines().subList(0, batchSize)
        NonBlockingHashMap errors = new NonBlockingHashMap()
        NonBlockingHashMap results = new NonBlockingHashMap()
        ParallelEnhancer.enhanceInstance(lines)
        long start
        long now

        def duration = {
            start = System.currentTimeMillis()
            submitTxsParallel(errors, results, clientThreads, lines, parityUrl)
            now = System.currentTimeMillis()
            now - start
        }

        println "$batchSize transactions submitted in ${duration} ms with ${errors.size()} errors \n\n"
        transactionsProcessed = 0
        addEngineSigner(jsonRpcClientInstance, engineSigner, password)
        waitForMinimumBlocksProcessed(batchSize, maxBlocksToCheck, stepDuration, transactionsProcessed, jsonRpcClientInstance, engineSigner, password)

        plotResults("GasLimit $gasLimit StepDuration: $stepDuration" as String, stepDuration)

        "sync".execute()

        then:
        errors.size() == 0
        println "End of batch \n\n"
        "sudo rm -rf ./data".execute null, workingDir

        where:
        batchSize | clientThreads | maxBlocksToCheck | gasLimit | gasLimitBoundDivisor | stepDuration | testRun
        10000     | 15           | 5                | 150000000 | 100000000            | 1            | 'test1'
        10000     | 15           | 5                | 150000000 | 100000000            | 2            | 'test2'
        10000     | 15           | 5                | 150000000 | 100000000            | 3            | 'test3'
        10000     | 15           | 5                | 150000000 | 100000000            | 4            | 'test4'
        10000     | 15           | 5                | 150000000 | 100000000            | 5            | 'test5'
    }

    def plotResults(String rowTitle, stepDuration) {

        def firstBlockTime = getBlockTimeMillisInDayByNumber(0, jsonRpcClientInstance)
        latestBlockNumber = getLatestBlockNumber(jsonRpcClientInstance)
        def lastBlockTime = getBlockTimeMillisInDayByNumber(this.latestBlockNumber, jsonRpcClientInstance)

        ArrayList jsonArray = new ArrayList()
        def series = new XYSeries(rowTitle);
        def range = 0..latestBlockNumber
        ParallelEnhancer.enhanceInstance(range)

        range.each {
            JsonRpcClient jsonRpcClientLoopInstance = new JsonRpcClient(parityUrl)
            String theBlock = convertToHexFormat(it)
            long transactionCount = getBlockTransactionCountByNumber(jsonRpcClientLoopInstance, theBlock)
            def blockJson = getBlockByNumber(jsonRpcClientLoopInstance, theBlock)
            int blockTimeInMillis = getBlockTimeMillisInDayByNumber(it, jsonRpcClientInstance)
            int blockTimeMillisSinceEngineSignOn = blockTimeInMillis - firstBlockTime

            println "block_number: ${it.value}, transactionCount: $transactionCount blockTimeMillisSinceEngineSignOn: $blockTimeMillisSinceEngineSignOn"

            jsonArray.add([
                    block_number    : "$it.value",
                    transactionCount: "$transactionCount",
                    blockJson       : "$blockJson"
            ])

            def durationBetweenBlockTimesByNumber = stepDuration
            if (it>1) {
                durationBetweenBlockTimesByNumber = getDurationBetweenBlockTimesByNumber(Math.max(0, it - 1), it, jsonRpcClientInstance)
            }
            series.add(durationBetweenBlockTimesByNumber.standardSeconds,transactionCount as Number)

        }
        writeJson(new File('build/testReport.json'), jsonArray)
        xyDataset.addSeries(series)
    }

    def cleanupSpec() {
        JFreeChart chart = createScatterPlot(chartTitle, "Block Duration Seconds", "Txs", this.xyDataset,VERTICAL,true,true,true );
        writeChartFile(chart,'Chart')
        println "Tearing down parity containers and cleaning up directories"
        bringDownParity(workingDir)
        sleep(5000l)
        "./clean.sh".execute null, workingDir
        "sudo rm -rf ./deployment".execute null, workingDir
        "sudo rm -rf ./data".execute null, workingDir
        println "cleaned up"
    }
}






