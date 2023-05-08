package eu.esa.sar.io.pyrate;


import eu.esa.sar.io.pyrate.pyrateheader.PyRateHeaderWriter;
import org.apache.commons.io.FileUtils;
import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.dataio.ProductWriterPlugIn;

import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.dataio.geotiff.GeoTiffProductWriter;
import org.esa.snap.engine_utilities.datamodel.Unit;


import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Locale;

public class PyRateProductWriter extends GeoTiffProductWriter {

    private File processingLocation;


    /**
     * Construct a new instance of a product writer for the given GeoTIFF product writer plug-in.
     *
     * @param writerPlugIn the given GeoTIFF product writer plug-in, must not be <code>null</code>
     */
    public PyRateProductWriter(ProductWriterPlugIn writerPlugIn) {
        super(writerPlugIn);
    }

    @Override
    protected void writeProductNodesImpl() throws IOException {

        if (getOutput() instanceof String) {
            processingLocation = new File((String) getOutput()).getParentFile();
        } else {
            processingLocation = ((File) getOutput()).getParentFile();
        }
        new File(processingLocation, "geoTiffs").mkdirs();
        new File(processingLocation, "headers").mkdirs();

        // Set up PyRATE output directory in our processing directory.
        new File(processingLocation, "pyrateOutputs").mkdirs();


        PyRateConfigurationFileBuilder configBuilder = new PyRateConfigurationFileBuilder();

        File geoTiffs = new File(processingLocation, "geoTiffs");
        geoTiffs.mkdirs();

        configBuilder.coherenceFileList = new File(processingLocation, "coherenceFiles.txt").getName();

        configBuilder.interferogramFileList = new File(processingLocation, "ifgFiles.txt").getName();

        configBuilder.outputDirectory = new File(processingLocation, "pyrateOutputs").getName();

        File headerFileFolder = new File(processingLocation, "headers");

        String mainFileContents = configBuilder.createMainConfigFileContents();

        FileUtils.write(new File(processingLocation, "input_parameters.conf"), mainFileContents);

        PyRateHeaderWriter gammaHeaderWriter = new PyRateHeaderWriter(getSourceProduct());

        headerFileFolder.mkdirs();
        try {
            gammaHeaderWriter.writeHeaderFiles(headerFileFolder, new File(processingLocation, configBuilder.headerFileList));
        } catch (ParseException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        ArrayList<String> bannedDates = gammaHeaderWriter.getBannedDates();

        // Write coherence and phase bands out to individual GeoTIFFS
        String interferogramFileList = null;
        try {
            interferogramFileList = writeBands(getSourceProduct(), "GeoTIFF", Unit.PHASE, bannedDates);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String coherenceFiles = null;
        try {
            coherenceFiles = writeBands(getSourceProduct(), "GeoTIFF", Unit.COHERENCE, bannedDates);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Write out elevation band in GeoTIFF format.
        try {
            writeElevationBand(getSourceProduct(), configBuilder.demFile, "GeoTIFF");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        // Only write in GAMMA format to write out header. .rslc image data gets deleted.
        try {
            writeElevationBand(getSourceProduct(), configBuilder.demFile, "Gamma");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        try {
            FileUtils.write(new File(processingLocation, configBuilder.coherenceFileList), coherenceFiles);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            FileUtils.write(new File(processingLocation, configBuilder.interferogramFileList), interferogramFileList);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
    private String writeBands(Product product, String format, String unit, ArrayList<String> bannedDates) throws IOException {
        String fileNames = "";
        int x = 0;
        for(Band b: product.getBands()){
            if(b.getUnit().contains(unit)){
                Product productSingleBand = new Product(product.getName(), product.getProductType(), product.getSceneRasterWidth(), product.getSceneRasterHeight());
                productSingleBand.setSceneGeoCoding(product.getSceneGeoCoding());
                b.readRasterDataFully();
                ProductUtils.copyBand(b.getName(), product, productSingleBand, true);
                String [] name = b.getName().split("_");
                int y = 0;
                String firstDate = "";
                String secondDate = "";
                for (String aname : name){
                    if (aname.length() == 9){
                        firstDate = aname;
                        secondDate = name[y + 1];
                        break;
                    }
                    y+= 1;
                }
                int firstDateNum = Integer.parseInt(bandNameDateToPyRateDate(firstDate, false));
                int secondDateNum = Integer.parseInt(bandNameDateToPyRateDate(secondDate, false));
                // Secondary date cannot be before primary date. Don't write out band if so. Also do not write any ifg pairs
                // that have been deemed as containing bad metadata.
                if(secondDateNum < firstDateNum ||
                        bannedDates.contains(bandNameDateToPyRateDate(firstDate, false)) ||
                        bannedDates.contains(bandNameDateToPyRateDate(secondDate, false))){
                    continue;
                }
                String pyRateDate = bandNameDateToPyRateDate(firstDate, false) + "-" + bandNameDateToPyRateDate(secondDate, false);
                String pyRateName = pyRateDate + "_" + unit;
                String fileName = new File(new File(processingLocation, "geoTiffs"), pyRateName).getAbsolutePath();
                productSingleBand.setName(pyRateName);
                productSingleBand.getBands()[0].setName(pyRateName);

                ProductIO.writeProduct(productSingleBand, fileName, format);

                if(format.equals("GeoTIFF")){
                    fileName += ".tif";
                }else{
                    PyRateHeaderWriter.adjustGammaHeader(productSingleBand, new File(new File(processingLocation, "geoTiffs"), productSingleBand.getName() + ".par"));
                    new File(processingLocation, productSingleBand.getName() + ".rslc").delete();
                }
                fileNames += "\n" + new File(fileName).getParentFile().getName() + "/" + new File(fileName).getName();
                x++;
            }
        }
        // Cut off trailing newline character.
        return fileNames.substring(1);
    }

    private void writeElevationBand(Product product, String name, String format) throws IOException {
        Product productSingleBand = new Product(product.getName(), product.getProductType(), product.getSceneRasterWidth(), product.getSceneRasterHeight());
        productSingleBand.setSceneGeoCoding(product.getSceneGeoCoding());
        Band elevationBand = product.getBand("elevation");
        ProductUtils.copyBand("elevation", product, productSingleBand, true);
        String fileName = new File(processingLocation, name).getAbsolutePath();
        ProductIO.writeProduct(productSingleBand, fileName, format);
        if(format.equals("Gamma")){
            // Default GAMMA format writing doesn't add everything we need. Add the additional needed files.
            PyRateHeaderWriter.adjustGammaHeader(productSingleBand, new File(processingLocation, "DEM.par"));
            new File(processingLocation, "elevation.rslc").delete();
        }
    }

    // Converts format of 14May2020 to 20200414. or 2020 04 14 depending on if forPARFile is set to true or not.
    public static String bandNameDateToPyRateDate(String bandNameDate, boolean forPARFile){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM").withLocale(Locale.ENGLISH);
        TemporalAccessor accessor = formatter.parse(toSentenceCase(bandNameDate.substring(2, 5)));
        int monthNumber = accessor.get(ChronoField.MONTH_OF_YEAR);
        String month = String.valueOf(monthNumber);
        if(monthNumber < 10){
            month = "0" + month;
        }
        // Formatted as YYYYMMDD if for band/product names, YYYY MM DD if for GAMMA PAR file contents.
        String delimiter = " ".substring(forPARFile ? 0: 1);
        return bandNameDate.substring(5) + delimiter +
                month + delimiter + bandNameDate.substring(0, 2);
    }
    public static String toSentenceCase(String word){
        String firstCharacter = word.substring(0, 1);
        String rest = word.substring(1);
        return firstCharacter.toUpperCase() + rest.toLowerCase();
    }


}