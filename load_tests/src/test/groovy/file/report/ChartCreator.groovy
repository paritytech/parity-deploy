package file.report

import com.thecoderscorner.groovychart.chart.AreaChart
import com.thecoderscorner.groovychart.chart.BaseChart
import com.thecoderscorner.groovychart.chart.ChartBuilder
import com.thecoderscorner.groovychart.chart.LineChart
import com.thecoderscorner.groovychart.chart.ScatterPlot
import com.thecoderscorner.groovychart.chart.TimeseriesChart
import groovy.swing.SwingBuilder
import org.jfree.chart.ChartPanel
import org.jfree.chart.ChartUtilities
import org.jfree.chart.JFreeChart
import org.jfree.chart.plot.PlotOrientation

import javax.swing.WindowConstants
import java.awt.BorderLayout
import java.awt.Color

class ChartCreator {

    static displayChartWindow(BaseChart baseChart) {
        SwingBuilder swing = new SwingBuilder();

        def frame = swing.frame(
                title: 'This is a Frame',
                location: [100, 100],
                size: [800, 400],
                layout: new BorderLayout(),
                defaultCloseOperation: WindowConstants.EXIT_ON_CLOSE) {
        };
        frame.add(new ChartPanel(baseChart.chart));
        frame.setVisible(true);
    }

    static displayChartWindow(JFreeChart baseChart) {
        SwingBuilder swing = new SwingBuilder();

        def frame = swing.frame(
                title: 'This is a Frame',
                location: [100, 100],
                size: [800, 400],
                layout: new BorderLayout(),
                defaultCloseOperation: WindowConstants.EXIT_ON_CLOSE) {
        };
        frame.add(new ChartPanel(baseChart));
        frame.setVisible(true);
    }

    static writeChartFile(JFreeChart baseChart, String chartFileName) {
        File file = new File(chartFileName + ".png");
        file.delete();
        ChartUtilities.saveChartAsPNG(file, baseChart, 2300, 1200);
    }

    static BaseChart buildAreaChart(ArrayList<BlockTime> dataSetList, String title, String milliseconds) {
        ChartBuilder builder = new ChartBuilder();

        AreaChart areachart = builder.areachart(title: "$title",
                categoryAxisLabel: milliseconds,
                valueAxisLabel: 'Txs / Block',
                orientation: PlotOrientation.VERTICAL,
                legend: true,
                tooltips: false,
                urls: false
        ) {

            defaultCategoryDataset() {
                dataSetList.eachWithIndex { blockTime, blockNumber ->
                    addValue(blockTime.txs, row: blockTime.rowTitle, column: blockTime.millis as String)
                }
            }

            categoryplot {
                foregroundAlpha 0.7

                renderer {
                    seriesPaint(0, paint: new Color(255, 0, 0));
                    seriesPaint(1, paint: new Color(150, 255, 0));
                    seriesPaint(2, paint: new Color(0, 150, 255));
                    seriesPaint(3, paint: new Color(0, 0, 255));
                    seriesPaint(4, paint: new Color(150, 0, 255));
                    seriesPaint(5, paint: new Color(150, 150, 255));
                }
            }
        }
        areachart
    }

    static BaseChart buildScatterChart(ArrayList<BlockTime> dataSetList, String title, String milliseconds) {
        ChartBuilder builder = new ChartBuilder();

        ScatterPlot areachart = builder.scatterplot(title: "$title",
                categoryAxisLabel: milliseconds,
                valueAxisLabel: 'Txs / Block',
                orientation: PlotOrientation.VERTICAL,
                legend: true,
                tooltips: false,
                urls: false
        ) {

            xyDataset() {
                dataSetList.eachWithIndex { blockTime, blockNumber ->
                    addValue(blockTime.txs, row: blockTime.rowTitle, column: blockTime.millis as String)
                }
            }

            xyplot {
                foregroundAlpha 0.7

                renderer {
                    seriesPaint(0, paint: new Color(255, 0, 0));
                    seriesPaint(1, paint: new Color(150, 255, 0));
                    seriesPaint(2, paint: new Color(0, 150, 255));
                    seriesPaint(3, paint: new Color(0, 0, 255));
                    seriesPaint(4, paint: new Color(150, 0, 255));
                    seriesPaint(5, paint: new Color(150, 150, 255));
                }
            }
        }
        areachart
    }

    static BaseChart buildTimeChart(ArrayList<BlockTime> dataSetList, String title, String milliseconds) {
        ChartBuilder builder = new ChartBuilder();

        TimeseriesChart areachart = builder.timeserieschart(title: "$title",
                categoryAxisLabel: milliseconds,
                valueAxisLabel: 'Txs / Block',
                orientation: PlotOrientation.VERTICAL,
                legend: true,
                tooltips: false,
                urls: false
        ) {

            defaultCategoryDataset() {
                dataSetList.eachWithIndex { blockTime, blockNumber ->
                    addValue(blockTime.txs, row: blockTime.rowTitle, column: blockTime.millis as String)
                }
            }

            categoryplot {
                foregroundAlpha 0.7

                renderer {
                    seriesPaint(0, paint: new Color(255, 0, 0));
                    seriesPaint(1, paint: new Color(150, 255, 0));
                    seriesPaint(2, paint: new Color(0, 150, 255));
                    seriesPaint(3, paint: new Color(0, 0, 255));
                    seriesPaint(4, paint: new Color(150, 0, 255));
                    seriesPaint(5, paint: new Color(150, 150, 255));
                }
            }
        }
        areachart
    }



}
