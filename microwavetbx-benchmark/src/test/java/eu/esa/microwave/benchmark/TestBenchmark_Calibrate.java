/*
 * Copyright (C) 2021 SkyWatch Space Applications Inc. https://www.skywatch.com
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package eu.esa.microwave.benchmark;

import com.bc.ceres.binding.dom.DefaultDomElement;
import com.bc.ceres.binding.dom.DomElement;
import com.bc.ceres.core.ProgressMonitor;
import eu.esa.sar.calibration.gpf.CalibrationOp;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.graph.Graph;
import org.esa.snap.core.gpf.graph.GraphProcessor;
import org.esa.snap.core.gpf.graph.Node;
import org.esa.snap.core.gpf.graph.NodeSource;
import org.junit.Test;

import java.io.File;

public class TestBenchmark_Calibrate extends BaseBenchmarks {

    @Test
    public void testGRD_calibrate_ProductIO() throws Exception {
        setName(new Throwable().getStackTrace()[0].getMethodName());
        calibrate(grdFile, WriteMode.PRODUCT_IO);
    }

    @Test
    public void testGRD_calibrate_GPF() throws Exception {
        setName(new Throwable().getStackTrace()[0].getMethodName());
        calibrate(grdFile, WriteMode.GPF);
    }

    @Test
    public void testGRD_calibrate_Graph() throws Exception {
        setName(new Throwable().getStackTrace()[0].getMethodName());
        calibrate(grdFile, WriteMode.GRAPH);
    }

    private void calibrate(final File srcFile, final WriteMode mode) throws Exception {
        Benchmark b = new Benchmark(testName) {
            @Override
            protected void execute() throws Exception {
                switch (mode) {
                    case PRODUCT_IO:
                    case GPF:
                        process(srcFile, outputFolder, mode);
                        break;
                    case GRAPH:
                        processGraph(srcFile, outputFolder);
                        break;
                }
            }
        };
        b.run();
    }

    private void process(final File file, final File outputFolder, final WriteMode mode) throws Exception {
        final Product srcProduct = read(file);

        CalibrationOp op = new CalibrationOp();
        op.setSourceProduct(srcProduct);
        Product trgProduct = op.getTargetProduct();

        if(mode == WriteMode.GPF) {
            writeGPF(trgProduct, outputFolder, DIMAP);
        } else {
            write(trgProduct, outputFolder, DIMAP);
        }

        trgProduct.dispose();
        srcProduct.dispose();
    }

    private void processGraph(final File file, final File outputFolder) throws Exception {

        final Graph graph = new Graph("graph");

        final Node readNode = new Node("read", "read");
        final DomElement readParameters = new DefaultDomElement("parameters");
        readParameters.createChild("file").setValue(file.getAbsolutePath());
        readNode.setConfiguration(readParameters);
        graph.addNode(readNode);

        final Node decompNode = new Node("Calibration", "Calibration");
        decompNode.addSource(new NodeSource("source", "read"));
        graph.addNode(decompNode);

        final Node writeNode = new Node("write", "write");
        final DomElement writeParameters = new DefaultDomElement("parameters");
        final File outFile = new File(outputFolder, file.getName());
        writeParameters.createChild("file").setValue(outFile.getAbsolutePath());
        writeNode.setConfiguration(writeParameters);
        writeNode.addSource(new NodeSource("source", "Calibration"));
        graph.addNode(writeNode);

        final GraphProcessor processor = new GraphProcessor();
        processor.executeGraph(graph, ProgressMonitor.NULL);
    }
}
