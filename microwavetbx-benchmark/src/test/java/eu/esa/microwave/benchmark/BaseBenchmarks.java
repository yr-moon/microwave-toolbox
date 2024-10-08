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

import com.bc.ceres.core.ProgressMonitor;
import eu.esa.sar.commons.test.TestData;
import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.common.SubsetOp;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;

public class BaseBenchmarks {

    protected final static File grdFile = new File(TestData.inputSAR +"S1/AWS/S1A_IW_GRDH_1SDV_20180719T002854_20180719T002919_022856_027A78_042A/manifest.safe");
    protected final static File grdZipFile = new File(TestData.inputSAR +"S1/GRD/S1A_IW_GRDH_1SDV_20240508T062559_20240508T062624_053776_0688DB_1A13.SAFE.zip");
    protected final static File slcFile = new File(TestData.inputSAR +"S1/SLC/S1A_IW_SLC__1SDV_20240504T180410_20240504T180437_053725_0686E4_637E.SAFE.zip");
    protected final static File qpFile = new File(TestData.inputSAR +"RS2/RS2_OK2084_PK24911_DK25857_FQ14_20080802_225909_HH_VV_HV_VH_SLC/product.xml");

    protected final static Rectangle rect = new Rectangle(0, 0, 5000, 5000);

    protected String testName;
    protected final String DIMAP = "BEAM-DIMAP";
    protected enum WriteMode {PRODUCT_IO, GPF, GRAPH}

    BaseBenchmarks() {
        diagnostics();
    }

    protected void setName(final String name) {
        this.testName = name;
    }

    protected Product read(final File file) throws IOException {
        return ProductIO.readProduct(file);
    }

    protected Product subset(final File file, final Rectangle rect) throws IOException {
        final Product srcProduct = ProductIO.readProduct(file);
        SubsetOp op = new SubsetOp();
        op.setSourceProduct(srcProduct);
        op.setCopyMetadata(true);
        op.setRegion(rect);
        return op.getTargetProduct();
    }

    protected void write(final Product trgProduct, final File outputFolder, final String format) throws IOException {
        ProductIO.writeProduct(trgProduct, new File(outputFolder, trgProduct.getName()), format, false);
    }

    protected void writeGPF(final Product trgProduct, final File outputFolder, final String format) {
        GPF.writeProduct(trgProduct, new File(outputFolder, trgProduct.getName()), format, false, ProgressMonitor.NULL);
    }

    protected void diagnostics() {
        System.out.println("Maximum Memory (MB): " + Runtime.getRuntime().maxMemory() / 1024 / 1024);
        System.out.println("Total Memory (MB): " + Runtime.getRuntime().totalMemory() / 1024 / 1024);
        System.out.println("Free Memory (MB): " + Runtime.getRuntime().freeMemory() / 1024 / 1024);
        System.out.println("Used Memory (MB): " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024);

        System.out.println("JVM Arguments: " + ManagementFactory.getRuntimeMXBean().getInputArguments());
        System.out.println("OS Name: " + System.getProperty("os.name"));
        System.out.println("OS Version: " + System.getProperty("os.version"));
    }
}
